package com.github.damontecres.wholphin.ui.discover

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.toSeerrRequestAcquisition
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.EnhancedCapability
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.canRequestMovie
import com.github.damontecres.wholphin.services.submitMovieRequest
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.discover.MovieRequest
import com.github.damontecres.wholphin.ui.detail.discover.RequestMovieDialog
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.getDiscoverRating
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Only Seerr-backed movie cards call this; TV and Jellyfin-local cards retain their actions. */
class QuickMovieRequestActions(
    private val openAction: (DiscoverItem, () -> Unit) -> Unit,
) {
    fun open(
        item: DiscoverItem,
        onUnavailable: () -> Unit = {},
    ) = openAction(item, onUnavailable)
}

val LocalQuickMovieRequest = staticCompositionLocalOf { QuickMovieRequestActions { _, _ -> } }

/** An opening long press cannot also confirm the dialog, even if its key repeats reach the dialog. */
internal class QuickMovieConfirmGate {
    private enum class Phase { IDLE, OPENING_PRESS, ABORTING_PRESS, AWAITING_NEW_PRESS, NEW_PRESS_DOWN, NEW_PRESS_UP }

    private var phase by mutableStateOf(Phase.IDLE)

    val released: Boolean get() = phase != Phase.OPENING_PRESS && phase != Phase.IDLE

    // The click callback must prove a complete, separate OK press, not merely an enabled button.
    fun consumeConfirmation(): Boolean {
        if (phase != Phase.NEW_PRESS_UP) return false
        phase = Phase.AWAITING_NEW_PRESS
        return true
    }

    fun reset() {
        phase = Phase.OPENING_PRESS
    }

    /** A details-button click may already have released OK before the dialog is composed. */
    fun resetAfterClick() {
        phase = Phase.AWAITING_NEW_PRESS
    }

    fun clear() {
        phase = Phase.IDLE
    }

    /** Consume the original held key through release, then restore ordinary card input. */
    fun abortOpening() {
        phase = if (phase == Phase.OPENING_PRESS || phase == Phase.ABORTING_PRESS) Phase.ABORTING_PRESS else Phase.IDLE
    }

    fun onKey(
        action: Int,
        keyCode: Int,
        repeatCount: Int = 0,
    ): Boolean {
        if (keyCode !in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (phase == Phase.ABORTING_PRESS && action == KeyEvent.ACTION_DOWN) clear()
            return false
        }
        return when (phase) {
            Phase.IDLE -> {
                false
            }

            Phase.OPENING_PRESS -> {
                if (action == KeyEvent.ACTION_UP) phase = Phase.AWAITING_NEW_PRESS
                true
            }

            Phase.ABORTING_PRESS -> {
                if (action == KeyEvent.ACTION_UP) clear()
                true
            }

            Phase.AWAITING_NEW_PRESS -> {
                if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                    phase = Phase.NEW_PRESS_DOWN
                    false
                } else {
                    true
                }
            }

            Phase.NEW_PRESS_DOWN -> {
                if (action == KeyEvent.ACTION_UP) {
                    phase = Phase.NEW_PRESS_UP
                    false
                } else {
                    true
                }
            }

            Phase.NEW_PRESS_UP -> {
                true
            }
        }
    }
}

