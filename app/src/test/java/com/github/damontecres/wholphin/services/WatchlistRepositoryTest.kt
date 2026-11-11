package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistFilter
import com.github.damontecres.wholphin.data.model.WatchlistFilters
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.data.model.WatchlistPage
import com.github.damontecres.wholphin.data.model.watchlistFocusAfterRemoval
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.detail.gridLeadingSlots
import com.github.damontecres.wholphin.ui.watchlist.WatchlistViewModel
import com.github.damontecres.wholphin.ui.watchlist.watchlistRandomTarget
import com.github.damontecres.wholphin.util.WholphinDispatchers
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WatchlistRepositoryTest {
    private class Store(
        initial: AppPreferences = AppPreferences.getDefaultInstance(),
    ) : DataStore<AppPreferences> {
        override val data = MutableStateFlow(initial)

        override suspend fun updateData(transform: suspend (t: AppPreferences) -> AppPreferences): AppPreferences =
            transform(data.value).also { data.value = it }
    }

    private fun key(
        id: Int,
        type: CatalogMediaType = CatalogMediaType.MOVIE,
    ) = MediaKey.Catalog(type, id)

    private fun entry(id: Int) =
        WatchlistEntry(
            key(id),
            "Title $id",
            Instant.parse("2026-11-10T00:00:00Z").minusSeconds(id.toLong()),
            null,
            null,
            null,
            DiscoverItem(id, SeerrItemType.MOVIE, "Title $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
        )

    private class Backend : WatchlistBackend {
        var members = mutableSetOf<MediaKey.Catalog>()
        val batches = mutableListOf<Set<MediaKey.Catalog>>()
        val reads = mutableListOf<Pair<WatchlistCategory, Int>>()
        val queries = mutableListOf<WatchlistFilters>()
        var additions = 0
        var removals = 0
        var lookup: (suspend (Set<MediaKey.Catalog>) -> Map<MediaKey.Catalog, Boolean>)? = null
        var write: (suspend () -> Unit)? = null
        var pageResult: suspend (WatchlistCategory, Int) -> WatchlistPage = { _, page -> WatchlistPage(page, 1, 0, emptyList()) }
        var filteredPageResult: (suspend (WatchlistCategory, Int, WatchlistFilters) -> WatchlistPage)? = null

        override suspend fun page(
            category: WatchlistCategory,
            page: Int,
            filters: WatchlistFilters,
        ): WatchlistPage {
            reads += category to page
            queries += filters
            return filteredPageResult?.invoke(category, page, filters) ?: pageResult(category, page)
        }

        override suspend fun membership(keys: Set<MediaKey.Catalog>): Map<MediaKey.Catalog, Boolean> {
            batches += keys
            return lookup?.invoke(keys) ?: keys.associateWith { it in members }
        }

        override suspend fun add(
            key: MediaKey.Catalog,
            title: String,
        ) {
            additions++
            write?.invoke()
            members += key
        }

        override suspend fun remove(key: MediaKey.Catalog) {
            removals++
            write?.invoke()
            members -= key
        }
    }

    private fun screenTest(
        preferences: Store = Store(),
        sessions: MutableStateFlow<WatchlistSession?>? = null,
        block: suspend TestScope.(Backend, WatchlistRepository, WatchlistViewModel) -> Unit,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val previousIo = WholphinDispatchers.IO
        Dispatchers.setMain(dispatcher)
        WholphinDispatchers.IO = dispatcher
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val backend = Backend()
            val repository =
                WatchlistRepository(sessions ?: MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            val viewModel = WatchlistViewModel(repository, mockk(relaxed = true), preferences)
            store.put("watchlist", viewModel)
            block(backend, repository, viewModel)
        } finally {
            store.clear()
            Dispatchers.resetMain()
            WholphinDispatchers.IO = previousIo
        }
    }

    @Test fun filterPreferencesRoundTripAndRecoverInvalidSelectionsIndependently() {
        for (owned in WatchlistFilter.entries) {
            for (watched in WatchlistFilter.entries) {
                val filters = WatchlistFilters(owned, watched)
                assertEquals(filters, WatchlistFilters.decode(filters.encode()))
            }
        }
        assertEquals(WatchlistFilters(), WatchlistFilters.decode(null))
        assertEquals(WatchlistFilters(watched = WatchlistFilter.NO), WatchlistFilters.decode("obsolete:NO"))
        assertEquals(WatchlistFilters(owned = WatchlistFilter.YES), WatchlistFilters.decode("YES:obsolete"))
    }

    @Test fun filtersPersistAcrossCategoriesAndScreenRecreationWithAuthoritativeTotals() {
        val preferences = Store()
        screenTest(preferences) { backend, _, viewModel ->
            backend.filteredPageResult = { _, page, filters ->
                WatchlistPage(page, 5, if (filters.owned == WatchlistFilter.ALL) 99 else 87, (1..20).map(::entry))
            }
            runCurrent()
            assertEquals(WatchlistFilters(), viewModel.state.value.filters)
            viewModel.owned(WatchlistFilter.YES)
            viewModel.watched(WatchlistFilter.NO)
            runCurrent()
            val combined = WatchlistFilters(WatchlistFilter.YES, WatchlistFilter.NO)
            assertEquals(combined, viewModel.state.value.filters)
            assertEquals(87, viewModel.state.value.totalResults)
            viewModel.position(1, 6)
            viewModel.refresh(force = true)
            runCurrent()
            assertEquals(1, viewModel.state.value.focusedIndex)
            for (category in WatchlistCategory.entries) {
                viewModel.category(category)
                runCurrent()
                assertEquals(combined, viewModel.state.value.filters)
                assertEquals(87, viewModel.state.value.totalResults)
                assertEquals(category, viewModel.state.value.category)
            }
            assertEquals(combined.encode(), preferences.data.value.watchlistFiltersMap["A"])
            viewModel.owned(WatchlistFilter.ALL)
            runCurrent()
            assertEquals(WatchlistFilter.NO, viewModel.state.value.filters.watched)
        }
        // Exercise persisted protobuf bytes, not a surviving ViewModel or page cache.
        val restored = Store(AppPreferences.parseFrom(preferences.data.value.toByteArray()))
        screenTest(restored) { backend, _, viewModel ->
            runCurrent()
            assertEquals(WatchlistFilters(watched = WatchlistFilter.NO), viewModel.state.value.filters)
            assertEquals(listOf(viewModel.state.value.filters), backend.queries)
        }
    }

    @Test fun filterPreferencesAreIsolatedByPersonalUserAndServerIdentity() {
        val preferences = Store()
        val sessions = MutableStateFlow<WatchlistSession?>(null)
        screenTest(preferences, sessions) { backend, _, viewModel ->
            val identities = listOf("seerrA:7:jellyfinA:user1", "seerrA:8:jellyfinA:user2", "seerrB:7:jellyfinB:user1")
            for ((index, owner) in identities.withIndex()) {
                sessions.value = WatchlistSession(owner, backend) { true }
                runCurrent()
                assertEquals(WatchlistFilters(), viewModel.state.value.filters)
                viewModel.owned(WatchlistFilter.entries[index])
                viewModel.watched(WatchlistFilter.entries[2 - index])
                runCurrent()
            }
            sessions.value = null
            runCurrent()
            assertNull(viewModel.state.value.owner)
            assertEquals(WatchlistFilters(), viewModel.state.value.filters)
            for ((index, owner) in identities.withIndex()) {
                sessions.value = WatchlistSession(owner, backend) { true }
                runCurrent()
                assertEquals(
                    WatchlistFilters(WatchlistFilter.entries[index], WatchlistFilter.entries[2 - index]),
                    viewModel.state.value.filters,
                )
                assertEquals(owner, viewModel.state.value.owner)
            }
            assertEquals(identities.toSet(), preferences.data.value.watchlistFiltersMap.keys)
        }
    }

    @Test fun combinedFilterChangeDuringPrefetchRejectsLateRowsAndResetsFocusOnlyForNewQuery() =
        screenTest { backend, repository, viewModel ->
            val gate = CompletableDeferred<Unit>()
            backend.filteredPageResult = { _, page, filters ->
                if (filters == WatchlistFilters() && page == 2) withContext(NonCancellable) { gate.await() }
                if (filters == WatchlistFilters()) {
                    WatchlistPage(page, 4, 80, (1..20).map(::entry))
                } else {
                    WatchlistPage(page, 4, 67, ((page - 1) * 20 + 101..page * 20 + 100).map(::entry))
                }
            }
            runCurrent()
            viewModel.position(6, 6)
            runCurrent()
            assertEquals(listOf(1, 2), backend.reads.map { it.second })
            viewModel.owned(WatchlistFilter.NO)
            viewModel.watched(WatchlistFilter.NO)
            runCurrent()
            assertEquals(0, viewModel.state.value.focusedIndex)
            assertTrue(
                viewModel.state.value.entries
                    .isEmpty(),
            )
            assertEquals(20, repository.state.value.membership.size)
            gate.complete(Unit)
            runCurrent()
            assertEquals((101..120).map(::entry), viewModel.state.value.entries)
            assertEquals(67, viewModel.state.value.totalResults)
            assertEquals(WatchlistFilters(WatchlistFilter.NO, WatchlistFilter.NO), backend.queries.last())
            val epoch = viewModel.state.value.gridEpoch
            repeat(100) { viewModel.position(6, 6) }
            runCurrent()
            assertEquals(40, viewModel.state.value.entries.size)
            assertEquals(6, viewModel.state.value.focusedIndex)
            assertEquals(epoch, viewModel.state.value.gridEpoch)
            assertEquals(
                1,
                backend.reads.zip(backend.queries).count { (read, filters) ->
                    read.second == 2 && filters != WatchlistFilters()
                },
            )
        }

    @Test fun pageCacheIncludesBothFiltersWithoutChangingMembershipAuthority() =
        runTest {
            val backend = Backend()
            backend.filteredPageResult =
                { _, page, filters -> WatchlistPage(page, 1, 1, listOf(entry(10 + filters.owned.ordinal * 3 + filters.watched.ordinal))) }
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            val queries =
                listOf(WatchlistFilters(), WatchlistFilters(owned = WatchlistFilter.YES), WatchlistFilters(watched = WatchlistFilter.NO))
            for (filters in queries) repository.page(WatchlistCategory.ALL, 1, filters = filters)
            for (filters in queries) repository.page(WatchlistCategory.ALL, 1, filters = filters)
            assertEquals(queries, backend.queries)
            assertEquals(3, repository.state.value.membership.size)
        }

    @Test fun prefetchIsEarlySequentialAndOrderedAcrossFivePagesWithoutMovingFocus() =
        screenTest { backend, _, viewModel ->
            val gate = CompletableDeferred<Unit>()
            backend.pageResult = { _, page ->
                val rows =
                    when (page) {
                        1 -> {
                            (1..20).map(::entry)
                        }

                        2 -> {
                            gate.await()
                            listOf(entry(20).copy(key = key(20, CatalogMediaType.SERIES))) + (21..39).map(::entry)
                        }

                        else -> {
                            ((40 + (page - 3) * 20)..minOf(59 + (page - 3) * 20, 98)).map(::entry)
                        }
                    }
                WatchlistPage(page, 5, 99, rows)
            }
            runCurrent()
            assertEquals(listOf(1), backend.reads.map { it.second })
            val gridEpoch = viewModel.state.value.gridEpoch
            viewModel.position(1, 6)
            runCurrent()
            assertEquals(1, backend.reads.size)
            viewModel.position(6, 6)
            runCurrent()
            assertEquals(listOf(1, 2), backend.reads.map { it.second })
            repeat(100) { viewModel.position(19, 6) }
            runCurrent()
            assertTrue(viewModel.state.value.loading)
            assertEquals(20, viewModel.state.value.entries.size)
            assertEquals(2, backend.reads.size)
            gate.complete(Unit)
            runCurrent()
            assertEquals(40, viewModel.state.value.entries.size)
            assertEquals(19, viewModel.state.value.focusedIndex)
            assertEquals(gridEpoch, viewModel.state.value.gridEpoch)
            for (index in listOf(24, 44, 64)) {
                repeat(100) { viewModel.position(index, 6) }
                runCurrent()
                assertEquals(index, viewModel.state.value.focusedIndex)
                assertEquals(gridEpoch, viewModel.state.value.gridEpoch)
            }
            repeat(100) { viewModel.position(98, 6) }
            runCurrent()
            assertEquals((1..5).toList(), backend.reads.map { it.second })
            assertEquals(99, viewModel.state.value.totalResults)
            assertEquals(
                (1..20).map {
                    key(it)
                } + key(20, CatalogMediaType.SERIES) + (21..98).map { key(it) },
                viewModel.state.value.entries
                    .map { it.key },
            )
        }

    @Test fun categoryChangeDuringDelayedPrefetchCannotRestoreOldRowsOrClearMembership() =
        screenTest { backend, repository, viewModel ->
            val gate = CompletableDeferred<Unit>()
            backend.pageResult = { category, page ->
                if (category == WatchlistCategory.ALL && page == 2) withContext(NonCancellable) { gate.await() }
                val rows = if (category == WatchlistCategory.ALL) (1..20).map(::entry) else listOf(entry(200))
                WatchlistPage(page, if (category == WatchlistCategory.ALL) 2 else 1, if (category == WatchlistCategory.ALL) 40 else 1, rows)
            }
            runCurrent()
            viewModel.position(6, 6)
            runCurrent()
            viewModel.category(WatchlistCategory.MOVIES)
            runCurrent()
            assertEquals(WatchlistCategory.MOVIES, viewModel.state.value.category)
            assertTrue(
                viewModel.state.value.entries
                    .isEmpty(),
            )
            assertEquals(0, viewModel.state.value.focusedIndex)
            assertEquals(20, repository.state.value.membership.size)
            gate.complete(Unit)
            runCurrent()
            assertEquals(
                listOf(key(200)),
                viewModel.state.value.entries
                    .map { it.key },
            )
            assertEquals(1, viewModel.state.value.totalResults)
            assertEquals(WatchlistCategory.MOVIES, viewModel.state.value.category)
            assertNull(viewModel.state.value.error)
            assertEquals(listOf(WatchlistCategory.ALL to 1, WatchlistCategory.ALL to 2, WatchlistCategory.MOVIES to 1), backend.reads)
        }

    @Test fun pageReadsCoalesceAndLaterExplicitMembershipWins() =
        runTest {
            val backend = Backend()
            backend.pageResult = { _, _ -> WatchlistPage(1, 1, 1, listOf(entry(1))) }
            val repository =
                WatchlistRepository(
                    MutableStateFlow(WatchlistSession("A", backend) { true }),
                    backgroundScope,
                    { 100_000 },
                )
            runCurrent()
            val first = async { repository.page(WatchlistCategory.ALL, 1) }
            val second = async { repository.page(WatchlistCategory.ALL, 1) }
            assertEquals(first.await(), second.await())
            assertEquals(1, backend.reads.size)
            assertEquals(1, backend.batches.size)
            assertEquals(WatchlistMembership.NOT_MEMBER, repository.state.value.membership[key(1)])
        }

    @Test fun membershipReadPreservesCancellation() =
        runTest {
            val backend = Backend()
            backend.lookup = { throw CancellationException("cancelled") }
            val repository =
                WatchlistRepository(
                    MutableStateFlow(WatchlistSession("A", backend) { true }),
                    backgroundScope,
                    { 100_000 },
                )
            runCurrent()
            assertTrue(runCatching { repository.membership(setOf(key(1))) }.exceptionOrNull() is CancellationException)
            assertTrue(
                repository.state.value.membership
                    .isEmpty(),
            )
        }

    @Test fun lateMutationCannotPublishIntoTheNextUser() =
        runTest {
            val backend = Backend()
            val sessions = MutableStateFlow<WatchlistSession?>(WatchlistSession("A", backend) { true })
            val repository = WatchlistRepository(sessions, backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            val gate = CompletableDeferred<Unit>()
            backend.write = { withContext(NonCancellable) { gate.await() } }
            val pending = async { runCatching { repository.setMembership(key(1), true, "Title") } }
            runCurrent()
            sessions.value = WatchlistSession("B", Backend()) { true }
            runCurrent()
            gate.complete(Unit)
            assertTrue(pending.await().exceptionOrNull() is CancellationException)
            assertEquals("B", repository.state.value.owner)
            assertTrue(
                repository.state.value.membership
                    .isEmpty(),
            )
            assertTrue(
                repository.state.value.busy
                    .isEmpty(),
            )
        }

    @Test fun failedRefreshBecomesUnknownAndCanRetryImmediately() =
        runTest {
            val backend = Backend()
            backend.members += key(1)
            val repository =
                WatchlistRepository(
                    MutableStateFlow(WatchlistSession("A", backend) { true }),
                    backgroundScope,
                    { 100_000 },
                )
            runCurrent()
            repository.membership(setOf(key(1)))
            backend.lookup = { throw IOException("offline") }
            assertTrue(runCatching { repository.membership(setOf(key(1)), refresh = true) }.isFailure)
            assertEquals(WatchlistMembership.UNKNOWN, repository.state.value.membership[key(1)])
            assertFalse(repository.setMembership(key(1), false, "Title"))
            backend.lookup = null
            backend.members.clear()
            repository.membership(setOf(key(1)))
            assertEquals(WatchlistMembership.NOT_MEMBER, repository.state.value.membership[key(1)])
            assertEquals(0, backend.removals)
        }

    @Test fun typedIdentityAndFocusBounds() {
        assertNotEquals(key(7), key(7, CatalogMediaType.SERIES))
        assertEquals(4, watchlistFocusAfterRemoval(5, 5))
        assertEquals(0, watchlistFocusAfterRemoval(0, 0))
        assertEquals(2, watchlistFocusAfterRemoval(2, 8))
        assertEquals(listOf("all", "movies", "series", "animation"), WatchlistCategory.entries.map { it.query })
    }

    @Test fun boundedBulkAndOverlappingCallersCoalesce() =
        runTest {
            val backend = Backend()
            val owner = WatchlistSession("A", backend) { true }
            val repository = WatchlistRepository(MutableStateFlow(owner), backgroundScope, { 100_000 })
            runCurrent()
            val keys = (1..205).mapTo(linkedSetOf()) { key(it) }
            val first = async { repository.membership(keys) }
            val second = async { repository.membership(keys) }
            first.await()
            second.await()
            assertEquals(listOf(100, 100, 5), backend.batches.map { it.size })
            assertEquals(WatchlistMembership.NOT_MEMBER, repository.state.value.membership[key(1)])
        }

    @Test fun failedOrPartialLookupNeverPublishesAbsence() =
        runTest {
            val backend = Backend()
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            backend.lookup = { throw IOException("offline") }
            assertTrue(runCatching { repository.membership(setOf(key(1))) }.isFailure)
            assertNull(repository.state.value.membership[key(1)])
            backend.lookup = { emptyMap() }
            assertTrue(runCatching { repository.membership(setOf(key(1))) }.isFailure)
            assertNull(repository.state.value.membership[key(1)])
        }

    @Test fun previousOwnerReadCannotPopulateReplacementSession() =
        runTest {
            val old = Backend()
            val gate = CompletableDeferred<Unit>()
            old.lookup = { keys ->
                withContext(NonCancellable) {
                    gate.await()
                    keys.associateWith { true }
                }
            }
            val sessions = MutableStateFlow<WatchlistSession?>(WatchlistSession("A", old) { true })
            val repository = WatchlistRepository(sessions, backgroundScope, { 100_000 })
            runCurrent()
            val pending = launch { runCatching { repository.membership(setOf(key(1))) } }
            runCurrent()
            sessions.value = WatchlistSession("B-on-other-server", Backend()) { true }
            runCurrent()
            assertTrue(
                repository.state.value.membership
                    .isEmpty(),
            )
            gate.complete(Unit)
            pending.join()
            assertEquals("B-on-other-server", repository.state.value.owner)
            assertTrue(
                repository.state.value.membership
                    .isEmpty(),
            )
        }

    @Test fun logoutClearsPagesMembershipAndMutationEligibility() =
        runTest {
            val backend = Backend()
            val sessions = MutableStateFlow<WatchlistSession?>(WatchlistSession("A", backend) { true })
            val repository = WatchlistRepository(sessions, backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            sessions.value = null
            runCurrent()
            assertNull(repository.state.value.owner)
            assertTrue(
                repository.state.value.membership
                    .isEmpty(),
            )
            assertTrue(runCatching { repository.setMembership(key(1), true, "Title") }.isFailure)
            assertEquals(0, backend.additions)
        }

    @Test fun successFencesLateReadAndInvalidatesPages() =
        runTest {
            val backend = Backend()
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            val gate = CompletableDeferred<Unit>()
            backend.lookup = { keys ->
                gate.await()
                keys.associateWith { false }
            }
            val pending = async { repository.membership(setOf(key(1)), refresh = true) }
            runCurrent()
            assertTrue(repository.setMembership(key(1), true, "Title"))
            gate.complete(Unit)
            pending.await()
            assertEquals(WatchlistMembership.MEMBER, repository.state.value.membership[key(1)])
            assertTrue(repository.setMembership(key(1), false, "Title"))
            assertEquals(WatchlistMembership.NOT_MEMBER, repository.state.value.membership[key(1)])
            assertEquals(1, backend.additions)
            assertEquals(1, backend.removals)
        }

    @Test fun duplicateMutationHasOneSubmission() =
        runTest {
            val backend = Backend()
            val gate = CompletableDeferred<Unit>()
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            backend.write = { gate.await() }
            val first = async { repository.setMembership(key(1), true, "Title") }
            runCurrent()
            assertFalse(repository.setMembership(key(1), true, "Title"))
            gate.complete(Unit)
            assertTrue(first.await())
            assertEquals(1, backend.additions)
        }

    @Test fun conflictAndTimeoutReconcileWithoutResubmitting() =
        runTest {
            for (member in listOf(true, false)) {
                val backend = Backend()
                val repository =
                    WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
                runCurrent()
                repository.membership(setOf(key(1)))
                backend.write = {
                    if (member) backend.members += key(1)
                    throw IOException(if (member) "409" else "timeout")
                }
                assertEquals(member, repository.setMembership(key(1), true, "Title"))
                assertEquals(
                    if (member) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER,
                    repository.state.value.membership[key(1)],
                )
                assertEquals(1, backend.additions)
            }
        }

    @Test fun uncertainOutcomeAndCancellationStayUnknown() =
        runTest {
            val backend = Backend()
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            backend.write = { throw IOException("timeout") }
            backend.lookup = { throw IOException("offline") }
            assertTrue(runCatching { repository.setMembership(key(1), true, "Title") }.isFailure)
            assertEquals(WatchlistMembership.UNKNOWN, repository.state.value.membership[key(1)])
            backend.lookup = null
            repository.membership(setOf(key(1)), refresh = true)
            backend.write = { throw CancellationException("cancelled") }
            assertTrue(runCatching { repository.setMembership(key(1), true, "Title") }.exceptionOrNull() is CancellationException)
            assertEquals(WatchlistMembership.UNKNOWN, repository.state.value.membership[key(1)])
            assertTrue(
                repository.state.value.busy
                    .isEmpty(),
            )
        }

    @Test fun allCategoriesAndPaginationPreserveRowsDatesAndMissingMetadata() =
        runTest {
            val backend = Backend()
            val rows = (1..45).map(::entry)
            backend.members.addAll(rows.map { it.key })
            backend.pageResult = { _, page -> WatchlistPage(page, 3, 45, rows.drop((page - 1) * 20).take(20)) }
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            for (category in WatchlistCategory.entries) {
                val pages = (1..3).flatMap { repository.page(category, it).entries }
                assertEquals(45, pages.size)
                assertEquals(rows.map { it.addedAt }, pages.map { it.addedAt })
                assertNull(pages.first().posterPath)
                assertNull(pages.first().genreIds)
            }
            assertEquals(12, backend.reads.size)
            assertTrue(
                repository.state.value.membership.values
                    .all { it == WatchlistMembership.MEMBER },
            )
        }

    @Test fun emptyPageDoesNotClearKnownMembershipAndUnsupportedFails() =
        runTest {
            val backend = Backend()
            backend.members += key(1)
            val repository = WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
            runCurrent()
            repository.membership(setOf(key(1)))
            assertTrue(repository.page(WatchlistCategory.ANIMATION, 1).entries.isEmpty())
            assertEquals(WatchlistMembership.MEMBER, repository.state.value.membership[key(1)])
            backend.pageResult = { _, _ -> throw WatchlistUnavailable("Unsupported API") }
            assertTrue(runCatching { repository.page(WatchlistCategory.ALL, 1) }.exceptionOrNull() is WatchlistUnavailable)
        }

    @Test fun screenLoadsPastTwentyAndBoundsRepeatedFocusCallbacks() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            val store = androidx.lifecycle.ViewModelStore()
            try {
                val backend = Backend()
                backend.pageResult = { _, page ->
                    val ids =
                        when (page) {
                            1 -> 1..20
                            2 -> 21..40
                            else -> 41..45
                        }
                    WatchlistPage(page, 3, 45, ids.map(::entry))
                }
                backend.members.addAll((1..45).map { key(it) })
                val repository =
                    WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true), Store())
                store.put("watchlist", viewModel)
                runCurrent()
                assertEquals(20, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(19, 6) }
                runCurrent()
                assertEquals(40, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(38, 6) }
                runCurrent()
                assertEquals(45, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(44, 6) }
                runCurrent()
                assertEquals(listOf(1, 2, 3), backend.reads.map { it.second })
                assertEquals(
                    45,
                    viewModel.state.value.entries
                        .map { it.key }
                        .toSet()
                        .size,
                )
                assertEquals(
                    (1..45).toList(),
                    viewModel.state.value.entries
                        .map { it.key.tmdbId },
                )
                viewModel.position(12, 6)
                viewModel.refresh()
                runCurrent()
                assertEquals(12, viewModel.state.value.focusedIndex)
                assertEquals(3, backend.reads.size)
                viewModel.category(WatchlistCategory.ANIMATION)
                runCurrent()
                assertEquals(WatchlistCategory.ANIMATION, backend.reads.last().first)
                assertEquals(20, viewModel.state.value.entries.size)
            } finally {
                store.clear()
                Dispatchers.resetMain()
                WholphinDispatchers.IO = previousIo
            }
        }

    @Test fun removingTheFocusedFinalItemLeavesATruthfulEmptyPage() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            val store = androidx.lifecycle.ViewModelStore()
            try {
                val backend = Backend()
                var rows = listOf(entry(1))
                backend.members += key(1)
                backend.pageResult = { _, _ -> WatchlistPage(1, 1, rows.size, rows) }
                backend.write = { rows = emptyList() }
                val repository =
                    WatchlistRepository(
                        MutableStateFlow(WatchlistSession("A", backend) { true }),
                        backgroundScope,
                        { 100_000 },
                    )
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true), Store())
                store.put("watchlist", viewModel)
                runCurrent()
                viewModel.position(0, 6)
                viewModel.remove(
                    viewModel.state.value.entries
                        .single(),
                )
                runCurrent()
                assertTrue(
                    viewModel.state.value.entries
                        .isEmpty(),
                )
                assertEquals(0, viewModel.state.value.totalResults)
                assertEquals(0, viewModel.state.value.focusedIndex)
                assertNull(viewModel.state.value.error)
                assertEquals(1, backend.removals)
            } finally {
                store.clear()
                Dispatchers.resetMain()
                WholphinDispatchers.IO = previousIo
            }
        }

    @Test fun screenPageErrorRetriesSamePageAndRetainsLoadedRows() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            val store = androidx.lifecycle.ViewModelStore()
            try {
                val backend = Backend()
                var failed = true
                backend.members.addAll((1..21).map { key(it) })
                backend.pageResult = { _, page ->
                    if (page == 2 && failed) throw IOException("offline")
                    WatchlistPage(page, 2, 21, if (page == 1) (1..20).map(::entry) else listOf(entry(21)))
                }
                val repository =
                    WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true), Store())
                store.put("watchlist", viewModel)
                runCurrent()
                viewModel.position(19, 6)
                runCurrent()
                assertEquals(20, viewModel.state.value.entries.size)
                assertNotNull(viewModel.state.value.error)
                repeat(100) { viewModel.position(19, 6) }
                runCurrent()
                assertEquals(2, backend.reads.size)
                failed = false
                viewModel.retry()
                runCurrent()
                assertEquals(listOf(1, 2, 2), backend.reads.map { it.second })
                assertEquals(21, viewModel.state.value.entries.size)
                assertNull(viewModel.state.value.error)
            } finally {
                store.clear()
                Dispatchers.resetMain()
                WholphinDispatchers.IO = previousIo
            }
        }

    @Test fun leadingSlotsKeepAbsoluteColumnsStableAcrossTwentyItemPrepends() {
        for (columns in listOf(1, 3, 4, 5, 6, 7, 8, 9, 20, 21)) {
            for (firstPage in 1..50) {
                val offset = (firstPage - 1) * 20
                val before = gridLeadingSlots(offset, columns)
                for (localIndex in 0 until 20) {
                    assertEquals((offset + localIndex) % columns, (before + localIndex) % columns)
                }
                if (firstPage > 1) {
                    val earlier = gridLeadingSlots(offset - 20, columns)
                    assertEquals((before + 6) % columns, (earlier + 20 + 6) % columns)
                }
            }
        }
    }

    @Test fun prefetchLeadUsesTheGridReportedColumnCountNotTheApiPageSize() {
        for ((columns, firstPrefetchIndex) in listOf(4 to 8, 7 to 0)) {
            screenTest { backend, _, viewModel ->
                backend.pageResult = { _, page ->
                    WatchlistPage(page, 2, 40, if (page == 1) (1..20).map(::entry) else (21..40).map(::entry))
                }
                runCurrent()
                if (firstPrefetchIndex > 0) {
                    viewModel.position(firstPrefetchIndex - 1, columns)
                    runCurrent()
                    assertEquals(listOf(1), backend.reads.map { it.second })
                }
                viewModel.position(firstPrefetchIndex, columns)
                runCurrent()
                assertEquals(listOf(1, 2), backend.reads.map { it.second })
            }
        }
    }

    @Test fun randomTargetUsesTheWholeFilteredCountAndExactPageOffsets() {
        for (total in listOf(1, 19, 20, 21, 990)) {
            for (index in 0 until total) {
                var bound = -1
                val target =
                    watchlistRandomTarget(total) { received ->
                        bound = received
                        index
                    }
                assertEquals(total, bound)
                assertEquals(index, target.index)
                assertEquals(index / 20 + 1, target.page)
                assertEquals(index % 20, target.offset)
            }
        }
        assertEquals(30, watchlistRandomTarget(990) { 586 }.page)
        assertEquals(6, watchlistRandomTarget(990) { 586 }.offset)
        assertTrue(runCatching { watchlistRandomTarget(0) }.isFailure)
    }

    @Test fun randomJumpFetchesOnlyTargetAndNeighborsThenBrowsesBothDirections() =
        screenTest { backend, _, viewModel ->
            backend.pageResult = { _, page ->
                WatchlistPage(page, 50, 990, ((page - 1) * 20 + 1..page * 20).map(::entry))
            }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            assertEquals(listOf(1, 30, 29, 31), backend.reads.map { it.second })
            val jumped = viewModel.state.value
            assertEquals(29, jumped.firstPage)
            assertEquals(31, jumped.page)
            assertEquals(60, jumped.entries.size)
            assertEquals(26, jumped.focusedIndex)
            assertEquals(587, (jumped.firstPage - 1) * 20 + jumped.focusedIndex + 1)
            assertEquals(1L, jumped.randomFocusEpoch)
            assertEquals(587, jumped.entries[jumped.focusedIndex].key.tmdbId)

            viewModel.position(0, 6)
            runCurrent()
            val previous = viewModel.state.value
            assertEquals(28, previous.firstPage)
            assertEquals(20, previous.focusedIndex)
            assertEquals(561, (previous.firstPage - 1) * 20 + previous.focusedIndex + 1)
            assertEquals(561, previous.entries[previous.focusedIndex].key.tmdbId)
            viewModel.position(previous.entries.lastIndex, 6)
            runCurrent()
            assertEquals(32, viewModel.state.value.page)
            assertEquals(
                640,
                viewModel.state.value.entries
                    .last()
                    .key.tmdbId,
            )
            assertEquals(listOf(1, 30, 29, 31, 28, 32), backend.reads.map { it.second })
            viewModel.position(46, 6)
            viewModel.refresh(force = true)
            runCurrent()
            assertEquals(46, viewModel.state.value.focusedIndex)
            assertEquals(
                587,
                viewModel.state.value.entries[46]
                    .key.tmdbId,
            )
            assertEquals(28, viewModel.state.value.firstPage)
            val origin = viewModel.state.value.entries[46]
            viewModel.open(origin)
            assertEquals(origin.key, viewModel.state.value.focusedKey)
            assertEquals(
                origin.key,
                viewModel.state.value.entries[viewModel.state.value.focusedIndex]
                    .key,
            )
        }

    @Test fun randomJumpKeepsCombinedFiltersAndHandlesShortBoundaryPages() =
        screenTest { backend, _, viewModel ->
            backend.filteredPageResult = { category, page, _ ->
                val total = if (category == WatchlistCategory.MOVIES) 41 else 45
                WatchlistPage(page, 3, total, ((page - 1) * 20 + 1..minOf(page * 20, total)).map(::entry))
            }
            runCurrent()
            viewModel.category(WatchlistCategory.MOVIES)
            viewModel.owned(WatchlistFilter.YES)
            viewModel.watched(WatchlistFilter.NO)
            runCurrent()
            viewModel.jumpToRandom(40)
            runCurrent()
            val current = viewModel.state.value
            assertEquals(WatchlistCategory.MOVIES, current.category)
            assertEquals(WatchlistFilters(WatchlistFilter.YES, WatchlistFilter.NO), current.filters)
            assertEquals(3, current.page)
            assertEquals(2, current.firstPage)
            assertEquals(21, current.entries.size)
            assertEquals(41, (current.firstPage - 1) * 20 + current.focusedIndex + 1)
            assertEquals(listOf(3, 2), backend.reads.takeLast(2).map { it.second })
            assertTrue(backend.queries.takeLast(2).all { it == current.filters })
            viewModel.jumpToRandom(0)
            runCurrent()
            assertEquals(1, viewModel.state.value.firstPage)
            assertEquals(0, viewModel.state.value.focusedIndex)
        }

    @Test fun randomWaitsForNeighborsAndCanRetryAFailedAdjacentPage() =
        screenTest { backend, _, viewModel ->
            var failPrevious = true
            backend.pageResult = { _, page ->
                if (page == 29 && failPrevious) throw IOException("offline")
                WatchlistPage(page, 50, 990, ((page - 1) * 20 + 1..page * 20).map(::entry))
            }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            assertEquals(29, viewModel.state.value.failedPage)
            assertEquals(40, viewModel.state.value.entries.size)
            assertEquals(
                587,
                viewModel.state.value.entries[viewModel.state.value.focusedIndex]
                    .key.tmdbId,
            )
            failPrevious = false
            viewModel.retry()
            runCurrent()
            assertEquals(29, viewModel.state.value.firstPage)
            assertEquals(
                587,
                viewModel.state.value.entries[viewModel.state.value.focusedIndex]
                    .key.tmdbId,
            )
            viewModel.jumpToRandom(586)
            viewModel.jumpToRandom(0)
            runCurrent()
            assertEquals(1, viewModel.state.value.firstPage)
            assertEquals(
                1,
                viewModel.state.value.entries[viewModel.state.value.focusedIndex]
                    .key.tmdbId,
            )
            assertEquals(1, backend.reads.count { it.second == 30 })
        }

    @Test fun randomDoesNotPublishOrMoveFocusUntilAdjacentPagesSettle() =
        screenTest { backend, _, viewModel ->
            val neighborGate = CompletableDeferred<Unit>()
            backend.pageResult = { _, page ->
                if (page == 29) neighborGate.await()
                WatchlistPage(page, 50, 990, ((page - 1) * 20 + 1..page * 20).map(::entry))
            }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            assertEquals(20, viewModel.state.value.entries.size)
            assertEquals(0L, viewModel.state.value.randomFocusEpoch)
            assertEquals(listOf(1, 30, 29), backend.reads.map { it.second })
            neighborGate.complete(Unit)
            runCurrent()
            val settled = viewModel.state.value
            assertEquals(60, settled.entries.size)
            assertEquals(587, settled.focusedKey?.tmdbId)
            assertEquals(587, settled.entries[settled.focusedIndex].key.tmdbId)
            assertEquals(1L, settled.randomFocusEpoch)
            assertEquals(listOf(1, 30, 29, 31), backend.reads.map { it.second })
        }

    @Test fun categoryResetRejectsDelayedRandomPage() =
        screenTest { backend, _, viewModel ->
            val delayed = CompletableDeferred<Unit>()
            backend.pageResult = { category, page ->
                if (category == WatchlistCategory.ALL && page == 30) delayed.await()
                val total = if (category == WatchlistCategory.ALL) 990 else 1
                WatchlistPage(
                    page,
                    if (category == WatchlistCategory.ALL) 50 else 1,
                    total,
                    ((page - 1) * 20 + 1..minOf(page * 20, total)).map(::entry),
                )
            }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            viewModel.category(WatchlistCategory.MOVIES)
            delayed.complete(Unit)
            runCurrent()
            assertEquals(WatchlistCategory.MOVIES, viewModel.state.value.category)
            assertEquals(
                listOf(1),
                viewModel.state.value.entries
                    .map { it.key.tmdbId },
            )
            assertEquals(0L, viewModel.state.value.randomFocusEpoch)
        }

    @Test fun shiftedPageDoesNotSilentlyDeduplicateAbsolutePositions() =
        screenTest { backend, _, viewModel ->
            backend.pageResult = { _, page ->
                WatchlistPage(page, 2, 40, if (page == 1) (1..20).map(::entry) else (20..39).map(::entry))
            }
            runCurrent()
            viewModel.position(19, 6)
            runCurrent()
            assertEquals(20, viewModel.state.value.entries.size)
            assertEquals(1, viewModel.state.value.failedPage)
            assertTrue(
                viewModel.state.value.error
                    .orEmpty()
                    .contains("changed"),
            )
            viewModel.retry()
            runCurrent()
            assertEquals(20, viewModel.state.value.entries.size)
            assertNull(viewModel.state.value.error)
        }

    @Test fun failedRandomTargetRetriesTheSameAbsoluteSelection() =
        screenTest { backend, _, viewModel ->
            var failed = true
            backend.pageResult = { _, page ->
                if (page == 30 && failed) throw IOException("offline")
                WatchlistPage(page, 50, 990, ((page - 1) * 20 + 1..page * 20).map(::entry))
            }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            assertEquals(586, viewModel.state.value.failedRandomIndex)
            assertEquals(20, viewModel.state.value.entries.size)
            failed = false
            viewModel.retry()
            runCurrent()
            assertNull(viewModel.state.value.error)
            assertEquals(
                587,
                viewModel.state.value.entries[viewModel.state.value.focusedIndex]
                    .key.tmdbId,
            )
            assertEquals(2, backend.reads.count { it.second == 30 })
        }

    @Test fun randomTargetRecountsOnceWhenTheFilteredTotalShrinks() =
        screenTest { backend, _, viewModel ->
            var total = 990
            backend.pageResult = { _, page ->
                WatchlistPage(
                    page,
                    (total + 19) / 20,
                    total,
                    if (page > (total + 19) / 20) emptyList() else ((page - 1) * 20 + 1..minOf(page * 20, total)).map(::entry),
                )
            }
            runCurrent()
            total = 21
            viewModel.jumpToRandom(586)
            runCurrent()
            assertEquals(21, viewModel.state.value.totalResults)
            assertNull(viewModel.state.value.error)
            val current = viewModel.state.value
            assertTrue((current.firstPage - 1) * 20 + current.focusedIndex in 0 until 21)
            assertEquals(1, backend.reads.count { it.second == 30 })
            assertTrue(backend.reads.size <= 5)
        }

    @Test fun ownerChangeCannotRestoreAnOldRandomTarget() {
        val sessions = MutableStateFlow<WatchlistSession?>(null)
        screenTest(sessions = sessions) { backend, _, viewModel ->
            val gate = CompletableDeferred<Unit>()
            backend.pageResult = { _, page ->
                if (page == 30) withContext(NonCancellable) { gate.await() }
                WatchlistPage(page, 50, 990, ((page - 1) * 20 + 1..page * 20).map(::entry))
            }
            sessions.value = WatchlistSession("A", backend) { true }
            runCurrent()
            viewModel.jumpToRandom(586)
            runCurrent()
            val replacement = Backend()
            replacement.pageResult = { _, _ -> WatchlistPage(1, 1, 1, listOf(entry(1000))) }
            sessions.value = WatchlistSession("B", replacement) { true }
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertEquals("B", viewModel.state.value.owner)
            assertEquals(
                listOf(1000),
                viewModel.state.value.entries
                    .map { it.key.tmdbId },
            )
            assertEquals(0L, viewModel.state.value.randomFocusEpoch)
        }
    }
}
