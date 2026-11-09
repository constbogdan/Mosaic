package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.TvApi
import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.TvRequestOutcome
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.MediaProductStateCoordinator
import com.github.damontecres.wholphin.services.SeasonIntegrityService
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.detail.discover.RequestSeason
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.TvRequest
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesRequestStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val active = MutableStateFlow(true)
    private val seerr = mockk<SeerrService>(relaxed = true)
    private val client = mockk<SeerrApiClient>(relaxed = true)
    private val tvApi = mockk<TvApi>(relaxed = true)
    private val tracker = mockk<SeerrAcquisitionTracker>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val integrity = mockk<SeasonIntegrityService>(relaxed = true)
    private val stores = ViewModelStore()
    private val seriesId = UUID.randomUUID()
    private val series = BaseItem(BaseItemDto(id = seriesId, type = BaseItemKind.SERIES))
    private val localSeason = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, indexNumber = 1))
    private lateinit var viewModel: SeriesViewModel

    @Before
    fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { seerr.api } returns client
        every { client.tvApi } returns tvApi
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        every { context.getString(any()) } returns "No additional seasons are available to request."
        every { seerr.active } returns active
        val api = mockk<ApiClient>(relaxed = true)
        every { api.userLibraryApi } returns
            mockk {
                // Suspend unrelated initial page loading while exercising real request/refresh methods.
                coEvery { getItem(seriesId) } coAnswers { awaitCancellation() }
            }
        val repository = mockk<ServerRepository>(relaxed = true)
        every { repository.currentUser } returns null
        val seerrRepository = mockk<SeerrServerRepository>(relaxed = true)
        every { seerrRepository.current } returns flowOf(null)
        every { seerrRepository.currentUserId } returns flowOf(null)
        val gate = mockk<EnhancedFeatureGate>()
        every { gate.isEnabled(any()) } returns true
        val productState = mockk<MediaProductStateCoordinator>(relaxed = true)
        every { productState.observe(any(), any()) } returns flowOf(emptyMap())
        coEvery { integrity.cachedSeasonNumbers(seriesId) } returns setOf(1)
        coEvery { integrity.evaluate(any(), any(), any()) } returns emptyList()
        viewModel =
            SeriesViewModel(
                api = api,
                context = context,
                serverRepository = repository,
                navigationManager = mockk(relaxed = true),
                itemPlaybackRepository = mockk(relaxed = true),
                themeSongPlayer = mockk(relaxed = true),
                favoriteWatchManager = mockk(relaxed = true),
                peopleFavorites = mockk(relaxed = true),
                trailerService = mockk(relaxed = true),
                extrasService = mockk(relaxed = true),
                streamChoiceService = mockk(relaxed = true),
                serverReportService = mockk(relaxed = true),
                userPreferencesService = mockk(relaxed = true),
                backdropService = mockk(relaxed = true),
                seerrService = seerr,
                seasonIntegrityService = integrity,
                mediaProductStateCoordinator = productState,
                seerrAcquisitionTracker = tracker,
                enhancedFeatureGate = gate,
                seerrServerRepository = seerrRepository,
                mediaManagementService = mockk(relaxed = true),
                seriesId = seriesId,
                seasonEpisodeIds = null,
                seriesPageType = SeriesPageType.DETAILS,
            )
        stores.put("series", viewModel)
        setState(
            SeriesState(
                seasons = listOf(localSeason),
                seerrTvDetails = TvDetails(id = 42),
                requestData = SeerrRequestData(serverId = 12),
                profileLoading = LoadingState.Success,
                requestSeasons = listOf(requestSeason()),
                requestSeasons4k = listOf(requestSeason()),
            ),
        )
    }

    @After
    fun tearDown() {
        stores.clear()
        unmockkStatic(Toast::class)
        WholphinDispatchers.reset()
    }

    @Test
    fun onlyRequestableNonlocalCardsActivateRequests() {
        val missing = SeriesDetailsSeason(2, null, requestSeason(), null)
        assertTrue(missing.canRequest())
        assertFalse(missing.copy(jellyfinItem = localSeason).canRequest())
        assertFalse(missing.copy(seerrSeason = requestSeason(RequestStatus.APPROVED)).canRequest())
        assertFalse(missing.copy(seerrSeason = requestSeason(RequestStatus.PENDING)).canRequest())
        assertFalse(missing.copy(seerrSeason = null).canRequest())
    }

    @Test
    fun inactiveSeerrInvalidatesOpenDialogAndRetainsIntegrityEvaluation() =
        runTest(dispatcher) {
            assertEquals(42, viewModel.state.value.requestMediaId(true))
            active.value = false
            assertNull(viewModel.state.value.requestMediaId(active.value))
            viewModel.refreshSeerrDetails(series, false)
            assertFallback()
            coVerify(exactly = 0) { seerr.getTvSeries(any()) }
            coVerify { integrity.evaluate(seriesId, null, emptyList()) }
        }

    @Test
    fun disappearingMetadataClearsRequestStateWithoutDroppingJellyfinSeasons() =
        runTest(dispatcher) {
            coEvery { seerr.getTvSeries(series) } returns null
            viewModel.refreshSeerrDetails(series, true)
            assertFallback()
            coVerify { integrity.evaluate(seriesId, null, emptyList()) }
        }

    @Test
    fun genuineMetadataFailureStillUsesNormalFallback() =
        runTest(dispatcher) {
            coEvery { seerr.getTvSeries(series) } throws IllegalStateException("unavailable")
            viewModel.refreshSeerrDetails(series, true)
            assertFallback()
        }

    @Test
    fun cancellationPropagatesWithoutPublishingAnUnavailableProjection() =
        runTest(dispatcher) {
            val cancelled = CancellationException("replaced refresh")
            coEvery { seerr.getTvSeries(series) } throws cancelled
            val before = viewModel.state.value
            try {
                viewModel.refreshSeerrDetails(series, true)
                throw AssertionError("Cancellation was swallowed")
            } catch (error: CancellationException) {
                assertSame(cancelled, error)
            }
            assertSame(before, viewModel.state.value)
            coVerify(exactly = 0) { integrity.evaluate(any(), any(), any()) }
        }

    @Test
    fun lateProfileResponseCannotRestoreStateAfterMetadataDisappears() =
        runTest(dispatcher) {
            val response = CompletableDeferred<SeerrRequestData>()
            coEvery { seerr.getProfilesAndFolders(any()) } coAnswers { withContext(NonCancellable) { response.await() } }
            viewModel.requestOnClick()
            runCurrent()
            assertEquals(LoadingState.Loading, viewModel.state.value.profileLoading)
            coEvery { seerr.getTvSeries(series) } returns null
            viewModel.refreshSeerrDetails(series, true)
            response.complete(SeerrRequestData(serverId = 99))
            runCurrent()
            assertFallback()
        }

    @Test
    fun metadataResponseCannotReactivateRequestsAfterSeerrBecomesInactive() =
        runTest(dispatcher) {
            coEvery { seerr.getTvSeries(series) } coAnswers {
                active.value = false
                TvDetails(id = 42)
            }
            viewModel.refreshSeerrDetails(series, true)
            assertFallback()
            coVerify { integrity.evaluate(seriesId, null, emptyList()) }
        }

    @Test
    fun lateProfileFailureCannotRestoreErrorAfterMetadataDisappears() =
        runTest(dispatcher) {
            val response = CompletableDeferred<SeerrRequestData>()
            coEvery { seerr.getProfilesAndFolders(any()) } coAnswers { withContext(NonCancellable) { response.await() } }
            viewModel.requestOnClick()
            runCurrent()
            coEvery { seerr.getTvSeries(series) } returns null
            viewModel.refreshSeerrDetails(series, true)
            response.completeExceptionally(IllegalStateException("late failure"))
            runCurrent()
            assertFallback()
        }

    @Test
    fun activeValidMetadataStillLoadsProfilesAndAllowsSubmission() =
        runTest(dispatcher) {
            val data = SeerrRequestData(serverId = 99)
            coEvery { seerr.getProfilesAndFolders(any()) } returns data
            coEvery { seerr.requestTv(any(), any()) } coAnswers { awaitCancellation() }
            viewModel.requestOnClick()
            runCurrent()
            assertEquals(LoadingState.Success, viewModel.state.value.profileLoading)
            assertEquals(data, viewModel.state.value.requestData)
            viewModel.request(TvRequest(data, 42, listOf(2), false, null, null))
            runCurrent()
            coVerify(exactly = 1) { seerr.requestTv(any(), any()) }
        }

    @Test
    fun post202RefreshesEligibilityWithoutQueueingOrResubmission() =
        runTest(dispatcher) {
            assertSubmission(edit = false, code = 202)
        }

    @Test
    fun put202RefreshesEligibilityWithoutQueueingOrResubmission() =
        runTest(dispatcher) {
            assertSubmission(edit = true, code = 202)
        }

    @Test
    fun post201PreservesQueueingAndRefresh() =
        runTest(dispatcher) {
            assertSubmission(edit = false, code = 201)
        }

    @Test
    fun put200PreservesQueueingAndRefresh() =
        runTest(dispatcher) {
            assertSubmission(edit = true, code = 200)
        }

    private suspend fun assertSubmission(
        edit: Boolean,
        code: Int,
    ) {
        val fixture =
            TvRequestHttpFixture(
                code,
                if (code ==
                    202
                ) {
                    "{\"message\":\"No seasons available to request\"}"
                } else {
                    "{\"id\":91,\"status\":1,\"type\":\"tv\"}"
                },
            )
        val refreshed = CompletableDeferred<Unit>()
        coEvery { seerr.requestTv(any(), any()) } coAnswers { fixture.submit(edit) }
        coEvery { tvApi.tvTvIdGet(42) } coAnswers {
            refreshed.complete(Unit)
            TvDetails(id = 42)
        }
        viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
        refreshed.await()
        coVerify(exactly = 1) { seerr.requestTv(any(), any()) }
        coVerify(exactly = 1) { tvApi.tvTvIdGet(42) }
        verify(exactly = 1) { tracker.refreshNow() }
        verify(exactly = if (code == 202) 0 else 1) { tracker.registerQueueing(any()) }
        if (code == 202) verify(exactly = 1) { Toast.makeText(context, "No additional seasons are available to request.", any()) }
        assertEquals(1, fixture.calls.size)
    }

    @Test
    fun cancellationDuringSubmissionDoesNotPublishOrRefresh() =
        runTest(dispatcher) {
            coEvery { seerr.requestTv(any(), any()) } throws CancellationException("cancelled")
            viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
            runCurrent()
            verify(exactly = 0) { tracker.registerQueueing(any()) }
            verify(exactly = 0) { tracker.refreshNow() }
            coVerify(exactly = 0) { tvApi.tvTvIdGet(any()) }
            verify(exactly = 0) { Toast.makeText(any(), any<CharSequence>(), any()) }
        }

    @Test
    fun inactiveSeerrDiscardsLateSubmissionOutcome() =
        runTest(dispatcher) {
            val response = CompletableDeferred<TvRequestOutcome>()
            coEvery { seerr.requestTv(any(), any()) } coAnswers { response.await() }
            viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
            runCurrent()
            active.value = false
            response.complete(TvRequestOutcome.NoNewRequest)
            runCurrent()
            verify(exactly = 0) { tracker.registerQueueing(any()) }
            verify(exactly = 0) { tracker.refreshNow() }
            coVerify(exactly = 0) { tvApi.tvTvIdGet(any()) }
        }

    @Test
    fun malformedSuccessRemainsARequestFailure() =
        runTest(dispatcher) {
            assertSubmissionFailure(201, "{}")
        }

    @Test
    fun httpErrorRemainsARequestFailure() =
        runTest(dispatcher) {
            assertSubmissionFailure(403, "{\"message\":\"forbidden\"}")
        }

    private suspend fun assertSubmissionFailure(
        code: Int,
        body: String,
    ) {
        val fixture = TvRequestHttpFixture(code, body)
        val completed = CompletableDeferred<Unit>()
        coEvery { seerr.requestTv(any(), any()) } coAnswers {
            try {
                fixture.submit()
            } finally {
                completed.complete(Unit)
            }
        }
        viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
        completed.await()
        dispatcher.scheduler.runCurrent()
        verify(exactly = 0) { tracker.refreshNow() }
        verify(exactly = 0) { tracker.registerQueueing(any()) }
        verify(exactly = 1) { Toast.makeText(context, "An error occurred", any()) }
        coVerify(exactly = 0) { tvApi.tvTvIdGet(any()) }
        assertEquals(1, fixture.calls.size)
    }

    @Test
    fun cancelledViewModelDiscardsNonCancellableLateResponse() =
        runTest(dispatcher) {
            val response = CompletableDeferred<TvRequestOutcome>()
            coEvery { seerr.requestTv(any(), any()) } coAnswers { withContext(NonCancellable) { response.await() } }
            viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
            runCurrent()
            stores.clear()
            response.complete(TvRequestOutcome.NoNewRequest)
            runCurrent()
            verify(exactly = 0) { tracker.registerQueueing(any()) }
            verify(exactly = 0) { tracker.refreshNow() }
            coVerify(exactly = 0) { tvApi.tvTvIdGet(any()) }
            verify(exactly = 0) { Toast.makeText(any(), any<CharSequence>(), any()) }
        }

    @Test
    fun inactiveSeerrCannotLoadProfilesOrSubmitRequests() =
        runTest(dispatcher) {
            active.value = false
            viewModel.requestOnClick()
            viewModel.request(TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null))
            runCurrent()
            coVerify(exactly = 0) { seerr.getProfilesAndFolders(any()) }
            coVerify(exactly = 0) { seerr.requestTv(any(), any()) }
            assertEquals(LoadingState.Pending, viewModel.state.value.profileLoading)
        }

    @Test
    fun invalidMediaIdentityCannotOpenDialogOrSubmit() =
        runTest(dispatcher) {
            setState(viewModel.state.value.copy(seerrTvDetails = TvDetails(id = -1)))
            assertNull(viewModel.state.value.requestMediaId(true))
            viewModel.requestOnClick()
            viewModel.request(TvRequest(SeerrRequestData(), -1, listOf(2), false, null, null))
            runCurrent()
            coVerify(exactly = 0) { seerr.getProfilesAndFolders(any()) }
            coVerify(exactly = 0) { seerr.requestTv(any(), any()) }
        }

    private fun assertFallback() {
        val state = viewModel.state.value
        assertNull(state.requestMediaId(true))
        assertEquals(listOf(localSeason), state.seasons)
        assertEquals(localSeason, state.detailsSeasons.single().jellyfinItem)
        assertFalse(state.detailsSeasons.single().canRequest())
        assertTrue(state.requestSeasons.isEmpty())
        assertTrue(state.requestSeasons4k.isEmpty())
        assertEquals(SeerrRequestData(), state.requestData)
        assertEquals(LoadingState.Pending, state.profileLoading)
    }

    private fun setState(state: SeriesState) {
        (viewModel.state as MutableStateFlow<SeriesState>).value = state
    }

    private fun requestSeason(status: RequestStatus = RequestStatus.UNKNOWN) =
        RequestSeason(Season(seasonNumber = 2), status, SeerrAvailability.UNKNOWN, true)
}
