package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.data.model.WatchlistPage
import com.github.damontecres.wholphin.data.model.watchlistFocusAfterRemoval
import com.github.damontecres.wholphin.ui.watchlist.WatchlistViewModel
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
        var additions = 0
        var removals = 0
        var lookup: (suspend (Set<MediaKey.Catalog>) -> Map<MediaKey.Catalog, Boolean>)? = null
        var write: (suspend () -> Unit)? = null
        var pageResult: suspend (WatchlistCategory, Int) -> WatchlistPage = { _, page -> WatchlistPage(page, 1, 0, emptyList()) }

        override suspend fun page(
            category: WatchlistCategory,
            page: Int,
        ): WatchlistPage {
            reads += category to page
            return pageResult(category, page)
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

    @Test fun screenLoadsPastTwentyDeduplicatesAndBoundsRepeatedFocusCallbacks() =
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
                            2 -> 20..39
                            else -> 40..45
                        }
                    WatchlistPage(page, 3, 45, ids.map(::entry))
                }
                backend.members.addAll((1..45).map { key(it) })
                val repository =
                    WatchlistRepository(MutableStateFlow(WatchlistSession("A", backend) { true }), backgroundScope, { 100_000 })
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true))
                store.put("watchlist", viewModel)
                runCurrent()
                assertEquals(20, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(19) }
                runCurrent()
                assertEquals(39, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(38) }
                runCurrent()
                assertEquals(45, viewModel.state.value.entries.size)
                repeat(100) { viewModel.position(44) }
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
                viewModel.position(12)
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
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true))
                store.put("watchlist", viewModel)
                runCurrent()
                viewModel.position(0)
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
                val viewModel = WatchlistViewModel(repository, mockk(relaxed = true))
                store.put("watchlist", viewModel)
                runCurrent()
                viewModel.position(19)
                runCurrent()
                assertEquals(20, viewModel.state.value.entries.size)
                assertNotNull(viewModel.state.value.error)
                repeat(100) { viewModel.position(19) }
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
}
