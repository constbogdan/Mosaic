package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.CollectionRequestEligibility
import com.github.damontecres.wholphin.ui.collections.CollectionRequestOutcome
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import com.github.damontecres.wholphin.ui.collections.collectionRequestEligibility
import com.github.damontecres.wholphin.ui.detail.discover.MovieRequest
import com.github.damontecres.wholphin.ui.detail.discover.SeerrProfile
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionRequestTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val current =
        CurrentSeerr(
            SeerrServer(1, "http://seerr.invalid"),
            SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "user", null, null),
            User(id = 1, permissions = 32),
            PublicSettings(),
        )

    private fun movie(
        id: Int,
        date: LocalDate? = LocalDate.of(2020, 1, 1),
    ) = DiscoverItem(id, SeerrItemType.MOVIE, "Movie $id", null, null, SeerrAvailability.UNKNOWN, date, null, null, null, null)

    @Test fun requestabilityUsesCurrentMovieDetailsAndSharedPolicy() {
        val item = movie(10)
        assertEquals(CollectionRequestEligibility.ELIGIBLE, collectionRequestEligibility(item, MovieDetails(id = 10), current, today))
        assertEquals(
            CollectionRequestEligibility.AVAILABLE,
            collectionRequestEligibility(item, MovieDetails(id = 10, mediaInfo = MediaInfo(status = 5)), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.REQUESTED,
            collectionRequestEligibility(item, MovieDetails(id = 10, mediaInfo = MediaInfo(status = 2)), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.PROCESSING,
            collectionRequestEligibility(item, MovieDetails(id = 10, mediaInfo = MediaInfo(status = 3)), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.EXCLUDED,
            collectionRequestEligibility(item, MovieDetails(id = 10, mediaInfo = MediaInfo(status = 6)), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.EXCLUDED,
            collectionRequestEligibility(movie(10, today.plusDays(1)), MovieDetails(id = 10), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.UNKNOWN,
            collectionRequestEligibility(movie(10, null), MovieDetails(id = 10), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.UNKNOWN,
            collectionRequestEligibility(item, MovieDetails(id = 11), current, today),
        )
        assertEquals(
            CollectionRequestEligibility.EXCLUDED,
            collectionRequestEligibility(item, MovieDetails(id = 10), current.copy(config = User(id = 1, permissions = 0)), today),
        )
    }

    private class FakeBackend(
        val items: List<DiscoverItem>,
    ) : CollectionsBackend {
        val statuses = items.associate { it.id to CollectionRequestEligibility.ELIGIBLE }.toMutableMap()
        val posts = mutableListOf<MovieRequest>()
        var submitBehavior: suspend (MovieRequest) -> MediaRequest = { MediaRequest(it.movieId!!, 1) }

        override suspend fun page(
            skip: Int,
            filter: CollectionsFilter,
            sort: CollectionsSort,
        ) = CollectionsPageData(skip, 20, 0, LibraryCollectionsResponse.Status.COMPLETE, false, 0, emptyList())

        override suspend fun detail(id: Int) = CollectionDetailsData(id, "Collection", null, null, null, items)

        override suspend fun requestEligibility(item: DiscoverItem) = statuses.getValue(item.id)

        override suspend fun profiles() =
            SeerrRequestData(
                serverId = 0,
                profiles = listOf(SeerrProfile(12, "1080p HDR", true), SeerrProfile(8, "2160p HDR", false)),
            )

        override suspend fun submit(request: MovieRequest): MediaRequest {
            posts += request
            return submitBehavior(request)
        }
    }

    private fun viewModel(sessions: MutableStateFlow<CollectionsSession?>): SeerrCollectionDetailsViewModel {
        val source = mockk<CollectionsSessionSource>()
        every { source.sessions } returns sessions
        return SeerrCollectionDetailsViewModel(source, mockk(relaxed = true))
    }

    private fun session(
        backend: CollectionsBackend,
        owner: String = "one",
        valid: () -> Boolean = { true },
    ) = CollectionsSession(owner, SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()), backend, valid)

    @Test fun selectionAndDirectQualitySubmissionAreSingleFlightAndStandardOnly() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = FakeBackend(listOf(movie(1), movie(2), movie(3)))
                backend.statuses[3] = CollectionRequestEligibility.REQUESTED
                val gate = CompletableDeferred<Unit>()
                backend.submitBehavior = { request ->
                    gate.await()
                    MediaRequest(request.movieId!!, 1)
                }
                val vm = viewModel(MutableStateFlow(session(backend)))
                vm.show(7, "one")
                runCurrent()
                vm.openRequest()
                runCurrent()
                assertEquals(
                    2,
                    vm.state.value.request
                        ?.selectedCount,
                )
                vm.toggleAllRequests()
                assertEquals(
                    0,
                    vm.state.value.request
                        ?.selectedCount,
                )
                vm.toggleRequest(2)
                assertEquals(
                    1,
                    vm.state.value.request
                        ?.selectedCount,
                )
                vm.submitRequests("1080p HDR")
                vm.submitRequests("1080p HDR")
                runCurrent()
                assertEquals(listOf(2), backend.posts.map { it.movieId })
                assertEquals(
                    0,
                    backend.posts
                        .single()
                        .data.serverId,
                )
                assertEquals(12, backend.posts.single().profileId)
                assertFalse(backend.posts.single().is4k)
                vm.closeRequest()
                assertTrue(
                    vm.state.value.request
                        ?.submitting == true,
                )
                gate.complete(Unit)
                runCurrent()
                assertEquals(
                    1,
                    vm.state.value.request
                        ?.submitted,
                )
                assertEquals(
                    CollectionRequestOutcome.REQUESTED,
                    vm.state.value.request
                        ?.movies
                        ?.first { it.item.id == 2 }
                        ?.outcome,
                )
                assertEquals(1, backend.posts.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun failedItemCanBeExplicitlyRetriedWithoutResubmittingSuccess() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = FakeBackend(listOf(movie(1), movie(2)))
                var fail = true
                backend.submitBehavior = { request ->
                    if (request.movieId == 2 && fail) throw IllegalStateException("fixture")
                    MediaRequest(request.movieId!!, 1)
                }
                val vm = viewModel(MutableStateFlow(session(backend)))
                vm.show(7, "one")
                runCurrent()
                vm.openRequest()
                runCurrent()
                vm.submitRequests("2160p HDR")
                runCurrent()
                assertEquals(listOf(1, 2), backend.posts.map { it.movieId })
                assertEquals(
                    1,
                    vm.state.value.request
                        ?.submitted,
                )
                assertEquals(
                    1,
                    vm.state.value.request
                        ?.failed,
                )
                assertEquals(
                    0,
                    vm.state.value.request
                        ?.selectedCount,
                )
                fail = false
                vm.toggleRequest(2)
                vm.submitRequests("2160p HDR")
                runCurrent()
                assertEquals(listOf(1, 2, 2), backend.posts.map { it.movieId })
                assertEquals(8, backend.posts.last().profileId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun timeoutLeavesAmbiguousMovieUnselectableUntilStatusRefresh() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = FakeBackend(listOf(movie(1)))
                backend.submitBehavior = { throw IOException("simulated timeout") }
                val vm = viewModel(MutableStateFlow(session(backend)))
                vm.show(7, "one")
                runCurrent()
                vm.openRequest()
                runCurrent()
                vm.submitRequests("1080p HDR")
                runCurrent()
                assertEquals(
                    CollectionRequestOutcome.UNKNOWN_RESULT,
                    vm.state.value.request
                        ?.movies
                        ?.single()
                        ?.outcome,
                )
                assertEquals(
                    0,
                    vm.state.value.request
                        ?.eligibleCount,
                )
                vm.toggleRequest(1)
                vm.submitRequests("1080p HDR")
                runCurrent()
                assertEquals(1, backend.posts.size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionChangeStopsFurtherRequestsAndMarksUnattempted() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = FakeBackend(listOf(movie(1), movie(2)))
                val gate = CompletableDeferred<Unit>()
                backend.submitBehavior = { request ->
                    gate.await()
                    MediaRequest(request.movieId!!, 1)
                }
                var valid = true
                val sessions = MutableStateFlow<CollectionsSession?>(session(backend, valid = { valid }))
                val vm = viewModel(sessions)
                vm.show(7, "one")
                runCurrent()
                vm.openRequest()
                runCurrent()
                vm.submitRequests("1080p HDR")
                runCurrent()
                assertEquals(1, backend.posts.size)
                valid = false
                sessions.value = session(backend, owner = "two")
                runCurrent()
                gate.complete(Unit)
                runCurrent()
                assertEquals(1, backend.posts.size)
                assertTrue(
                    vm.state.value.request
                        ?.sessionInvalid == true,
                )
                assertEquals(
                    CollectionRequestOutcome.UNKNOWN_RESULT,
                    vm.state.value.request
                        ?.movies
                        ?.first { it.item.id == 1 }
                        ?.outcome,
                )
                assertEquals(
                    CollectionRequestOutcome.NOT_ATTEMPTED,
                    vm.state.value.request
                        ?.movies
                        ?.first { it.item.id == 2 }
                        ?.outcome,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }
}
