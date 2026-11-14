package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionItem
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.ui.collections.CollectionsViewModel
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {
    private fun entry(id: Int) =
        CollectionsEntry(
            LibraryCollectionItem(id, "Collection $id", null, null, null, 1, 4, LibraryCollectionItem.Completeness.INCOMPLETE),
            null,
            null,
        )

    private fun page(
        skip: Int,
        total: Int = 45,
    ) = CollectionsPageData(
        skip,
        20,
        total,
        LibraryCollectionsResponse.Status.COMPLETE,
        false,
        0,
        ((skip + 1)..(skip + 20).coerceAtMost(total)).map(::entry),
    )

    private fun model(
        backend: CollectionsBackend,
        source: MutableStateFlow<CollectionsSession?>,
    ): CollectionsViewModel {
        val sessionSource = mockk<CollectionsSessionSource>()
        every { sessionSource.sessions } returns source
        return CollectionsViewModel(sessionSource, mockk(relaxed = true))
    }

    private fun session(
        backend: CollectionsBackend,
        owner: String = "one",
    ) = CollectionsSession(
        owner,
        SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()),
        backend,
        { true },
    )

    @Test fun browserCollectionBackKeepsOriginAndFocusedCard() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ) = page(skip, 2)

                        override suspend fun detail(id: Int) = CollectionDetailsData(id, "Collection $id", null, null, null, emptyList())
                    }
                val stack = mutableListOf<Destination>(Destination.Home(), Destination.Collections)
                val navigation = mockk<NavigationManager>(relaxed = true)
                every { navigation.navigateTo(any()) } answers {
                    stack.add(firstArg())
                    Unit
                }
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns MutableStateFlow(session(backend))
                val vm = CollectionsViewModel(source, navigation)
                runCurrent()
                vm.position(1, 6)
                vm.open(entry(2))
                assertEquals(Destination.SeerrCollection(2, 1, 4, "one"), stack.last())
                stack.removeLast()
                assertEquals(Destination.Collections, stack.last())
                assertEquals("tmdb:collection:2", vm.state.value.focusedKey)
                assertEquals(1, vm.state.value.focusedIndex)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun prefetchIsBoundedAndKeepsStableOrderAcrossThreePages() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ): CollectionsPageData {
                            calls += skip
                            return page(skip)
                        }

                        override suspend fun detail(id: Int): CollectionDetailsData =
                            CollectionDetailsData(id, "Collection $id", null, null, null, emptyList())
                    }
                val vm = model(backend, MutableStateFlow(session(backend)))
                runCurrent()
                assertEquals(listOf(0), calls)
                vm.position(10, 4)
                vm.position(10, 4)
                runCurrent()
                assertEquals(listOf(0, 20), calls)
                vm.position(30, 4)
                runCurrent()
                assertEquals(listOf(0, 20, 40), calls)
                assertEquals(
                    (1..45).map { "tmdb:collection:$it" },
                    vm.state.value.entries
                        .map { it.gridId },
                )
                assertEquals(45, vm.state.value.total)
                assertEquals("tmdb:collection:31", vm.state.value.focusedKey)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun oldFilterResponseCannotReplaceNewFilterOrFocus() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val delayed = CompletableDeferred<CollectionsPageData>()
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ): CollectionsPageData = if (filter == CollectionsFilter.ALL) delayed.await() else page(skip, 2)

                        override suspend fun detail(id: Int): CollectionDetailsData =
                            CollectionDetailsData(id, "Collection $id", null, null, null, emptyList())
                    }
                val vm = model(backend, MutableStateFlow(session(backend)))
                runCurrent()
                vm.filter(CollectionsFilter.COMPLETE)
                runCurrent()
                delayed.complete(page(0))
                runCurrent()
                assertEquals(CollectionsFilter.COMPLETE, vm.state.value.filter)
                assertEquals(
                    listOf("tmdb:collection:1", "tmdb:collection:2"),
                    vm.state.value.entries
                        .map { it.gridId },
                )
                assertFalse(vm.state.value.loading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun appendFailureRetainsRowsAndRetryUsesFailedOffset() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                var fail = true
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ): CollectionsPageData {
                            calls += skip
                            if (skip == 20 && fail) throw IOException("offline")
                            return page(skip)
                        }

                        override suspend fun detail(id: Int): CollectionDetailsData =
                            CollectionDetailsData(id, "Collection $id", null, null, null, emptyList())
                    }
                val vm = model(backend, MutableStateFlow(session(backend)))
                runCurrent()
                vm.position(10, 4)
                runCurrent()
                assertEquals(20, vm.state.value.entries.size)
                assertTrue(vm.state.value.error != null)
                assertEquals(20, vm.state.value.failedSkip)
                fail = false
                vm.retry()
                runCurrent()
                assertEquals(listOf(0, 20, 20), calls)
                assertEquals(40, vm.state.value.entries.size)
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun adminPermissionFailureIsDistinctFromEmptyResults() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ): CollectionsPageData = throw ClientException("Forbidden", 403)

                        override suspend fun detail(id: Int): CollectionDetailsData =
                            CollectionDetailsData(id, "", null, null, null, emptyList())
                    }
                val vm = model(backend, MutableStateFlow(session(backend)))
                runCurrent()
                assertTrue(vm.state.value.denied)
                assertEquals(null, vm.state.value.error)
                assertTrue(
                    vm.state.value.entries
                        .isEmpty(),
                )
                assertEquals(null, vm.state.value.status)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