internal data class QuickMovieRequestState(
    val item: DiscoverItem,
    val session: QuickMovieSession,
    val movie: MovieDetails? = null,
    val rating: DiscoverRating? = null,
    val data: SeerrRequestData = SeerrRequestData(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val submitting: Boolean = false,
)

internal data class QuickMovieSession(
    val current: CurrentSeerr,
    val client: SeerrApiClient,
)

@HiltViewModel
class QuickMovieRequestViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        private val acquisitionTracker: SeerrAcquisitionTracker,
        private val enhancedFeatureGate: EnhancedFeatureGate,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<QuickMovieRequestState?>(null)
        internal val state: StateFlow<QuickMovieRequestState?> = mutableState
        private var loadJob: Job? = null
        private var submitJob: Job? = null
        private var episode = 0
        private val opening = AtomicBoolean(false)
        private var unavailableAction: (() -> Unit)? = null
        private var abortOpeningAction: (() -> Unit)? = null

        init {
            viewModelScope.launchIO {
                seerrServerRepository.current.collect { current ->
                    if (mutableState.value?.session?.current != null && mutableState.value?.session?.current != current) dismiss()
                }
            }
        }

        fun open(
            item: DiscoverItem,
            onUnavailable: () -> Unit = {},
            onStart: () -> Unit = {},
            onAbort: () -> Unit = {},
        ) {
            if (item.type != SeerrItemType.MOVIE) return
            if (item.id <= 0) {
                onUnavailable()
                return
            }
            if (!opening.compareAndSet(false, true)) return
            if (mutableState.value != null) {
                opening.set(false)
                return
            }
            // Capture the actual card owner's Seerr session before dispatching any I/O.
            val origin = (seerrServerRepository.connection.value.connection as? SeerrConnectionStatus.Success)?.current
            if (origin == null) {
                opening.set(false)
                onUnavailable()
                return
            }
            val startEpisode = episode
            onStart()
            val published = AtomicBoolean(false)
            val abortDelivered = AtomicBoolean(false)
            val abortOnce = { if (abortDelivered.compareAndSet(false, true)) onAbort() }
            abortOpeningAction = abortOnce
            val job =
                viewModelScope.launchIO {
                    try {
                        val current = seerrServerRepository.current.firstOrNull()
                        if (startEpisode != episode || current == null || current != origin) return@launchIO
                        val client = seerrService.api
                        val session = QuickMovieSession(current, client)
                        if (mutableState.value != null || !sameSession(session)) return@launchIO
                        val token = ++episode
                        unavailableAction = onUnavailable
                        mutableState.value = QuickMovieRequestState(item, session)
                        published.set(true)
                        load(token)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Quick movie request unavailable")
                        if (startEpisode == episode &&
                            (seerrServerRepository.connection.value.connection as? SeerrConnectionStatus.Success)?.current == origin
                        ) {
                            onUnavailable()
                        }
                    } finally {
                        if (!published.get()) abortOnce()
                        opening.set(false)
                    }
                }
            // Also cover cancellation before the dispatched coroutine enters its try/finally.
            job.invokeOnCompletion {
                if (!published.get()) {
                    abortOnce()
                    opening.set(false)
                }
            }
        }

        private suspend fun sameSession(session: QuickMovieSession): Boolean =
            seerrService.api === session.client && seerrServerRepository.current.firstOrNull() == session.current

        private fun load(token: Int) {
            val original = mutableState.value ?: return
            loadJob?.cancel()
            loadJob =
                viewModelScope.launchIO {
                    try {
                        val movie =
                            original.session.client.moviesApi
                                .movieMovieIdGet(movieId = original.item.id)
                        currentCoroutineContext().ensureActive()
                        if (token != episode || !sameSession(original.session)) return@launchIO
                        if (!original.session.current.canRequestMovie(movie, false)) {
                            val fallback = unavailableAction
                            dismiss()
                            fallback?.invoke()
                            return@launchIO
                        }
                        val data = seerrService.getProfilesAndFolders(SeerrItemType.MOVIE, include4k = false)
                        currentCoroutineContext().ensureActive()
                        if (token != episode || !sameSession(original.session)) return@launchIO
                        mutableState.update {
                            it
                                ?.takeIf { state ->
                                    state.item.id == original.item.id
                                }?.copy(movie = movie, data = data, loading = false)
                        }
                        val rating =
                            getDiscoverRating(original.item.id) {
                                DiscoverRating(
                                    original.session.client.moviesApi
                                        .movieMovieIdRatingsGet(movieId = original.item.id),
                                )
                            }
                        currentCoroutineContext().ensureActive()
                        if (token == episode && sameSession(original.session)) {
                            mutableState.update { it?.takeIf { state -> state.item.id == original.item.id }?.copy(rating = rating) }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Quick movie request loading failed")
                        if (token == episode &&
                            sameSession(original.session)
                        ) {
                            mutableState.update { it?.copy(loading = false, error = true) }
                        }
                    }
                }
        }

        fun retry() {
            val original = mutableState.value ?: return
            if (original.loading || original.submitting || !original.error) return
            mutableState.update { it?.copy(loading = true, error = false) }
            load(episode)
        }

        fun submit(request: MovieRequest) {
            val original = mutableState.value ?: return
            if (original.loading || original.error || original.submitting || original.movie == null ||
                request.is4k || request.movieId != original.item.id || request.data != original.data
            ) {
                return
            }
            mutableState.update { it?.copy(submitting = true) }
            val token = episode
            submitJob =
                viewModelScope.launchIO {
                    try {
                        if (!sameSession(original.session)) return@launchIO
                        val fresh =
                            original.session.client.moviesApi
                                .movieMovieIdGet(movieId = original.item.id)
                        currentCoroutineContext().ensureActive()
                        if (token != episode || !sameSession(original.session) || !original.session.current.canRequestMovie(fresh, false)) {
                            dismiss()
                            return@launchIO
                        }
                        val submitted = original.session.client.submitMovieRequest(request)
                        currentCoroutineContext().ensureActive()
                        if (token != episode || !sameSession(original.session)) return@launchIO
                        if (enhancedFeatureGate.isEnabled(EnhancedCapability.ACQUISITION_TRACKING)) {
                            try {
                                val queueing = submitted.toSeerrRequestAcquisition()
                                acquisitionTracker.registerQueueing(
                                    queueing.copy(request = queueing.request.copy(discoverItem = original.item)),
                                )
                                acquisitionTracker.refreshNow()
                            } catch (ex: Exception) {
                                // The request succeeded; acquisition presentation must not offer a second submission.
                                Timber.e(ex, "Quick movie acquisition tracking failed after submission")
                            }
                        }
                        dismiss()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Quick movie request submission failed")
                        if (token == episode &&
                            sameSession(original.session)
                        ) {
                            mutableState.update { it?.copy(error = true, submitting = false) }
                        }
                    }
                }
        }

        fun dismiss() {
            episode++
            abortOpeningAction?.invoke()
            abortOpeningAction = null
            mutableState.value = null
            unavailableAction = null
            loadJob?.cancel()
            submitJob?.cancel()
        }
    }

/** One host overlays all Seerr-backed card destinations without changing their grid lifecycle. */
@Composable
fun QuickMovieRequestHost(
    sessionKey: String,
    viewModel: QuickMovieRequestViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val confirmationGate = remember { QuickMovieConfirmGate() }
    LaunchedEffect(sessionKey) {
        confirmationGate.abortOpening()
        viewModel.dismiss()
    }
    val inputGuard =
        Modifier.onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            confirmationGate.onKey(native.action, native.keyCode, native.repeatCount)
        }
    val actions =
        remember(viewModel) {
            QuickMovieRequestActions { item, onUnavailable ->
                viewModel.open(item, onUnavailable, confirmationGate::reset, confirmationGate::abortOpening)
            }
        }
    CompositionLocalProvider(LocalQuickMovieRequest provides actions) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                confirmationGate.onKey(native.action, native.keyCode, native.repeatCount)
            },
        ) {
            content()
        }
        state?.let { current ->
            val movie = current.movie
            if (!current.error) {
                RequestMovieDialog(
                    loading = if (current.loading || current.submitting) LoadingState.Loading else LoadingState.Success,
                    data = current.data,
                    requestEnabled = true,
                    movie = movie,
                    posterUrl = current.item.posterUrl,
                    rating = current.rating,
                    fallbackItem = current.item,
                    onSubmit = { request ->
                        viewModel.submit(request)
                    },
                    confirmAction = confirmationGate::consumeConfirmation,
                    onDismissRequest = {
                        confirmationGate.clear()
                        viewModel.dismiss()
                    },
                    confirmationReady = confirmationGate.released && !current.submitting,
                    dialogModifier = inputGuard,
                )
            } else {
                BasicDialog(onDismissRequest = {
                    confirmationGate.clear()
                    viewModel.dismiss()
                }, modifier = inputGuard) {
                    if (current.error) {
                        Column(Modifier.padding(24.dp)) {
                            Text(stringResource(R.string.compose_error_message_title))
                            Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                        }
                    } else {
                        LoadingPage(Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}
