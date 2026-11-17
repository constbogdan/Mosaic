package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.leavingsoon.LeavingSoonViewModel
import com.github.damontecres.wholphin.ui.nav.Destination
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
class LeavingSoonViewModelTest {
    private fun entry(id: Int): LeavingSoonEntry =
        LeavingSoonEntry(
            canonicalMediaId = id,
            item =
                DiscoverItem(
                    id,
                    SeerrItemType.MOVIE,
                    "Movie $id",
                    null,
                    null,
                    SeerrAvailability.UNKNOWN,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
            hasTmdbIdentity = true,
            title = "Movie $id",
            posterUrl = null,
            year = null,
            removeAt = null,
        )

    private fun page(
        number: Int,
        total: Int = 45,
    ): LeavingSoonPageData =
        LeavingSoonPageData(
            number,
            (total + 19) / 20,
            total,
            20,
            (1..(total - (number - 1) * 20).coerceIn(0, 20)).map { entry((number - 1) * 20 + it) },
        )

    private fun model(backend: LeavingSoonBackend): Pair<LeavingSoonViewModel, MutableStateFlow<LeavingSoonSession?>> {
        val source = mockk<LeavingSoonSessionSource>()
        val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
        val sessions = MutableStateFlow<LeavingSoonSession?>(LeavingSoonSession("one", backend, { true }, client))
        every { source.sessions } returns sessions
        return LeavingSoonViewModel(source, mockk(relaxed = true)) to sessions
    }

    @Test fun tabSwitchResetsPagesAndRequestsServerFilter() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Pair<LeavingSoonCategory, Int>>()
                val (vm, _) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ): LeavingSoonPageData {
                                calls += category to page
                                return this@LeavingSoonViewModelTest.page(page)
                            }
                        },
                    )
                runCurrent()
                vm.category(LeavingSoonCategory.MOVIES)
                runCurrent()
                vm.category(LeavingSoonCategory.SERIES)
                runCurrent()
                assertEquals(
                    listOf(
                        LeavingSoonCategory.ALL to 1,
                        LeavingSoonCategory.MOVIES to 1,
                        LeavingSoonCategory.SERIES to 1,
                    ),
                    calls,
                )
                assertEquals(20, vm.state.value.entries.size)
                assertEquals(LeavingSoonCategory.SERIES, vm.state.value.category)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sequentialPrefetchKeepsFocusedIdentityAndServerTotals() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val (vm, _) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ): LeavingSoonPageData {
                                calls += page
                                return this@LeavingSoonViewModelTest.page(page)
                            }
                        },
                    )
                runCurrent()
                vm.position(18, 6)
                runCurrent()
                assertEquals(listOf(1, 2), calls)
                assertEquals(40, vm.state.value.entries.size)
                assertEquals(45, vm.state.value.totalResults)
                assertEquals("canonical:19", vm.state.value.focusedKey)
                vm.position(39, 6)
                runCurrent()
                assertEquals(listOf(1, 2, 3), calls)
                assertEquals(45, vm.state.value.entries.size)
                assertEquals("canonical:40", vm.state.value.focusedKey)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun incrementalFailureRetainsCardsAndRetryDoesNotDuplicatePage() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val (vm, _) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ): LeavingSoonPageData {
                                calls += page
                                if (page == 2 && calls.count { it == 2 } == 1) throw IOException("offline")
                                return this@LeavingSoonViewModelTest.page(page)
                            }
                        },
                    )
                runCurrent()
                vm.position(18, 6)
                runCurrent()
                assertEquals(20, vm.state.value.entries.size)
                assertEquals(2, vm.state.value.failedPage)
                assertFalse(vm.state.value.loading)
                vm.retry()
                runCurrent()
                assertEquals(listOf(1, 2, 2), calls)
                assertEquals(40, vm.state.value.entries.size)
                assertEquals(null, vm.state.value.error)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun initialFailureIsNotPresentedAsEmptyAndCanRetry() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var attempts = 0
                val (vm, _) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ): LeavingSoonPageData {
                                attempts++
                                if (attempts == 1) throw IOException("unavailable")
                                return this@LeavingSoonViewModelTest.page(1, 1)
                            }
                        },
                    )
                runCurrent()
                assertEquals(1, vm.state.value.failedPage)
                assertTrue(vm.state.value.error != null)
                assertEquals(0, vm.state.value.entries.size)
                vm.retry()
                runCurrent()
                assertEquals(2, attempts)
                assertEquals(null, vm.state.value.error)
                assertEquals(1, vm.state.value.entries.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun pageWindowIsBoundedAndCanReloadPrecedingPageWithoutLosingFocus() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val calls = mutableListOf<Int>()
                val (vm, _) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ): LeavingSoonPageData {
                                calls += page
                                return this@LeavingSoonViewModelTest.page(page, 120)
                            }
                        },
                    )
                runCurrent()
                for (page in 1..5) {
                    vm.position(vm.state.value.entries.lastIndex, 6)
                    runCurrent()
                }
                assertEquals(listOf(1, 2, 3, 4, 5, 6), calls)
                assertEquals(2, vm.state.value.firstPage)
                assertEquals(6, vm.state.value.lastPage)
                assertEquals(100, vm.state.value.entries.size)
                assertEquals("canonical:100", vm.state.value.focusedKey)
                vm.position(0, 6)
                runCurrent()
                assertEquals(1, vm.state.value.firstPage)
                assertEquals(5, vm.state.value.lastPage)
                assertEquals(100, vm.state.value.entries.size)
                assertEquals("canonical:21", vm.state.value.focusedKey)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionSwitchRejectsLateOldPage() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val oldPage = CompletableDeferred<LeavingSoonPageData>()
                val (vm, sessions) =
                    model(
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ) = withContext(NonCancellable) { oldPage.await() }
                        },
                    )
                runCurrent()
                val client = SeerrApiClient("http://other.invalid/api/v1", null, OkHttpClient())
                sessions.value =
                    LeavingSoonSession(
                        "two",
                        object : LeavingSoonBackend {
                            override suspend fun page(
                                category: LeavingSoonCategory,
                                page: Int,
                            ) = this@LeavingSoonViewModelTest.page(1, 1)
                        },
                        { true },
                        client,
                    )
                runCurrent()
                oldPage.complete(page(1))
                runCurrent()
                assertEquals("two", vm.state.value.owner)
                assertEquals(1, vm.state.value.entries.size)
                assertTrue(
                    vm.state.value.entries
                        .none { it.canonicalMediaId == 2 },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun openingMovieAndSeriesUsesTypedDetailsAndRetainsOriginFocus() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val source = mockk<LeavingSoonSessionSource>()
                val client = SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient())
                val movie = entry(1)
                val series = entry(2).copy(item = entry(2).item.copy(type = SeerrItemType.TV))
                val backend =
                    object : LeavingSoonBackend {
                        override suspend fun page(
                            category: LeavingSoonCategory,
                            page: Int,
                        ) = LeavingSoonPageData(1, 1, 2, 20, listOf(movie, series))
                    }
                every { source.sessions } returns MutableStateFlow(LeavingSoonSession("owner", backend, { true }, client))
                val navigation = mockk<NavigationManager>(relaxed = true)
                val vm = LeavingSoonViewModel(source, navigation)
                runCurrent()
                vm.open(movie)
                assertEquals(movie.key, vm.state.value.focusedKey)
                verify(exactly = 1) {
                    navigation.navigateTo(match { it is Destination.DiscoveredItem && it.item.type == SeerrItemType.MOVIE })
                }
                vm.open(series)
                assertEquals(series.key, vm.state.value.focusedKey)
                verify(exactly = 1) {
                    navigation.navigateTo(match { it is Destination.DiscoveredItem && it.item.type == SeerrItemType.TV })
                }
                vm.open(series.copy(hasTmdbIdentity = false))
                vm.open(entry(99))
                verify(exactly = 2) { navigation.navigateTo(any()) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
