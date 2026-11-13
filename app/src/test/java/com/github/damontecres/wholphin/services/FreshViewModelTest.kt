package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.FreshStatus
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.fresh.FreshViewModel
import com.github.damontecres.wholphin.ui.fresh.freshGridPositionCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FreshViewModelTest {
    private fun entries(
        start: Int,
        count: Int,
    ) = (start until start + count).map { id ->
        FreshEntry(DiscoverItem(id, SeerrItemType.MOVIE, "Movie $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null))
    }

    private fun page(
        number: Int,
        total: Int = 45,
    ) = FreshPageData(
        number,
        3,
        total,
        FreshStatus.Status.READY,
        entries((number - 1) * 20, (total - (number - 1) * 20).coerceIn(0, 20)),
    )

    private fun model(backend: FreshBackend): FreshViewModel {
        val source = mockk<FreshSessionSource>()
        val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
        every { source.sessions } returns MutableStateFlow(FreshSession("owner", backend, { true }, client))
        return FreshViewModel(source, mockk(relaxed = true))
    }

    @Test fun cardGridCallbackPassesActualIndexAndColumnCount() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += page
                                return this@FreshViewModelTest.page(page)
                            }
                        },
                    )
                runCurrent()
                val cardGridCallback = freshGridPositionCallback(vm::position)
                cardGridCallback(4, 5)
                assertEquals(5, vm.state.value.focusedIndex)
                assertEquals("MOVIE:5", vm.state.value.focusedKey)
                assertEquals(listOf(1), calls)
                cardGridCallback(4, 9)
                runCurrent()
                assertEquals(9, vm.state.value.focusedIndex)
                assertEquals(listOf(1, 2), calls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun insertedItemBeforeSecondPageRestartsAndRetainsTypedFocus() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += page
                                return when (calls.size) {
                                    1 -> {
                                        this@FreshViewModelTest.page(1)
                                    }

                                    2 -> {
                                        this@FreshViewModelTest.page(2, 46)
                                    }

                                    else -> {
                                        this@FreshViewModelTest.page(1, 46).copy(
                                            entries =
                                                listOf(
                                                    FreshEntry(
                                                        DiscoverItem(
                                                            1000,
                                                            SeerrItemType.MOVIE,
                                                            "New",
                                                            null,
                                                            null,
                                                            SeerrAvailability.UNKNOWN,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                            null,
                                                        ),
                                                    ),
                                                ) +
                                                    entries(0, 19),
                                        )
                                    }
                                }
                            }
                        },
                    )
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(listOf(1, 2, 1), calls)
                assertEquals(1, vm.state.value.page)
                assertEquals(46, vm.state.value.totalResults)
                assertEquals("MOVIE:9", vm.state.value.focusedKey)
                assertEquals(10, vm.state.value.focusedIndex)
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun removedItemBeforeSecondPageRestartsWithSafeFocusFallback() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += page
                                return when (calls.size) {
                                    1 -> this@FreshViewModelTest.page(1)
                                    2 -> this@FreshViewModelTest.page(2, 44)
                                    else -> this@FreshViewModelTest.page(1, 44).copy(entries = entries(1, 20))
                                }
                            }
                        },
                    )
                runCurrent()
                vm.position(0, 12)
                runCurrent()
                assertEquals(listOf(1, 2, 1), calls)
                assertEquals("MOVIE:1", vm.state.value.focusedKey)
                assertEquals(0, vm.state.value.focusedIndex)
                assertEquals(44, vm.state.value.totalResults)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun duplicateAcrossPagesRestartsEvenWhenTotalIsUnchanged() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += page
                                return if (calls.size == 2) {
                                    this@FreshViewModelTest.page(2).copy(entries = entries(19, 20))
                                } else {
                                    this@FreshViewModelTest.page(1)
                                }
                            }
                        },
                    )
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(listOf(1, 2, 1), calls)
                assertEquals(20, vm.state.value.entries.size)
                assertEquals("MOVIE:9", vm.state.value.focusedKey)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun repeatedShiftsStopAutomaticallyAndManualRetryStartsAtFirstPage() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += page
                                return when (calls.size) {
                                    1 -> this@FreshViewModelTest.page(1)
                                    2 -> this@FreshViewModelTest.page(2, 46)
                                    3 -> this@FreshViewModelTest.page(1, 46)
                                    4 -> this@FreshViewModelTest.page(2, 47)
                                    else -> this@FreshViewModelTest.page(1, 47)
                                }
                            }
                        },
                    )
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(listOf(1, 2, 1, 2), calls)
                assertEquals(1, vm.state.value.failedPage)
                assertTrue(
                    vm.state.value.error
                        ?.contains("changed") == true,
                )
                vm.retry()
                runCurrent()
                assertEquals(listOf(1, 2, 1, 2, 1), calls)
                assertEquals(47, vm.state.value.totalResults)
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun abandonedRecoveryResponseCannotReplaceNewCategory() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val delayedRecovery = CompletableDeferred<FreshPageData>()
                val calls = mutableListOf<Pair<FreshCategory, Int>>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += category to page
                                return when (calls.size) {
                                    1 -> this@FreshViewModelTest.page(1)
                                    2 -> this@FreshViewModelTest.page(2, 46)
                                    3 -> withContext(NonCancellable) { delayedRecovery.await() }
                                    else -> this@FreshViewModelTest.page(1)
                                }
                            }
                        },
                    )
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(listOf(FreshCategory.ALL to 1, FreshCategory.ALL to 2, FreshCategory.ALL to 1), calls)
                vm.category(FreshCategory.MOVIES)
                runCurrent()
                assertEquals(FreshCategory.MOVIES, vm.state.value.category)
                assertEquals(20, vm.state.value.entries.size)
                delayedRecovery.complete(this@FreshViewModelTest.page(1, 46))
                runCurrent()
                assertEquals(FreshCategory.MOVIES, vm.state.value.category)
                assertEquals(45, vm.state.value.totalResults)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun categorySwitchRejectsLateOldResponseAndKeepsNewSort() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val old = CompletableDeferred<FreshPageData>()
                val backend =
                    object : FreshBackend {
                        override suspend fun page(
                            category: FreshCategory,
                            sort: FreshSort,
                            page: Int,
                        ): FreshPageData =
                            if (category ==
                                FreshCategory.ALL
                            ) {
                                withContext(NonCancellable) { old.await() }
                            } else {
                                this@FreshViewModelTest.page(page)
                            }
                    }
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                val sessions = MutableStateFlow<FreshSession?>(FreshSession("owner", backend, { true }, client))
                val source = mockk<FreshSessionSource>()
                every { source.sessions } returns sessions
                val vm = FreshViewModel(source, mockk(relaxed = true))
                runCurrent()
                vm.category(FreshCategory.ANIMATION)
                vm.sort(FreshSort.RATING)
                runCurrent()
                assertEquals(FreshCategory.ANIMATION, vm.state.value.category)
                assertEquals(FreshSort.RATING, vm.state.value.sort)
                assertEquals(20, vm.state.value.entries.size)
                old.complete(page(1, 1))
                runCurrent()
                assertEquals(20, vm.state.value.entries.size)
                assertEquals(FreshSort.RATING, vm.state.value.sort)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun documentarySwitchResetsAnimationPaginationAndRejectsDelayedAppend() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val delayed = CompletableDeferred<FreshPageData>()
                val calls = mutableListOf<Pair<FreshCategory, Int>>()
                val vm =
                    model(
                        object : FreshBackend {
                            override suspend fun page(
                                category: FreshCategory,
                                sort: FreshSort,
                                page: Int,
                            ): FreshPageData {
                                calls += category to page
                                return if (category == FreshCategory.ANIMATION && page == 2) {
                                    withContext(NonCancellable) { delayed.await() }
                                } else {
                                    this@FreshViewModelTest.page(page)
                                }
                            }
                        },
                    )
                runCurrent()
                vm.category(FreshCategory.ANIMATION)
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(FreshCategory.ANIMATION to 2, calls.last())
                vm.category(FreshCategory.DOCUMENTARY)
                runCurrent()
                assertEquals(FreshCategory.DOCUMENTARY to 1, calls.last())
                assertEquals(FreshCategory.DOCUMENTARY, vm.state.value.category)
                assertEquals(1, vm.state.value.page)
                assertEquals(20, vm.state.value.entries.size)
                delayed.complete(page(2))
                runCurrent()
                assertEquals(1, vm.state.value.page)
                assertEquals(20, vm.state.value.entries.size)
                assertEquals(FreshCategory.DOCUMENTARY, vm.state.value.category)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun prefetchAppendsInOrderAndDetailsRetainsExactTypedFocus() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val backend =
                    object : FreshBackend {
                        override suspend fun page(
                            category: FreshCategory,
                            sort: FreshSort,
                            page: Int,
                        ): FreshPageData {
                            calls += page
                            return this@FreshViewModelTest.page(page)
                        }
                    }
                val source = mockk<FreshSessionSource>()
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                every { source.sessions } returns MutableStateFlow(FreshSession("owner", backend, { true }, client))
                val navigation = mockk<NavigationManager>(relaxed = true)
                val vm = FreshViewModel(source, navigation)
                runCurrent()
                vm.position(5, 4)
                runCurrent()
                assertEquals(listOf(1), calls)
                vm.position(9, 4)
                runCurrent()
                assertEquals(listOf(1, 2), calls)
                assertEquals(40, vm.state.value.entries.size)
                assertEquals(
                    (0 until 40).toList(),
                    vm.state.value.entries
                        .map { it.item.id },
                )
                vm.position(29, 4)
                runCurrent()
                assertEquals(listOf(1, 2, 3), calls)
                assertEquals(45, vm.state.value.entries.size)
                val selected = vm.state.value.entries[30]
                vm.open(selected)
                assertEquals(selected.key, vm.state.value.focusedKey)
                assertEquals(30, vm.state.value.focusedIndex)
                verify(exactly = 1) { navigation.navigateTo(any()) }
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionLossClearsRowsAndLatePageCannotRestoreThem() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val deferred = CompletableDeferred<FreshPageData>()
                val backend =
                    object : FreshBackend {
                        override suspend fun page(
                            category: FreshCategory,
                            sort: FreshSort,
                            page: Int,
                        ): FreshPageData = withContext(NonCancellable) { deferred.await() }
                    }
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                var active = true
                val sessions = MutableStateFlow<FreshSession?>(FreshSession("owner", backend, { active }, client))
                val source = mockk<FreshSessionSource>()
                every { source.sessions } returns sessions
                val vm = FreshViewModel(source, mockk(relaxed = true))
                runCurrent()
                active = false
                sessions.value = null
                runCurrent()
                assertEquals(null, vm.state.value.owner)
                deferred.complete(page(1))
                runCurrent()
                assertTrue(
                    vm.state.value.entries
                        .isEmpty(),
                )
                assertFalse(vm.state.value.loading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun appendFailureKeepsLoadedRowsAndRetryCompletesThePage() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var fail = true
                val backend =
                    object : FreshBackend {
                        override suspend fun page(
                            category: FreshCategory,
                            sort: FreshSort,
                            page: Int,
                        ): FreshPageData {
                            if (page == 2 && fail) throw IOException("fixture")
                            return this@FreshViewModelTest.page(page)
                        }
                    }
                val source = mockk<FreshSessionSource>()
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                every { source.sessions } returns MutableStateFlow(FreshSession("owner", backend, { true }, client))
                val vm = FreshViewModel(source, mockk(relaxed = true))
                runCurrent()
                vm.position(9, 4)
                runCurrent()
                assertEquals(20, vm.state.value.entries.size)
                assertEquals(2, vm.state.value.failedPage)
                assertTrue(vm.state.value.error != null)
                fail = false
                vm.retry()
                runCurrent()
                assertEquals(40, vm.state.value.entries.size)
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun disabledFeedIsAnEmptyNonErrorState() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    object : FreshBackend {
                        override suspend fun page(
                            category: FreshCategory,
                            sort: FreshSort,
                            page: Int,
                        ) = FreshPageData(1, 0, 0, FreshStatus.Status.DISABLED, emptyList())
                    }
                val source = mockk<FreshSessionSource>()
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                every { source.sessions } returns MutableStateFlow(FreshSession("owner", backend, { true }, client))
                val vm = FreshViewModel(source, mockk(relaxed = true))
                runCurrent()
                assertEquals(FreshStatus.Status.DISABLED, vm.state.value.status)
                assertTrue(
                    vm.state.value.entries
                        .isEmpty(),
                )
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
