package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.api.seerr.MoviesApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.requestSession
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.SeerrState
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverMovieLifecycleTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = ViewModelStore()
    private val current = MutableStateFlow<CurrentSeerr?>(requestSession())
    private val connection = MutableStateFlow(SeerrState(SeerrConnectionStatus.Success(current.value!!)))
    private val service = mockk<SeerrService>(relaxed = true)
    private val client = mockk<SeerrApiClient>(relaxed = true)
    private var activeClient = client
    private val movies = mockk<MoviesApi>(relaxed = true)
    private val repository = mockk<SeerrServerRepository>(relaxed = true)
    private val movie = MovieDetails(id = 42)

    @Before fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { service.api } answers { activeClient }
        every { client.moviesApi } returns movies
        every { repository.current } returns current
        every { repository.connection } returns connection
        every { repository.currentUserId } returns current.map { it?.config?.id }
        coEvery { movies.movieMovieIdGet(any()) } answers { MovieDetails(id = firstArg()) }
        coEvery { service.createDiscoverItem(any<MovieDetails>()) } returns mockk<DiscoverItem>(relaxed = true)
    }

    @After fun tearDown() {
        stores.clear()
        dispatcher.scheduler.runCurrent()
        WholphinDispatchers.reset()
    }

    private fun model(id: Int = 42): DiscoverMovieViewModel =
        DiscoverMovieViewModel(
            mockk(relaxed = true),
            mockk<Context>(relaxed = true),
            mockk(relaxed = true),
            mockk<BackdropService>(relaxed = true),
            mockk(relaxed = true),
            service,
            mockk<SeerrAcquisitionTracker>(relaxed = true),
            mockk<EnhancedFeatureGate>(relaxed = true),
            repository,
            mockk<DiscoverItem> { every { this@mockk.id } returns id },
        ).also { stores.put("movie-$id", it) }

    @Test fun constructorAndFirstResumeIssueOnlyOneMovieAndOneMetadataBatch() =
        runTest(dispatcher) {
            val vm = model()
            vm.onResume()
            runCurrent()
            assertTrue(vm.state.value.movie is DataLoadingState.Success)
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
            coVerify(exactly = 1) { movies.movieMovieIdSimilarGet(42, 1) }
            coVerify(exactly = 1) { movies.movieMovieIdRecommendationsGet(42, 1) }
            // State recomposition has no ViewModel entry point and cannot dispatch another load.
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
        }

    @Test fun genuineLaterResumesRefreshOnlyMovieStatus() =
        runTest(dispatcher) {
            val vm = model()
            vm.onResume()
            runCurrent()
            vm.onResume()
            runCurrent()
            vm.onResume()
            runCurrent()
            coVerify(exactly = 3) { movies.movieMovieIdGet(42) }
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
            coVerify(exactly = 1) { movies.movieMovieIdSimilarGet(42, 1) }
            coVerify(exactly = 1) { movies.movieMovieIdRecommendationsGet(42, 1) }
        }

    @Test fun laterResumeSupersedesAnInFlightEntryRead() =
        runTest(dispatcher) {
            val oldFetched = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                if (++calls ==
                    1
                ) {
                    withContext(NonCancellable) {
                        oldFetched.complete(Unit)
                        releaseOld.await()
                        MovieDetails(id = 42, title = "old")
                    }
                } else {
                    MovieDetails(id = 42, title = "new")
                }
            }
            val vm = model()
            vm.onResume()
            runCurrent()
            oldFetched.await()
            vm.onResume()
            runCurrent()
            releaseOld.complete(Unit)
            runCurrent()
            coVerify(exactly = 2) { movies.movieMovieIdGet(42) }
            assertEquals("new", (vm.state.value.movie as DataLoadingState.Success).data.title)
        }

    @Test fun initialFailureHasExplicitRetryAndLaterFailureUsesErrorState() =
        runTest(dispatcher) {
            var calls = 0
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                if (++calls != 2) error("fixture failure")
                movie
            }
            val vm = model()
            vm.onResume()
            runCurrent()
            assertTrue(vm.state.value.movie is DataLoadingState.Error)
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
            vm.retry()
            runCurrent()
            assertTrue(vm.state.value.movie is DataLoadingState.Success)
            vm.onResume()
            runCurrent()
            assertTrue(vm.state.value.movie is DataLoadingState.Error)
            assertEquals(3, calls)
        }

    @Test fun sessionChangeRejectsLateOldResultAndStartsNewSessionRead() =
        runTest(dispatcher) {
            val oldFetched = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                if (++calls ==
                    1
                ) {
                    withContext(NonCancellable) {
                        oldFetched.complete(Unit)
                        releaseOld.await()
                        MovieDetails(id = 42, title = "old")
                    }
                } else {
                    MovieDetails(id = 42, title = "new")
                }
            }
            val vm = model()
            vm.onResume()
            runCurrent()
            oldFetched.await()
            current.value = requestSession(userId = 8)
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            runCurrent()
            releaseOld.complete(Unit)
            runCurrent()
            assertEquals("new", (vm.state.value.movie as DataLoadingState.Success).data.title)
            coVerify(exactly = 2) { movies.movieMovieIdGet(42) }
        }

    @Test fun forcedRetryRejectsAnOutOfOrderResponseFromTheSameSession() =
        runTest(dispatcher) {
            val oldFetched = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            var calls = 0
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                if (++calls ==
                    1
                ) {
                    withContext(NonCancellable) {
                        oldFetched.complete(Unit)
                        releaseOld.await()
                        MovieDetails(id = 42, title = "old")
                    }
                } else {
                    MovieDetails(id = 42, title = "new")
                }
            }
            val vm = model()
            vm.onResume()
            runCurrent()
            oldFetched.await()
            vm.retry()
            runCurrent()
            releaseOld.complete(Unit)
            runCurrent()
            assertEquals("new", (vm.state.value.movie as DataLoadingState.Success).data.title)
            coVerify(exactly = 2) { movies.movieMovieIdGet(42) }
        }

    @Test fun clientReplacementInvalidatesOldMovieAndMetadata() =
        runTest(dispatcher) {
            val vm = model()
            vm.onResume()
            runCurrent()
            val replacement = mockk<SeerrApiClient>(relaxed = true)
            val replacementMovies = mockk<MoviesApi>(relaxed = true)
            every { replacement.moviesApi } returns replacementMovies
            coEvery { replacementMovies.movieMovieIdGet(42) } returns MovieDetails(id = 42, title = "replacement")
            activeClient = replacement
            current.value = requestSession(userId = 8)
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            runCurrent()
            assertEquals("replacement", (vm.state.value.movie as DataLoadingState.Success).data.title)
            coVerify(exactly = 1) { replacementMovies.movieMovieIdGet(42) }
        }

    @Test fun differentMovieUsesIndependentEntryAndDisposalCancelsPendingRead() =
        runTest(dispatcher) {
            val hold = CompletableDeferred<Unit>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                hold.await()
                movie
            }
            val first = model()
            val second = model(99)
            first.onResume()
            second.onResume()
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
            coVerify(exactly = 1) { movies.movieMovieIdGet(99) }
            stores.clear()
            hold.complete(Unit)
            runCurrent()
            assertTrue(first.state.value.movie !is DataLoadingState.Success)
        }
}
