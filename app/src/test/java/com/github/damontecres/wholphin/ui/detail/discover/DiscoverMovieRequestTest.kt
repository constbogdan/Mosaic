package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.api.seerr.MoviesApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestUser
import com.github.damontecres.wholphin.api.seerr.requestSession
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import com.github.damontecres.wholphin.util.successValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverMovieRequestTest {
    private val dispatcher = StandardTestDispatcher()
    private val current = MutableStateFlow<CurrentSeerr?>(requestSession())
    private val service = mockk<SeerrService>(relaxed = true)
    private val client = mockk<SeerrApiClient>(relaxed = true)
    private val movies = mockk<MoviesApi>(relaxed = true)
    private val tracker = mockk<SeerrAcquisitionTracker>(relaxed = true)
    private val http = TvRequestHttpFixture(201, "{\"id\":91,\"status\":1,\"type\":\"movie\"}")
    private val stores = ViewModelStore()
    private var details = MovieDetails(id = 42, mediaInfo = MediaInfo(id = 84, tmdbId = 42, status = 1, status4k = 1))
    private var refreshed: CompletableDeferred<Unit>? = null
    private lateinit var model: DiscoverMovieViewModel

    @Before fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        every { service.api } returns client
        every { client.moviesApi } returns movies
        every { client.requestApi } returns http.api.requestApi
        val repository =
            mockk<SeerrServerRepository> {
                every { this@mockk.current } returns this@DiscoverMovieRequestTest.current
                every { currentUserId } returns this@DiscoverMovieRequestTest.current.map { it?.config?.id }
            }
        val wrapper = mockk<SeerrApi> { every { api } returns client }
        val realService = SeerrService(wrapper, repository, mockk())
        coEvery { service.cancelRequest(any(), any(), any(), any(), any(), any()) } coAnswers {
            realService.cancelRequest(firstArg(), secondArg(), arg(2), arg(3), arg(4), arg(5))
        }
        coEvery { movies.movieMovieIdGet(42) } coAnswers {
            refreshed?.complete(Unit)
            details
        }
        coEvery { service.createDiscoverItem(any<MovieDetails>()) } coAnswers { awaitCancellation() }
        val gate = mockk<EnhancedFeatureGate> { every { isEnabled(any()) } returns true }
        val item = mockk<DiscoverItem> { every { id } returns 42 }
        model =
            DiscoverMovieViewModel(
                mockk(relaxed = true),
                mockk<Context>(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                service,
                tracker,
                gate,
                repository,
                item,
            )
        stores.put("movie", model)
        dispatcher.scheduler.runCurrent()
    }

    @After fun tearDown() {
        stores.clear()
        unmockkStatic(Toast::class)
        WholphinDispatchers.reset()
    }

    @Test fun standardOnly() = runTest(dispatcher) { assertQualities(requestSession(32), true, false) }

    @Test fun ordinaryUhdProfileUsesStandardRadarrWithoutFourKCapability() =
        runTest(dispatcher) {
            current.value = requestSession(movie4k = false, tv4k = false)
            val profile = SeerrProfile(9, "UHD", true)
            refreshed = CompletableDeferred()
            model.request(MovieRequest(SeerrRequestData(serverId = 11, profiles = listOf(profile)), 42, false, profile.id, null))
            runCurrent()
            refreshed!!.await()
            val call = http.calls.single()
            val body = Buffer().also { call.body!!.writeTo(it) }.readUtf8()
            val submitted = Serializer.kotlinxSerializationJson.decodeFromString<RequestPostRequest>(body)
            assertEquals("POST", call.method)
            assertEquals(11, submitted.serverId)
            assertEquals(profile.id, submitted.profileId)
            assertEquals(false, submitted.is4k)
        }

    @Test fun fourKOnly() = runTest(dispatcher) { assertQualities(requestSession(1024), false, true) }

    @Test fun bothQualities() = runTest(dispatcher) { assertQualities(requestSession(), true, true) }

    @Test fun neitherQuality() = runTest(dispatcher) { assertQualities(requestSession(0), false, false) }

    @Test fun fourKPermissionDoesNotOverrideDisabledCapability() =
        runTest(dispatcher) {
            assertQualities(requestSession(1024, movie4k = false), false, false)
        }

    @Test fun capabilityDoesNotGrantPermission() = runTest(dispatcher) { assertQualities(requestSession(0, movie4k = true), false, false) }

    @Test fun standardRequestDoesNotBlockFourK() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, status = 2, status4k = 1, requests = listOf(record())))
            assertQualities(requestSession(), false, true)
        }

    @Test fun fourKRequestDoesNotBlockStandard() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, status = 1, status4k = 2, requests = listOf(record(is4k = true))))
            assertQualities(requestSession(), true, false)
        }

    @Test fun availableStandardDoesNotBecomeFourKAvailability() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, status = 5, status4k = 1))
            assertQualities(requestSession(), false, true)
        }

    @Test fun approvedAndCompletedRequestsAreNotNewRequests() =
        runTest(dispatcher) {
            for (status in listOf(2, 5)) {
                setMedia(MediaInfo(id = 84, tmdbId = 42, status = 1, status4k = 1, requests = listOf(record(status = status))))
                assertQualities(requestSession(32), false, false)
            }
        }

    @Test fun permissionLossWithPreviouslyPreparedDialogPayloadMakesNoPost() =
        runTest(dispatcher) {
            val prepared = movieRequest(false)
            assertEquals(true, model.requestEnabled.first())
            current.value = requestSession(0)
            model.request(prepared)
            runCurrent()
            assertTrue(http.calls.isEmpty())
            assertEquals(false, model.requestEnabled.first())
        }

    @Test fun profilePresenceDoesNotAuthorizeSubmission() =
        runTest(dispatcher) {
            current.value = requestSession(0)
            model.request(MovieRequest(SeerrRequestData(serverId = 10, server4kId = 20), 42, true, 3, "/fixture"))
            runCurrent()
            assertTrue(http.calls.isEmpty())
        }

    @Test fun cancellationTargetsOwnedRequestNotFirstOwnerOrMediaId() =
        runTest(dispatcher) {
            assertCancellation(listOf(record(88, owner = 8), record(91)), 91, false)
        }

    @Test fun fourKCancellationUsesExactRequestId() =
        runTest(dispatcher) {
            assertCancellation(listOf(record(88, owner = 8), record(92, is4k = true)), 92, true)
        }

    @Test fun managerCanCancelApprovedRequestWhenUnambiguous() =
        runTest(dispatcher) {
            current.value = requestSession(16)
            assertCancellation(listOf(record(status = 2, owner = 8)), 91, false)
        }

    @Test fun cancellationRefusesAmbiguousMissingAndUnauthorizedTargets() =
        runTest(dispatcher) {
            for (records in listOf(
                emptyList(),
                listOf(record(owner = 8)),
                listOf(record(status = 2)),
                listOf(record(), record(92, is4k = true)),
            )) {
                setMedia(MediaInfo(id = 84, tmdbId = 42, status = 2, status4k = 2, requests = records))
                model.cancelRequest(91, false)
                runCurrent()
            }
            assertTrue(http.calls.isEmpty())
            coVerify(exactly = 0) { service.cancelRequest(any(), any(), any(), any(), any(), any()) }
        }

    @Test fun wrongQualityOrMediaIdCannotSubstituteForRequestId() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, requests = listOf(record())))
            model.cancelRequest(42, false)
            model.cancelRequest(84, false)
            model.cancelRequest(91, true)
            runCurrent()
            assertTrue(http.calls.isEmpty())
        }

    @Test fun sessionSwitchDuringCancellationDoesNotRefreshReplacementSession() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, requests = listOf(record())))
            http.code = 204
            http.body = ""
            val responded = CompletableDeferred<Unit>()
            http.onResponse = {
                current.value = requestSession(userId = 8)
                responded.complete(Unit)
            }
            model.cancelRequest(91, false)
            responded.await()
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
            verify(exactly = 0) { tracker.refreshNow() }
        }

    @Test fun cancellationOfViewModelRejectsNonCancellableLateDeleteResponse() =
        runTest(dispatcher) {
            setMedia(MediaInfo(id = 84, tmdbId = 42, requests = listOf(record())))
            val response = CompletableDeferred<Unit>()
            coEvery { service.cancelRequest(any(), any(), any(), any(), any(), any()) } coAnswers
                { withContext(NonCancellable) { response.await() } }
            model.cancelRequest(91, false)
            runCurrent()
            stores.clear()
            response.complete(Unit)
            runCurrent()
            coVerify(exactly = 1) { movies.movieMovieIdGet(42) }
            verify(exactly = 0) { tracker.refreshNow() }
        }

    private suspend fun assertQualities(
        session: CurrentSeerr,
        standard: Boolean,
        fourK: Boolean,
    ) {
        current.value = session
        assertEquals(standard, model.requestEnabled.first())
        assertEquals(fourK, model.request4kEnabled.first())
        for ((is4k, allowed) in listOf(false to standard, true to fourK)) {
            val before = http.calls.size
            http.body = "{\"id\":91,\"status\":1,\"type\":\"movie\",\"is4k\":$is4k}"
            refreshed = CompletableDeferred()
            model.request(movieRequest(is4k))
            dispatcher.scheduler.runCurrent()
            if (allowed) refreshed!!.await()
            assertEquals(before + if (allowed) 1 else 0, http.calls.size)
        }
    }

    private suspend fun assertCancellation(
        records: List<MediaRequest>,
        id: Int,
        is4k: Boolean,
    ) {
        setMedia(MediaInfo(id = 84, tmdbId = 42, status = 2, status4k = 2, requests = records))
        http.code = 204
        http.body = ""
        http.onResponse = { details = details.copy(mediaInfo = details.mediaInfo?.copy(requests = records.filterNot { it.id == id })) }
        refreshed = CompletableDeferred()
        model.cancelRequest(id, is4k)
        refreshed!!.await()
        assertEquals(
            records
                .filterNot {
                    it.id == id
                }.map { it.id },
            model.state.value.movie.successValue
                ?.mediaInfo
                ?.requests
                .orEmpty()
                .map { it.id },
        )
        assertEquals("DELETE", http.calls.single().method)
        assertEquals(
            "/api/v1/request/$id",
            http.calls
                .single()
                .url.encodedPath,
        )
        coVerify(exactly = 2) { movies.movieMovieIdGet(42) }
        verify(exactly = 0) { tracker.refreshNow() }
    }

    private fun setMedia(media: MediaInfo) {
        details = details.copy(mediaInfo = media)
        val field = model.javaClass.getDeclaredField("_state").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val state = field.get(model) as MutableStateFlow<DiscoverMovieState>
        state.value = state.value.copy(movie = DataLoadingState.Success(details))
    }

    private fun movieRequest(is4k: Boolean) = MovieRequest(SeerrRequestData(), 42, is4k, null, null)

    private fun record(
        id: Int = 91,
        status: Int = 1,
        owner: Int = 7,
        is4k: Boolean = false,
    ) = MediaRequest(
        id = id,
        status = status,
        type = "movie",
        is4k = is4k,
        media = MediaInfo(id = 84, tmdbId = 42),
        requestedBy = RequestUser(id = owner),
    )
}
