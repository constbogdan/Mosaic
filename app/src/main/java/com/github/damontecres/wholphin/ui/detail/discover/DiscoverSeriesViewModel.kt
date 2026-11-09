package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.TvRequestOutcome
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.toSeerrRequestAcquisition
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.EnhancedCapability
import com.github.damontecres.wholphin.services.EnhancedFeatureGate
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrAcquisitionTracker
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.canRequestTv
import com.github.damontecres.wholphin.services.permitsRequest
import com.github.damontecres.wholphin.services.singleCancellationTarget
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel(assistedFactory = DiscoverSeriesViewModel.Factory::class)
class DiscoverSeriesViewModel
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
            fun create(item: DiscoverItem): DiscoverSeriesViewModel
        }

        private val _state = MutableStateFlow(DiscoverSeriesState())
        val state: StateFlow<DiscoverSeriesState> = _state

        val userConfig = seerrServerRepository.current.map { it?.config }
        val requestEnabled =
            combine(seerrServerRepository.current, state) { current, state -> current.canRequestTv(state.tvSeries.successValue, false) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)
        val request4kEnabled =
            combine(seerrServerRepository.current, state) { current, state -> current.canRequestTv(state.tvSeries.successValue, true) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        init {
            init()
        }

        private fun fetchAndSetItem(
            expectedClient: SeerrApiClient? = null,
            ownerId: Int? = null,
        ): Deferred<TvDetails?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val client = expectedClient ?: seerrService.api
                    val tv = client.tvApi.tvTvIdGet(tvId = item.id)
                    currentCoroutineContext().ensureActive()
                    if (expectedClient != null &&
                        (seerrService.api !== expectedClient || seerrServerRepository.currentUserId.first() != ownerId)
                    ) {
                        return@async null
                    }
                    _state.update { it.copy(tvSeries = DataLoadingState.Success(tv)) }
                    tv
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating tv details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for tv %s", item.id)
                try {
                    val tv = seerrService.api.tvApi.tvTvIdGet(tvId = item.id)
                    _state.update { it.copy(tvSeries = DataLoadingState.Success(tv)) }
                    val discoveredItem = seerrService.createDiscoverItem(tv)
                    backdropService.submit(discoveredItem)

                    updateSeasonStatus(tv)
                    updateAvailability()

                    viewModelScope.launchIO {
                        val rating =
                            getDiscoverRating(item.id) {
                                DiscoverRating(seerrService.api.tvApi.tvTvIdRatingsGet(tvId = item.id))
                            }
                        _state.update { it.copy(rating = rating) }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.tvApi
                                    .tvTvIdSimilarGet(tvId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.tvApi
                                    .tvTvIdRecommendationsGet(tvId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        tv.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            tv.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }

                    val trailers =
                        tv.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting tv details")
                    _state.update { it.copy(tvSeries = DataLoadingState.Error(ex)) }
                }
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

        private suspend fun updateSeasonStatus(tv: TvDetails) {
            if (seerrServerRepository.current.first().permitsRequest(SeerrItemType.TV, true)) {
                updateSeasonStatus(tv, true)
            }
            updateSeasonStatus(tv, false)
        }

        private suspend fun updateSeasonStatus(
            tv: TvDetails,
            is4k: Boolean,
        ) {
            Timber.v("updateSeasonStatus: id=%s, is4k=%s", tv.id, is4k)
            val currentUserId = seerrServerRepository.currentUserId.first()
            val requestSeasons = tv.toRequestSeasons(currentUserId, is4k)
            Timber.v("Got %s seasons, is4k=%s", requestSeasons.size, is4k)
//            requestSeasons.forEach {
//                Timber.v(
//                    "is4k=%s, season %s: availability=%s, status=%s, editable=%s",
//                    is4k,
//                    it.season.seasonNumber,
//                    it.availability,
//                    it.status,
//                    it.editable,
//                )
//            }
            _state.update {
                if (is4k) {
                    it.copy(seasons4k = requestSeasons)
                } else {
                    it.copy(seasons = requestSeasons)
                }
            }
        }

        private suspend fun updateAvailability() {
            val user = userConfig.firstOrNull()
            val canCancel =
                state.value.tvSeries.successValue
                    ?.mediaInfo
                    .singleCancellationTarget(user, SeerrItemType.TV, item.id) != null
            val status =
                state.value.tvSeries.successValue?.let { tv ->
                    val overallStatus = SeerrAvailability.from(tv.mediaInfo?.status)
                    if (overallStatus == SeerrAvailability.UNKNOWN) {
                        val userRequests =
                            tv.mediaInfo
                                ?.requests
                                ?.filter { it.requestedBy?.id == user?.id }
                                .orEmpty()
                        if (userRequests.any { RequestStatus.from(it.status) == RequestStatus.PENDING }) {
                            SeerrAvailability.PENDING
                        } else if (userRequests.any { RequestStatus.from(it.status) == RequestStatus.APPROVED }) {
                            SeerrAvailability.PROCESSING
                        } else {
                            SeerrAvailability.UNKNOWN
                        }
                    } else {
                        overallStatus
                    }
                }
            _state.update {
                it.copy(
                    canCancelRequest = canCancel,
                    availability = status ?: SeerrAvailability.UNKNOWN,
                )
            }
        }

        fun requestOnClick() {
            viewModelScope.launchIO {
                val current = seerrServerRepository.current.first()
                val tv = state.value.tvSeries.successValue
                if (!current.canRequestTv(tv, false) && !current.canRequestTv(tv, true)) return@launchIO
                try {
                    val data = seerrService.getProfilesAndFolders(SeerrItemType.TV)
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                            requestData = data,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting profiles & folders")
                    showToast(context, "Error getting profiles & folders: ${ex.localizedMessage}")
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                        )
                    }
                }
            }
        }

        fun request(request: TvRequest) {
            viewModelScope.launchIO {
                state.value.tvSeries.successValue?.let { tv ->
                    val current = seerrServerRepository.current.first()
                    if (tv.id != request.tvId || request.tvId != item.id || request.seasons.isEmpty() ||
                        !current.permitsRequest(SeerrItemType.TV, request.is4k)
                    ) {
                        return@launchIO
                    }
                    val client = seerrService.api
                    val ownerId = seerrServerRepository.currentUserId.first()
                    var submitted: TvRequestOutcome? = null
                    try {
                        submitted = seerrService.requestTv(tv, request)
                        currentCoroutineContext().ensureActive()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        if (seerrService.api !== client || seerrServerRepository.currentUserId.first() != ownerId) return@launchIO
                        Timber.e(ex, "Error requesting %s", request.tvId)
                        showToast(context, "An error occurred")
                    }
                    if (seerrService.api !== client || seerrServerRepository.currentUserId.first() != ownerId) return@launchIO
                    (submitted as? TvRequestOutcome.Submitted)?.request?.let { response ->
                        if (!enhancedFeatureGate.isEnabled(EnhancedCapability.ACQUISITION_TRACKING)) {
                            return@let
                        }
                        val queueing = response.toSeerrRequestAcquisition()
                        val seasonCounts =
                            tv.seasons
                                .orEmpty()
                                .mapNotNull { season ->
                                    val number = season.seasonNumber ?: return@mapNotNull null
                                    val count = season.episodeCount ?: return@mapNotNull null
                                    number to count
                                }.toMap()
                        seerrAcquisitionTracker.registerQueueing(
                            queueing.copy(
                                request =
                                    queueing.request.copy(
                                        discoverItem = item,
                                        requestedSeasonNumbers = request.seasons.toSet(),
                                        seasonEpisodeCounts = seasonCounts,
                                    ),
                            ),
                        )
                    }
                    if (submitted != null && enhancedFeatureGate.isEnabled(EnhancedCapability.ACQUISITION_TRACKING)) {
                        seerrAcquisitionTracker.refreshNow()
                    }
                    if (submitted == TvRequestOutcome.NoNewRequest) {
                        showToast(context, context.getString(R.string.request_no_new_seasons))
                    }

                    fetchAndSetItem(client, ownerId).await()?.let {
                        updateSeasonStatus(it)
                        updateAvailability()
                    }
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
                    state.value.tvSeries.successValue
                        ?.takeIf { it.id == item.id }
                        ?.mediaInfo ?: return@launchIO
                val owner = seerrServerRepository.current.first()?.config
                val target = media.singleCancellationTarget(owner, SeerrItemType.TV, item.id)
                if (target?.id != requestId || (target.is4k == true) != is4k) return@launchIO
                try {
                    seerrService.cancelRequest(media, SeerrItemType.TV, item.id, requestId, is4k, client)
                    currentCoroutineContext().ensureActive()
                    if (seerrService.api !== client || seerrServerRepository.currentUserId.first() != owner?.id) return@launchIO
                    fetchAndSetItem(client, owner?.id).await()?.let {
                        updateSeasonStatus(it)
                        updateAvailability()
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    if (seerrService.api !== client || seerrServerRepository.currentUserId.first() != owner?.id) return@launchIO
                    Timber.e(ex, "Error cancelling request %s", requestId)
                    showToast(context, "An error occurred")
                }
            }
        }
    }

data class DiscoverSeriesState(
    val tvSeries: DataLoadingState<TvDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val seasons4k: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
    val availability: SeerrAvailability = SeerrAvailability.UNKNOWN,
)
