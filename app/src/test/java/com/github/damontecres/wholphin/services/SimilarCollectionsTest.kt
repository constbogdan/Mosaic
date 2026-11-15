package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import com.github.damontecres.wholphin.ui.collections.chooseReleasedSeed
import com.github.damontecres.wholphin.ui.collections.discoverSimilarCollectionIds
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SimilarCollectionsTest {
    private fun movie(
        id: Int,
        date: LocalDate?,
    ) = DiscoverItem(id, SeerrItemType.MOVIE, "Movie $id", null, null, SeerrAvailability.UNKNOWN, date, null, null, null, null)

    @Test fun seedUsesOnlyReleasedMembersAndDeterministicChoice() {
        val today = LocalDate.of(2026, 11, 15)
        val movies = listOf(movie(1, null), movie(2, today.plusDays(1)), movie(3, today), movie(4, today.minusYears(1)))
        assertEquals(
            4,
            chooseReleasedSeed(movies, today) { count ->
                assertEquals(2, count)
                1
            },
        )
        assertNull(chooseReleasedSeed(movies.take(2), today) { 0 })
    }

    @Test fun discoveryPreservesOrderDeduplicatesAndStopsAtLastPage() =
        runTest {
            val pages = mutableListOf<Int>()
            val ids =
                discoverSimilarCollectionIds(7) { page ->
                    pages += page
                    when (page) {
                        1 -> SimilarRecommendationsPage(2, listOf(null, 7, 3, 3, -1, 4))
                        else -> SimilarRecommendationsPage(2, listOf(4, 5, null))
                    }
                }
            assertEquals(listOf(3, 4, 5), ids)
            assertEquals(listOf(1, 2), pages)
        }

    @Test fun discoveryNeverExceedsTenCollectionsOrThreePages() =
        runTest {
            var calls = 0
            val ids =
                discoverSimilarCollectionIds(100) { page ->
                    calls++
                    SimilarRecommendationsPage(99, (page * 10 until page * 10 + 10).toList())
                }
            assertEquals(10, ids.size)
            assertEquals(1, calls)
            calls = 0
            val sparse =
                discoverSimilarCollectionIds(100) {
                    calls++
                    SimilarRecommendationsPage(99, listOf(it))
                }
            assertEquals(listOf(1, 2, 3), sparse)
            assertEquals(3, calls)
        }

    @Test fun laterRecommendationFailureRetainsEarlierCandidates() =
        runTest {
            val ids =
                discoverSimilarCollectionIds(7) { page ->
                    if (page == 2) throw IOException("optional page failed")
                    SimilarRecommendationsPage(3, listOf(3, 4))
                }
            assertEquals(listOf(3, 4), ids)
        }

    @Test fun enrichmentUsesOneAuthenticatedRecommendationsCallAndIgnoresUnresolved() =
        runTest {
            val requests = mutableListOf<String>()
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        requests += request.url.toString()
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("fixture")
                            .body(
                                """{"page":1,"totalPages":1,"results":[
                              {"id":1,"mediaType":"movie","collectionMembership":{"kind":"collection","collectionId":12}},
                              {"id":2,"mediaType":"movie","collectionMembership":{"kind":"standalone"}},
                              {"id":3,"mediaType":"movie","collectionMembership":{"kind":"unresolved"}},
                              {"id":4,"mediaType":"movie","collectionMembership":{"kind":"collection","collectionId":0}}
                            ]}""".toResponseBody("application/json".toMediaType()),
                            ).build()
                    }.build()
            val client = SeerrApiClient("http://seerr.invalid/api/v1", "fixture-key", http)
            val current =
                CurrentSeerr(
                    SeerrServer(1, "http://seerr.invalid"),
                    SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "admin", null, null),
                    User(id = 7),
                    PublicSettings(),
                )
            val page = SeerrCollectionsBackend(client, current).similarRecommendations(42, 1)
            assertEquals(listOf(12, null, null, null), page.collectionIds)
            assertEquals(1, requests.size)
            assertTrue(requests.single().contains("/api/v1/movie/42/recommendations"))
            assertTrue(requests.single().contains("includeCollectionIds=true"))
            assertTrue(requests.single().contains("page=1"))
        }

    @Test fun newDetailEntryKeepsSeedAndMetadataOrderDespiteOutOfOrderCompletion() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val first = CompletableDeferred<CollectionDetailsData>()
                val second = CompletableDeferred<CollectionDetailsData>()
                var recommendationCalls = 0
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ) = error("Not used")

                        override suspend fun detail(id: Int): CollectionDetailsData =
                            when (id) {
                                7 -> CollectionDetailsData(id, "Origin", null, null, null, listOf(movie(42, LocalDate.of(2020, 1, 1))))
                                3 -> first.await()
                                4 -> second.await()
                                else -> error("Unexpected collection")
                            }

                        override suspend fun similarRecommendations(
                            seedMovieId: Int,
                            page: Int,
                        ): SimilarRecommendationsPage {
                            assertEquals(42, seedMovieId)
                            recommendationCalls++
                            return SimilarRecommendationsPage(1, listOf(3, 4))
                        }
                    }
                val sessions = MutableStateFlow<CollectionsSession?>(testSession("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val stack = mutableListOf<Destination>(Destination.SeerrCollection(7, null, null, "one"))
                val navigation = mockk<NavigationManager>(relaxed = true)
                every { navigation.backStack } returns stack
                every { navigation.navigateTo(any()) } answers {
                    stack.add(firstArg())
                    Unit
                }
                val vm = SeerrCollectionDetailsViewModel(source, navigation)
                vm.show(7, "one")
                runCurrent()
                assertEquals(42, vm.state.value.similarSeedId)
                vm.retry() // A normal detail refresh must not invalidate the optional row load.
                runCurrent()
                second.complete(CollectionDetailsData(4, "Fourth", null, null, null, emptyList()))
                runCurrent()
                assertTrue(vm.state.value.similarLoading)
                first.complete(CollectionDetailsData(3, "Third", null, null, null, emptyList()))
                runCurrent()
                assertEquals(
                    listOf(3, 4),
                    vm.state.value.similar
                        .map { it.id },
                )
                vm.retry()
                runCurrent()
                assertEquals(42, vm.state.value.similarSeedId)
                assertEquals(1, recommendationCalls)
                vm.focusSimilar(4)
                vm.openSimilar(4)
                assertEquals(4, (stack.last() as Destination.SeerrCollection).collectionId)
                assertNull((stack.last() as Destination.SeerrCollection).availableMovies)
                val firstToken = (stack.last() as Destination.SeerrCollection).entryToken
                vm.openSimilar(4)
                assertEquals(2, stack.size)
                stack.removeLast()
                vm.openSimilar(4)
                assertEquals(2, stack.size)
                assertTrue((stack.last() as Destination.SeerrCollection).entryToken != firstToken)
                assertEquals(4, vm.state.value.similarFocusedId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionChangeDiscardsPendingSimilarResults() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pending = CompletableDeferred<SimilarRecommendationsPage>()
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ) = error("Not used")

                        override suspend fun detail(id: Int) =
                            CollectionDetailsData(id, "Origin", null, null, null, listOf(movie(42, LocalDate.of(2020, 1, 1))))

                        override suspend fun similarRecommendations(
                            seedMovieId: Int,
                            page: Int,
                        ) = pending.await()
                    }
                val sessions = MutableStateFlow<CollectionsSession?>(testSession("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true))
                vm.show(7, "one")
                runCurrent()
                assertTrue(vm.state.value.similarLoading)
                sessions.value = testSession("two", backend)
                runCurrent()
                pending.complete(SimilarRecommendationsPage(1, listOf(3)))
                runCurrent()
                assertEquals("two", vm.state.value.owner)
                assertTrue(
                    vm.state.value.similar
                        .isEmpty(),
                )
                assertNull(vm.state.value.similarSeedId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun failedCandidateDetailDoesNotFailTheWholeRow() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    object : CollectionsBackend {
                        override suspend fun page(
                            skip: Int,
                            filter: CollectionsFilter,
                            sort: CollectionsSort,
                        ) = error("Not used")

                        override suspend fun detail(id: Int) =
                            when (id) {
                                7 -> CollectionDetailsData(id, "Origin", null, null, null, listOf(movie(42, LocalDate.of(2020, 1, 1))))
                                3 -> throw IOException("metadata unavailable")
                                4 -> CollectionDetailsData(id, "Fourth", null, null, null, emptyList())
                                else -> error("Unexpected collection")
                            }

                        override suspend fun similarRecommendations(
                            seedMovieId: Int,
                            page: Int,
                        ) = SimilarRecommendationsPage(1, listOf(3, 4))
                    }
                val sessions = MutableStateFlow<CollectionsSession?>(testSession("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true))
                vm.show(7, "one")
                runCurrent()
                assertEquals(
                    listOf(4),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertTrue(!vm.state.value.similarLoading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun testSession(
        owner: String,
        backend: CollectionsBackend,
    ) = CollectionsSession(owner, SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()), backend, { true })
}
