package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.data.model.toSeerrRequestAcquisition
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.EnhancedCapability
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.services.canRequestMovie
import com.github.damontecres.wholphin.services.singleCancellationTarget
import com.github.damontecres.wholphin.services.submitMovieRequest
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel(assistedFactory = DiscoverMovieViewModel.Factory::class)
class DiscoverMovieViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val seerrAcquisitionTracker: SeerrAcquisitionTracker,
        private val enhancedFeatureGate: EnhancedFeatureGate,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverMovieViewModel
        }

        private val _state = MutableStateFlow(DiscoverMovieState())
        val state: StateFlow<DiscoverMovieState> = _state

        private data class ProfileSession(
            val current: CurrentSeerr,
            val client: SeerrApiClient,
        )

        @Volatile private var profileSession: ProfileSession? = null

        @Volatile private var openingSession: ProfileSession? = null
        private val profileEpoch = AtomicInteger(0)

        @Volatile private var profileLoadJob: Job? = null
        private val requestInFlight = AtomicBoolean(false)

        init {
            viewModelScope.launch(WholphinDispatchers.Main.immediate) {
                seerrServerRepository.connection.collect {
                    openingSession?.let { session -> if (!isCurrent(session)) closeRequestDialog() }
                }
            }
        }

        private fun activeSession(): ProfileSession? =
            (seerrServerRepository.connection.value.connection as? SeerrConnectionStatus.Success)
                ?.current
                ?.let { ProfileSession(it, seerrService.api) }

        private fun isCurrent(session: ProfileSession): Boolean {
            val active = activeSession()
            return active?.current == session.current && active.client === session.client
        }

        val userConfig = seerrServerRepository.current.map { it?.config }
        val requestEnabled =
            combine(seerrServerRepository.current, state) { current, state -> current.canRequestMovie(state.movie.successValue, false) }
        val request4kEnabled =
            combine(seerrServerRepository.current, state) { current, state -> current.canRequestMovie(state.movie.successValue, true) }

        init {
            init()
        }

        private fun fetchAndSetItem(
            expectedClient: SeerrApiClient? = null,
            ownerId: Int? = null,
        ): Deferred<MovieDetails?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val movie = (expectedClient ?: seerrService.api).moviesApi.movieMovieIdGet(movieId = item.id)
                    currentCoroutineContext().ensureActive()
                    if (expectedClient != null &&
                        (seerrService.api !== expectedClient || seerrServerRepository.currentUserId.firstOrNull() != ownerId)
                    ) {
                        return@async null
                    }
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    movie
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for movie %s", item.id)
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    val discoveredItem = seerrService.createDiscoverItem(movie)
                    backdropService.submit(discoveredItem)

                    updateCanCancel()

                    viewModelScope.launchIO {
                        val rating =
                            getDiscoverRating(item.id) {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id),
                                )
                            }
                        _state.update { it.copy(rating = rating) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdSimilarGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdRecommendationsGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        movie.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            movie.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }
                    val trailers =
                        movie.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    _state.update { it.copy(movie = DataLoadingState.Error(ex)) }
                }
            }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            val canCancel =
                state.value.movie.successValue
                    ?.mediaInfo
                    .singleCancellationTarget(user, SeerrItemType.MOVIE, item.id) != null
            _state.update { it.copy(canCancelRequest = canCancel) }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun goTo(
            mediaInfo: MediaInfo?,
            type: BaseItemKind,
        ) {
            viewModelScope.launchDefault {
                goToButtonDiscover(mediaInfo, type, context, navigationManager)
            }
        }

        fun request(request: MovieRequest) {
            val prepared = profileSession ?: return
            val snapshot = state.value
            if (requestInFlight.get() || snapshot.profileLoading != LoadingState.Success || request.is4k ||
                request.data != snapshot.requestData || request.movieId != item.id ||
                request !in
                listOfNotNull(
                    movieRequestForQuality(snapshot.requestData, item.id, MOVIE_PROFILE_1080P_HDR),
                    movieRequestForQuality(snapshot.requestData, item.id, MOVIE_PROFILE_2160P_HDR),
                ) || !isCurrent(prepared) || !prepared.current.canRequestMovie(snapshot.movie.successValue, false)
            ) {
                return
            }
            if (!requestInFlight.compareAndSet(false, true)) return
            viewModelScope.launchIO {
                try {
                    if (!isCurrent(prepared)) return@launchIO
                    val fresh = prepared.client.moviesApi.movieMovieIdGet(movieId = item.id)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(prepared) || !prepared.current.canRequestMovie(fresh, false)) return@launchIO
                    val submitted = prepared.client.submitMovieRequest(request)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(prepared)) return@launchIO
                    if (enhancedFeatureGate.isEnabled(EnhancedCapability.ACQUISITION_TRACKING)) {
                        try {
                            val queueing = submitted.toSeerrRequestAcquisition()
                            seerrAcquisitionTracker.registerQueueing(
                                queueing.copy(request = queueing.request.copy(discoverItem = item)),
                            )
                            seerrAcquisitionTracker.refreshNow()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.e(ex, "Movie acquisition tracking failed after request submission")
                        }
                    }
                    fetchAndSetItem(prepared.client, prepared.current.config.id).await()
                    updateCanCancel()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    if (isCurrent(prepared)) {
                        Timber.e(ex, "Error requesting %s", request.movieId)
                        showToast(context, "An error occurred")
                    }
                } finally {
                    requestInFlight.set(false)
                }
            }
        }

        fun cancelRequest(
            requestId: Int,
            is4k: Boolean,
        ) {
            viewModelScope.launchIO {
                val client = seerrService.api
                val media =
                    state.value.movie.successValue
                        ?.takeIf { it.id == item.id }
                        ?.mediaInfo ?: return@launchIO
                val owner = seerrServerRepository.current.firstOrNull()?.config
                val target = media.singleCancellationTarget(owner, SeerrItemType.MOVIE, item.id)
                if (target?.id != requestId || (target.is4k == true) != is4k) return@launchIO
                try {
                    seerrService.cancelRequest(media, SeerrItemType.MOVIE, item.id, requestId, is4k, client)
                    currentCoroutineContext().ensureActive()
                    if (seerrService.api !== client || seerrServerRepository.currentUserId.firstOrNull() != owner?.id) return@launchIO
                    fetchAndSetItem(client, owner?.id).await()
                    updateCanCancel()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    if (seerrService.api !== client || seerrServerRepository.currentUserId.firstOrNull() != owner?.id) return@launchIO
                    Timber.e(ex, "Error cancelling request %s", requestId)
                    showToast(context, "An error occurred")
                }
            }
        }

        fun requestOnClick(): Boolean {
            val session = activeSession() ?: return false
            if (requestInFlight.get() || !session.current.canRequestMovie(state.value.movie.successValue, false)) return false
            val epoch = profileEpoch.incrementAndGet()
            profileLoadJob?.cancel()
            profileSession = null
            openingSession = session
            _state.update { it.copy(profileLoading = LoadingState.Loading, requestData = SeerrRequestData()) }
            profileLoadJob =
                viewModelScope.launchIO {
                    try {
                        val shouldLoad =
                            withContext(WholphinDispatchers.Main.immediate) {
                                if (epoch != profileEpoch.get()) {
                                    false
                                } else if (!isCurrent(session)) {
                                    closeRequestDialog()
                                    false
                                } else {
                                    true
                                }
                            }
                        if (!shouldLoad) return@launchIO
                        val data = seerrService.getProfilesAndFolders(SeerrItemType.MOVIE, include4k = false)
                        currentCoroutineContext().ensureActive()
                        // Opening, closing, connection invalidation and publication share Main.
                        // There is no suspension between the final check and the two state writes.
                        withContext(WholphinDispatchers.Main.immediate) {
                            if (epoch == profileEpoch.get()) {
                                if (!isCurrent(session)) {
                                    closeRequestDialog()
                                } else {
                                    profileSession = session
                                    _state.update { it.copy(profileLoading = LoadingState.Success, requestData = data) }
                                }
                            }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        withContext(WholphinDispatchers.Main.immediate) {
                            if (epoch == profileEpoch.get()) {
                                if (!isCurrent(session)) {
                                    closeRequestDialog()
                                } else {
                                    Timber.e(ex, "Error getting profiles & folders")
                                    showToast(context, "Error getting profiles & folders: ${ex.localizedMessage}")
                                    _state.update { it.copy(profileLoading = LoadingState.Error(ex), requestData = SeerrRequestData()) }
                                }
                            }
                        }
                    }
                }
            return true
        }

        fun closeRequestDialog() {
            profileEpoch.incrementAndGet()
            profileLoadJob?.cancel()
            profileSession = null
            openingSession = null
            _state.update { it.copy(profileLoading = LoadingState.Pending, requestData = SeerrRequestData()) }
        }
    }

data class DiscoverMovieState(
    val movie: DataLoadingState<MovieDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
)
