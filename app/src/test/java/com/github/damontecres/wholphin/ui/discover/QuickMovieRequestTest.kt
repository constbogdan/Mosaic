package com.github.damontecres.wholphin.ui.discover

import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.api.seerr.MoviesApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieMovieIdRatingsGet200Response
import com.github.damontecres.wholphin.api.seerr.requestSession
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.SeerrState
import com.github.damontecres.wholphin.ui.detail.discover.MovieRequest
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickMovieRequestTest {
    private val dispatcher = StandardTestDispatcher()
    private val current = MutableStateFlow<CurrentSeerr?>(requestSession())
    private val connection = MutableStateFlow(SeerrState(SeerrConnectionStatus.Success(current.value!!)))
    private val service = mockk<SeerrService>()
    private val client = mockk<SeerrApiClient>()
    private val movies = mockk<MoviesApi>()
    private val tracker = mockk<SeerrAcquisitionTracker>(relaxed = true)
    private val http = TvRequestHttpFixture(201, "{\"id\":91,\"status\":1,\"type\":\"movie\"}")
    private val stores = ViewModelStore()
    private var movie = MovieDetails(id = 42, title = "Fixture", mediaInfo = MediaInfo(id = 84, tmdbId = 42, status = 1))
    private lateinit var model: QuickMovieRequestViewModel
    private val item = DiscoverItem(42, SeerrItemType.MOVIE, "Fixture", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)

    @Before fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { service.api } returns client
        every { client.moviesApi } returns movies
        every { client.requestApi } returns http.api.requestApi
        coEvery { movies.movieMovieIdGet(42) } answers { movie }
        coEvery { movies.movieMovieIdRatingsGet(42) } returns MovieMovieIdRatingsGet200Response(criticsScore = 86, audienceScore = 75)
        coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } returns SeerrRequestData()
        val repository =
            mockk<SeerrServerRepository> {
                every { this@mockk.current } returns this@QuickMovieRequestTest.current
                every { connection } returns this@QuickMovieRequestTest.connection
            }
        val gate = mockk<EnhancedFeatureGate> { every { isEnabled(any()) } returns false }
        model = QuickMovieRequestViewModel(service, repository, tracker, gate)
        stores.put("quick", model)
        dispatcher.scheduler.runCurrent()
    }

    @After fun tearDown() {
        stores.clear()
        WholphinDispatchers.reset()
    }

    @Test fun eligibleMovieLoadsWithoutSubmittingAndRepeatedOpenDoesNotReplaceIt() =
        runTest(dispatcher) {
            model.open(item)
            runCurrent()
            assertEquals(
                42,
                model.state.value
                    ?.movie
                    ?.id,
            )
            assertFalse(model.state.value!!.loading)
            assertTrue(http.calls.isEmpty())
            model.open(item.copy(id = 43))
            runCurrent()
            assertEquals(
                42,
                model.state.value
                    ?.item
                    ?.id,
            )
        }

    @Test fun ratingStartsBeforeMetadataEligibilityAndProfilesWithoutBlockingDialog() =
        runTest(dispatcher) {
            val metadata = CompletableDeferred<MovieDetails>()
            val profiles = CompletableDeferred<SeerrRequestData>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers { metadata.await() }
            coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } coAnswers { profiles.await() }
            model.open(item)
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
            assertEquals(
                86,
                model.state.value
                    ?.rating
                    ?.criticRating,
            )
            assertEquals(
                75,
                model.state.value
                    ?.rating
                    ?.audienceScore,
            )
            assertEquals(
                7.5f,
                model.state.value
                    ?.rating
                    ?.audienceRating,
            )
            assertTrue(model.state.value!!.loading)
            assertNull(model.state.value?.movie)
            assertFalse(model.state.value!!.presentable)

            metadata.complete(movie)
            runCurrent()
            assertTrue(model.state.value!!.loading)
            assertTrue(model.state.value!!.presentable)
            profiles.complete(SeerrRequestData())
            runCurrent()
            assertFalse(model.state.value!!.loading)
            assertEquals(
                86,
                model.state.value
                    ?.rating
                    ?.criticRating,
            )
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
        }

    @Test fun movieRatingPreservesAudiencePercentageIncludingZeroAndMissingFields() {
        val both = DiscoverRating(MovieMovieIdRatingsGet200Response(criticsScore = 86, audienceScore = 75))
        assertEquals(86, both.criticRating)
        assertEquals(75, both.audienceScore)
        assertEquals(7.5f, both.audienceRating)

        val zero = DiscoverRating(MovieMovieIdRatingsGet200Response(audienceScore = 0))
        assertNull(zero.criticRating)
        assertEquals(0, zero.audienceScore)
        assertEquals(0f, zero.audienceRating)

        val absent = DiscoverRating(MovieMovieIdRatingsGet200Response(criticsScore = 42))
        assertEquals(42, absent.criticRating)
        assertNull(absent.audienceScore)
        assertNull(absent.audienceRating)
    }

    @Test fun ratingFailureDoesNotBlockEligibilityProfilesOrRetry() =
        runTest(dispatcher) {
            coEvery { movies.movieMovieIdRatingsGet(42) } throws IllegalStateException("fixture rating failure")
            coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } throws IllegalStateException("fixture profiles failure")
            model.open(item)
            runCurrent()
            assertTrue(model.state.value!!.error)
            assertNull(model.state.value?.rating)
            coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } returns SeerrRequestData()
            model.retry()
            runCurrent()
            assertFalse(model.state.value!!.error)
            assertFalse(model.state.value!!.loading)
            assertNull(model.state.value?.rating)
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
            assertTrue(http.calls.isEmpty())
        }

    @Test fun dismissalCancelsPendingRatingWithoutPublishingIt() =
        runTest(dispatcher) {
            val response = CompletableDeferred<MovieMovieIdRatingsGet200Response>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { movies.movieMovieIdRatingsGet(42) } coAnswers {
                try {
                    response.await()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            model.open(item)
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdRatingsGet(42) }
            model.dismiss()
            runCurrent()
            assertTrue(cancelled.isCompleted)
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun sessionChangeRejectsRatingAtFinalPublication() =
        runTest(dispatcher) {
            model.open(item)
            runCurrent()
            val opening = model.state.value!!
            val nextSession = requestSession(userId = 8)
            connection.value = SeerrState(SeerrConnectionStatus.Success(nextSession))
            model.publishRating(opening.token, opening.session, DiscoverRating(42, null))
            assertEquals(
                86,
                model.state.value
                    ?.rating
                    ?.criticRating,
            )
            current.value = nextSession
            runCurrent()
            assertNull(model.state.value)
        }

    @Test fun rapidSameMovieReopenRejectsOldRatingAtFinalPublication() =
        runTest(dispatcher) {
            model.open(item)
            runCurrent()
            val old = model.state.value!!
            model.dismiss()
            model.open(item)
            runCurrent()
            val newer = model.state.value!!
            assertTrue(newer.token != old.token)
            model.publishRating(old.token, old.session, DiscoverRating(42, null))
            assertEquals(
                86,
                model.state.value
                    ?.rating
                    ?.criticRating,
            )
            assertEquals(newer.token, model.state.value?.token)
            coVerify(exactly = 2) { movies.movieMovieIdRatingsGet(42) }
        }

    @Test fun staleMetadataProfilesAndErrorsCannotReplaceNewOpening() =
        runTest(dispatcher) {
            var metadataCalls = 0
            val secondMovie = CompletableDeferred<MovieDetails>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers {
                if (++metadataCalls == 1) movie else secondMovie.await()
            }
            model.open(item)
            runCurrent()
            val old = model.state.value!!
            model.dismiss()
            model.open(item)
            runCurrent()
            val newer = model.state.value!!
            assertTrue(newer.token != old.token)
            assertTrue(newer.loading)
            assertNull(newer.movie)

            // Simulate an old worker passing its outer check just before dismissal/reopening.
            model.updateIfCurrent(old.token, old.session) { it.copy(movie = movie, presentable = true) }
            model.updateIfCurrent(old.token, old.session) { it.copy(data = SeerrRequestData(), loading = false) }
            model.updateIfCurrent(old.token, old.session) { it.copy(error = true, loading = false) }
            assertEquals(newer, model.state.value)
            assertTrue(http.calls.isEmpty())

            secondMovie.complete(movie)
            runCurrent()
            assertEquals(newer.token, model.state.value?.token)
            assertFalse(model.state.value!!.loading)
        }

    @Test fun posterNavigationClaimsOneCurrentOpeningAndDoesNotSubmit() =
        runTest(dispatcher) {
            var navigations = 0
            model.open(item, onOpenDetails = { navigations++ })
            runCurrent()
            val opening = model.state.value!!
            val navigate = model.takePosterNavigation(opening)
            assertNull(model.state.value)
            navigate?.invoke()
            assertEquals(1, navigations)
            assertNull(model.takePosterNavigation(opening))
            assertTrue(http.calls.isEmpty())

            model.open(item, onOpenDetails = { navigations++ })
            runCurrent()
            assertNull(model.takePosterNavigation(opening))
            assertEquals(1, navigations)
            assertTrue(model.state.value != null)
        }

    @Test fun posterNavigationIsInvalidatedBySessionChangeAndDismissal() =
        runTest(dispatcher) {
            var navigations = 0
            model.open(item, onOpenDetails = { navigations++ })
            runCurrent()
            val opening = model.state.value!!
            connection.value = SeerrState(SeerrConnectionStatus.Success(requestSession(userId = 8)))
            assertNull(model.takePosterNavigation(opening))
            assertEquals(0, navigations)
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun cardReleaseCompletesTheOriginatingGestureWithoutArmingRequestConfirmation() {
        val gate = QuickMovieConfirmGate()
        gate.reset()
        assertTrue(gate.onCardKey(0, 23, 1))
        assertFalse(gate.onCardKey(1, 23))
        assertTrue(gate.released)
        assertFalse(gate.consumeConfirmation())
        assertFalse(gate.onKey(0, 23))
        assertFalse(gate.onKey(1, 23))
        assertTrue(gate.consumeConfirmation())

        gate.reset()
        gate.abortOpening()
        assertFalse(gate.onCardKey(1, 23))
        assertFalse(gate.onCardKey(0, 23))
        assertFalse(gate.consumeConfirmation())
    }

    @Test fun pendingProcessingAvailableAndBlocklistedMoviesRetainSecondaryAction() =
        runTest(dispatcher) {
            for (status in listOf(2, 3, 5, 6)) {
                movie = movie.copy(mediaInfo = movie.mediaInfo?.copy(status = status))
                var fallback = 0
                model.open(item, onUnavailable = { fallback++ })
                runCurrent()
                assertNull(model.state.value)
                assertEquals(1, fallback)
            }
            assertTrue(http.calls.isEmpty())
        }

    @Test fun ineligibleMovieNeverPresentsDialogAndOriginalOkReleaseCannotSubmit() =
        runTest(dispatcher) {
            val metadata = CompletableDeferred<MovieDetails>()
            val input = QuickMovieConfirmGate()
            movie = movie.copy(mediaInfo = movie.mediaInfo?.copy(status = SeerrAvailability.AVAILABLE.status))
            coEvery { movies.movieMovieIdGet(42) } coAnswers { metadata.await() }
            var fallback = 0

            model.open(item, onUnavailable = { fallback++ }, onStart = input::reset, onAbort = input::abortOpening)
            runCurrent()
            assertFalse(model.state.value!!.presentable)
            metadata.complete(movie)
            runCurrent()
            assertNull(model.state.value)
            assertEquals(1, fallback)
            assertFalse(input.onCardKey(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
            assertFalse(input.onCardKey(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
            assertFalse(input.onCardKey(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
            assertFalse(input.consumeConfirmation())
            assertTrue(http.calls.isEmpty())
        }

    @Test fun deniedMoviePermissionNeverPresentsDialog() =
        runTest(dispatcher) {
            current.value = requestSession(permissions = 0)
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            runCurrent()
            val metadata = CompletableDeferred<MovieDetails>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers { metadata.await() }

            model.open(item)
            runCurrent()
            assertFalse(model.state.value!!.presentable)
            metadata.complete(movie)
            runCurrent()
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun standardEligibilityDoesNotOfferFourKOnlyMovie() =
        runTest(dispatcher) {
            movie = movie.copy(mediaInfo = movie.mediaInfo?.copy(status = 5, status4k = 1))
            model.open(item)
            runCurrent()
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun explicitSubmissionIsSingleAndFourKPayloadIsRejected() =
        runTest(dispatcher) {
            model.open(item)
            runCurrent()
            val request = MovieRequest(SeerrRequestData(), 42, false, null, null)
            model.submit(request.copy(is4k = true))
            model.submit(request.copy(movieId = 43))
            assertTrue(http.calls.isEmpty())
            val responded = CompletableDeferred<Unit>()
            http.onResponse = { responded.complete(Unit) }
            model.submit(request)
            model.submit(request)
            runCurrent()
            responded.await()
            model.state.first { it == null }
            assertEquals(1, http.calls.size)
            assertNull(model.state.value)
        }

    @Test fun eligibilityChangeBeforeSubmitPreventsPost() =
        runTest(dispatcher) {
            model.open(item)
            runCurrent()
            movie = movie.copy(mediaInfo = movie.mediaInfo?.copy(status = 5))
            model.submit(MovieRequest(SeerrRequestData(), 42, false, null, null))
            runCurrent()
            assertTrue(http.calls.isEmpty())
            assertNull(model.state.value)
        }

    @Test fun profileFailureIsVisibleAndRetryLoadsAgainWithoutPost() =
        runTest(dispatcher) {
            coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } throws IllegalStateException("fixture")
            model.open(item)
            runCurrent()
            assertTrue(model.state.value!!.error)
            coEvery { service.getProfilesAndFolders(SeerrItemType.MOVIE, false) } returns SeerrRequestData()
            model.retry()
            model.retry()
            runCurrent()
            assertEquals(
                42,
                model.state.value
                    ?.movie
                    ?.id,
            )
            assertFalse(model.state.value!!.error)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun metadataFailureAndRetryRemainPresentableWithoutSubmission() =
        runTest(dispatcher) {
            coEvery { movies.movieMovieIdGet(42) } throws IllegalStateException("fixture")
            model.open(item)
            runCurrent()
            assertTrue(model.state.value!!.error)
            assertTrue(model.state.value!!.presentable)
            val metadata = CompletableDeferred<MovieDetails>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers { metadata.await() }
            model.retry()
            runCurrent()
            assertTrue(model.state.value!!.loading)
            assertTrue(model.state.value!!.presentable)
            metadata.complete(movie)
            runCurrent()
            assertTrue(model.state.value!!.presentable)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun sessionSwitchAndDismissDiscardLateMovieResponse() =
        runTest(dispatcher) {
            val response = CompletableDeferred<MovieDetails>()
            coEvery { movies.movieMovieIdGet(42) } coAnswers { response.await() }
            model.open(item)
            runCurrent()
            current.value = requestSession(userId = 8)
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            runCurrent()
            response.complete(movie)
            runCurrent()
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
            current.value = requestSession()
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            model.open(item)
            runCurrent()
            model.dismiss()
            runCurrent()
            assertNull(model.state.value)
        }

    @Test fun tvAndInvalidMovieIdsCannotOpen() =
        runTest(dispatcher) {
            model.open(item.copy(type = SeerrItemType.TV))
            model.open(item.copy(id = 0))
            runCurrent()
            assertNull(model.state.value)
        }

    @Test fun disconnectedSeerrRetainsTheExistingSecondaryAction() =
        runTest(dispatcher) {
            current.value = null
            connection.value = SeerrState()
            runCurrent()
            var fallback = 0
            model.open(item, onUnavailable = { fallback++ })
            runCurrent()
            assertEquals(1, fallback)
            assertNull(model.state.value)
            assertTrue(http.calls.isEmpty())
        }

    @Test fun sessionChangeBeforeDialogPublicationReleasesTheOriginalPressAndRestoresOk() =
        runTest(dispatcher) {
            val gate = QuickMovieConfirmGate()
            model.open(item, onStart = gate::reset, onAbort = gate::abortOpening)
            current.value = requestSession(userId = 8)
            runCurrent()
            assertNull(model.state.value)
            assertTrue(gate.onKey(0, 23, 2))
            assertTrue(gate.onKey(1, 23))
            assertFalse(gate.onKey(0, 23))
            assertFalse(gate.onKey(1, 23))
            assertFalse(gate.consumeConfirmation())
            current.value = requestSession()
            connection.value = SeerrState(SeerrConnectionStatus.Success(current.value!!))
            model.open(item, onStart = gate::reset, onAbort = gate::abortOpening)
            runCurrent()
            assertEquals(
                42,
                model.state.value
                    ?.movie
                    ?.id,
            )
            assertFalse(gate.consumeConfirmation())
        }

    @Test fun unavailableClientAndCancelledOpeningDoNotTrapSubsequentOk() =
        runTest(dispatcher) {
            val gate = QuickMovieConfirmGate()
            every { service.api } throws IllegalStateException("fixture unavailable")
            model.open(item, onStart = gate::reset, onAbort = gate::abortOpening)
            runCurrent()
            assertNull(model.state.value)
            assertTrue(gate.onKey(1, 23))
            assertFalse(gate.onKey(0, 23))
            every { service.api } returns client
            model.open(item, onStart = gate::reset, onAbort = gate::abortOpening)
            model.dismiss() // Navigation away before the dispatched opening publishes state.
            runCurrent()
            assertNull(model.state.value)
            assertTrue(gate.onKey(1, 23))
            assertFalse(gate.onKey(0, 23))
            assertTrue(http.calls.isEmpty())
        }

    @Test fun abortedOpeningClearsOnBackWithoutChangingSuccessfulHeldOkProtection() {
        val gate = QuickMovieConfirmGate()
        gate.reset()
        gate.abortOpening()
        gate.abortOpening() // A concurrent session observer may also dismiss the same opening.
        assertTrue(gate.onKey(1, 23))
        gate.reset()
        gate.abortOpening()
        assertFalse(gate.onKey(0, 4))
        assertFalse(gate.onKey(0, 23))
        gate.reset()
        assertTrue(gate.onKey(1, 23))
        assertFalse(gate.consumeConfirmation())
        assertFalse(gate.onKey(0, 23))
        assertFalse(gate.onKey(1, 23))
        assertTrue(gate.consumeConfirmation())
    }

    @Test fun metadataFailureThenDismissalRestoresCardInputWithoutSubmitting() =
        runTest(dispatcher) {
            val gate = QuickMovieConfirmGate()
            coEvery { movies.movieMovieIdGet(42) } throws IllegalStateException("fixture metadata failure")
            model.open(item, onStart = gate::reset, onAbort = gate::abortOpening)
            runCurrent()
            assertTrue(model.state.value?.error == true)
            model.dismiss()
            assertTrue(gate.onKey(1, 23))
            assertFalse(gate.onKey(0, 23))
            assertFalse(gate.onKey(1, 23))
            assertTrue(http.calls.isEmpty())
        }

    @Test fun heldOkAndRepeatsCannotConfirmUntilACompleteSeparatePress() {
        val gate = QuickMovieConfirmGate()
        assertFalse(gate.released)
        assertFalse(gate.onKey(0, 23)) // Ordinary short-clicks work before opening.
        gate.reset()
        assertFalse(gate.consumeConfirmation())
        assertTrue(gate.onKey(0, 23))
        assertTrue(gate.onKey(0, 23, 1))
        assertTrue(gate.onKey(0, 23, 7))
        assertFalse(gate.onKey(1, 4)) // Back still dismisses.
        assertFalse(gate.consumeConfirmation())
        assertTrue(gate.onKey(1, 23)) // The initiating release is consumed.
        assertTrue(gate.released)
        assertFalse(gate.consumeConfirmation())
        assertTrue(gate.onKey(1, 23)) // Stray releases cannot confirm.
        assertTrue(gate.onKey(0, 23, 1)) // A repeated down is not a new press.
        assertFalse(gate.consumeConfirmation())
        assertFalse(gate.onKey(0, 23))
        assertTrue(gate.onKey(0, 23, 1)) // Repeats of the deliberate press are ignored.
        assertFalse(gate.consumeConfirmation())
        assertFalse(gate.onKey(1, 23))
        assertTrue(gate.consumeConfirmation())
        assertFalse(gate.consumeConfirmation()) // At most one submit per completed press.
        gate.reset()
        assertFalse(gate.released)
        gate.clear()
        assertFalse(gate.onKey(0, 23))
    }

    @Test fun detailsClickRequiresFreshCompletePressBeforeConfirmation() {
        val gate = QuickMovieConfirmGate()
        gate.resetAfterClick()
        assertTrue(gate.released)
        assertFalse(gate.consumeConfirmation())
        assertTrue(gate.onKey(1, 23)) // A late release from the opening click is not a confirmation.
        assertTrue(gate.onKey(0, 23, 1)) // Repeated held input is not a fresh press.
        assertFalse(gate.consumeConfirmation())
        assertFalse(gate.onKey(0, 23))
        assertFalse(gate.onKey(1, 23))
        assertTrue(gate.consumeConfirmation())
        assertFalse(gate.consumeConfirmation())
    }
}
