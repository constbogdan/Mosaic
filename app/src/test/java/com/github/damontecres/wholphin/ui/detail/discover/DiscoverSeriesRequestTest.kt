package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.TvApi
import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.TvRequestOutcome
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverSeriesRequestTest {
    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<SeerrService>(relaxed = true)
    private val client = mockk<SeerrApiClient>(relaxed = true)
    private val tvApi = mockk<TvApi>(relaxed = true)
    private val tracker = mockk<SeerrAcquisitionTracker>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val owner = MutableStateFlow<Int?>(7)
    private val stores = ViewModelStore()
    private lateinit var model: DiscoverSeriesViewModel

    @Before
    fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { service.api } returns client
        every { client.tvApi } returns tvApi
        mockkStatic(Toast::class)
        every { Toast.makeText(any(), any<CharSequence>(), any()) } returns mockk(relaxed = true)
        every { context.getString(any()) } returns "No additional seasons are available to request."
        val repository = mockk<SeerrServerRepository>(relaxed = true)
        every { repository.current } returns flowOf(null)
        every { repository.currentUserId } returns owner
        val gate = mockk<EnhancedFeatureGate> { every { isEnabled(any()) } returns true }
        val item = mockk<DiscoverItem> { every { id } returns 42 }
        coEvery { tvApi.tvTvIdGet(42) } returns TvDetails(id = 42)
        // Pause unrelated metadata enrichment after init publishes the details snapshot.
        coEvery { service.createDiscoverItem(any<TvDetails>()) } coAnswers { awaitCancellation() }
        model =
            DiscoverSeriesViewModel(
                mockk(relaxed = true),
                context,
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                service,
                tracker,
                gate,
                repository,
                item,
            )
        stores.put("discover", model)
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        stores.clear()
        unmockkStatic(Toast::class)
        WholphinDispatchers.reset()
    }

    @Test
    fun post202RefreshesWithoutQueueingOrResubmitting() = runTest(dispatcher) { assertSubmission(false, 202) }

    @Test
    fun put202RefreshesWithoutQueueingOrResubmitting() = runTest(dispatcher) { assertSubmission(true, 202) }

    @Test
    fun post201KeepsReturnedIdentityQueueingAndRefresh() = runTest(dispatcher) { assertSubmission(false, 201) }

    @Test
    fun put200KeepsReturnedIdentityQueueingAndRefresh() = runTest(dispatcher) { assertSubmission(true, 200) }

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
        coEvery { service.requestTv(any(), any()) } coAnswers { fixture.submit(edit) }
        coEvery { tvApi.tvTvIdGet(42) } coAnswers {
            refreshed.complete(Unit)
            TvDetails(id = 42)
        }
        model.request(request())
        refreshed.await()
        coVerify(exactly = 1) { service.requestTv(any(), any()) }
        // One init read and exactly one post-submission read.
        coVerify(exactly = 2) { tvApi.tvTvIdGet(42) }
        verify(exactly = 1) { tracker.refreshNow() }
        verify(exactly = if (code == 202) 0 else 1) { tracker.registerQueueing(any()) }
        if (code != 202) verify { tracker.registerQueueing(match { it.request.requestId == 91 }) }
        if (code == 202) verify(exactly = 1) { Toast.makeText(context, "No additional seasons are available to request.", any()) }
        assertEquals(1, fixture.calls.size)
    }

    @Test
    fun cancelledSubmissionDoesNotQueueOrShowFailure() =
        runTest(dispatcher) {
            coEvery { service.requestTv(any(), any()) } throws CancellationException("cancelled")
            model.request(request())
            runCurrent()
            assertNoCompletion()
            verify(exactly = 0) { Toast.makeText(any(), any<CharSequence>(), any()) }
        }

    @Test
    fun ownerChangeDiscardsLateSubmission() =
        runTest(dispatcher) {
            val response = CompletableDeferred<TvRequestOutcome>()
            coEvery { service.requestTv(any(), any()) } coAnswers { response.await() }
            model.request(request())
            runCurrent()
            owner.value = 8
            response.complete(TvRequestOutcome.NoNewRequest)
            runCurrent()
            assertNoCompletion()
        }

    @Test
    fun clientChangeDiscardsLateSubmission() =
        runTest(dispatcher) {
            val response = CompletableDeferred<TvRequestOutcome>()
            coEvery { service.requestTv(any(), any()) } coAnswers { response.await() }
            model.request(request())
            runCurrent()
            every { service.api } returns mockk<SeerrApiClient>(relaxed = true)
            response.complete(TvRequestOutcome.NoNewRequest)
            runCurrent()
            verify(exactly = 0) { tracker.refreshNow() }
            verify(exactly = 0) { tracker.registerQueueing(any()) }
        }

    private fun assertNoCompletion() {
        verify(exactly = 0) { tracker.refreshNow() }
        verify(exactly = 0) { tracker.registerQueueing(any()) }
        coVerify(exactly = 1) { tvApi.tvTvIdGet(42) }
    }

    @Test
    fun cancelledViewModelDiscardsEvenANonCancellableLateResponse() =
        runTest(dispatcher) {
            val response = CompletableDeferred<TvRequestOutcome>()
            coEvery { service.requestTv(any(), any()) } coAnswers { withContext(NonCancellable) { response.await() } }
            model.request(request())
            runCurrent()
            stores.clear()
            response.complete(TvRequestOutcome.NoNewRequest)
            runCurrent()
            assertNoCompletion()
            verify(exactly = 0) { Toast.makeText(any(), any<CharSequence>(), any()) }
        }

    @Test
    fun malformedSuccessIsStillARequestFailure() =
        runTest(dispatcher) {
            assertSubmissionFailure(201, "{}")
        }

    @Test
    fun serverErrorIsStillARequestFailure() =
        runTest(dispatcher) {
            assertSubmissionFailure(503, "{\"message\":\"unavailable\"}")
        }

    private suspend fun assertSubmissionFailure(
        code: Int,
        body: String,
    ) {
        val fixture = TvRequestHttpFixture(code, body)
        val completed = CompletableDeferred<Unit>()
        coEvery { service.requestTv(any(), any()) } coAnswers {
            try {
                fixture.submit()
            } finally {
                completed.complete(Unit)
            }
        }
        model.request(request())
        completed.await()
        dispatcher.scheduler.runCurrent()
        verify(exactly = 0) { tracker.refreshNow() }
        verify(exactly = 0) { tracker.registerQueueing(any()) }
        verify(exactly = 1) { Toast.makeText(context, "An error occurred", any()) }
        assertEquals(1, fixture.calls.size)
    }

    private fun request() = TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null)
}
