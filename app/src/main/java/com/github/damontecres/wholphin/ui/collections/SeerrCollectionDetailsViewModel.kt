package com.github.damontecres.wholphin.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.services.CollectionsSession
import com.github.damontecres.wholphin.services.CollectionsSessionSource
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.detail.discover.movieRequestForQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import javax.inject.Inject

internal data class CollectionDetailsState(
    val owner: String? = null,
    val collectionId: Int = 0,
    val details: CollectionDetailsData? = null,
    val loading: Boolean = false,
    val denied: Boolean = false,
    val error: Boolean = false,
    val focusedKey: String? = null,
    val focusedIndex: Int = 0,
    val requestEligibility: Map<Int, CollectionRequestEligibility> = emptyMap(),
    val requestEligibilityLoading: Boolean = false,
    val request: CollectionRequestState? = null,
)

@HiltViewModel
internal class SeerrCollectionDetailsViewModel
    @Inject
    constructor(
        source: CollectionsSessionSource,
        private val navigation: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(CollectionDetailsState())
        private var session: CollectionsSession? = null
        private var expectedOwner: String? = null
        private var load: Job? = null
        private var eligibilityLoad: Job? = null
        private var profileLoad: Job? = null
        private var submitJob: Job? = null
        private var activeSubmissionId: Int? = null
        private var generation = 0L

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.owner == next?.owner && session?.epoch === next?.epoch) return@collect
                    load?.cancel()
                    eligibilityLoad?.cancel()
                    profileLoad?.cancel()
                    generation++
                    submitJob?.cancel()
                    session = next
                    val id = state.value.collectionId
                    val previousRequest = state.value.request
                    val interruptedId = activeSubmissionId
                    state.value =
                        CollectionDetailsState(
                            owner = next?.owner,
                            collectionId = id,
                            request =
                                previousRequest?.copy(
                                    sessionInvalid = true,
                                    submitting = false,
                                    movies =
                                        previousRequest.movies.map {
                                            if (it.outcome == null && it.item.id == interruptedId) {
                                                it.copy(selected = false, outcome = CollectionRequestOutcome.UNKNOWN_RESULT)
                                            } else if (it.selected && it.outcome == null) {
                                                it.copy(selected = false, outcome = CollectionRequestOutcome.NOT_ATTEMPTED)
                                            } else {
                                                it
                                            }
                                        },
                                    failed = previousRequest.failed + if (interruptedId != null) 1 else 0,
                                ),
                        )
                    activeSubmissionId = null
                    if (next != null && next.owner == expectedOwner && id > 0) request(id)
                }
            }
        }

        fun show(
            id: Int,
            owner: String,
        ) {
            if (id <= 0 || (state.value.collectionId == id && expectedOwner == owner)) return
            load?.cancel()
            eligibilityLoad?.cancel()
            profileLoad?.cancel()
            submitJob?.cancel()
            generation++
            expectedOwner = owner
            state.value = CollectionDetailsState(owner = session?.owner, collectionId = id)
            request(id)
        }

        fun retry() {
            val current = state.value
            if (current.collectionId > 0 && current.owner != null && !current.loading) request(current.collectionId)
        }

        fun openRequest() {
            val current = state.value
            val active = session ?: return
            if (current.request != null || current.owner != active.owner || active.owner != expectedOwner ||
                !active.isCurrent() || current.loading || current.error || current.denied || current.requestEligibilityLoading ||
                current.requestEligibility.values.none { it == CollectionRequestEligibility.ELIGIBLE }
            ) {
                return
            }
            val movies =
                current.details?.movies.orEmpty().distinctBy { it.id }.mapNotNull { item ->
                    val eligibility = current.requestEligibility[item.id] ?: return@mapNotNull null
                    if (eligibility in setOf(CollectionRequestEligibility.EXCLUDED, CollectionRequestEligibility.AVAILABLE)) {
                        null
                    } else {
                        CollectionRequestMovie(item, eligibility)
                    }
                }
            if (movies.none { it.selectable }) return
            state.value = current.copy(request = CollectionRequestState(active.owner, current.collectionId, movies))
            val token = generation
            profileLoad?.cancel()
            profileLoad =
                viewModelScope.launch {
                    try {
                        val data = active.backend.profiles()
                        if (token == generation && active.isCurrent()) {
                            updateRequest(active) {
                                it.copy(loadingProfiles = false, profiles = data)
                            }
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        if (token == generation && active.isCurrent()) {
                            updateRequest(active) {
                                it.copy(loadingProfiles = false, profileError = true)
                            }
                        }
                    }
                }
        }

        fun closeRequest() {
            val request = state.value.request ?: return
            if (request.submitting) return
            profileLoad?.cancel()
            state.value = state.value.copy(request = null)
            if (request.submitted > 0 || request.failed > 0) retry()
        }

        fun toggleRequest(id: Int) =
            updateSelection { request ->
                request.copy(
                    movies =
                        request.movies.map { movie ->
                            if (movie.item.id == id && movie.selectable) movie.copy(selected = !movie.selected) else movie
                        },
                )
            }

        fun toggleAllRequests() =
            updateSelection { request ->
                val select = !request.allSelected
                request.copy(movies = request.movies.map { if (it.selectable) it.copy(selected = select) else it })
            }

        private fun updateSelection(transform: (CollectionRequestState) -> CollectionRequestState) {
            val active = session ?: return
            val request = state.value.request ?: return
            if (request.submitting || request.sessionInvalid || !active.isCurrent() || request.owner != active.owner) return
            state.value = state.value.copy(request = transform(request))
        }

        private fun updateRequest(
            active: CollectionsSession,
            transform: (CollectionRequestState) -> CollectionRequestState,
        ) {
            val current = state.value
            val request = current.request ?: return
            if (request.owner == active.owner && request.collectionId == current.collectionId && !request.sessionInvalid) {
                state.value = current.copy(request = transform(request))
            }
        }

        fun submitRequests(profileName: String) {
            val active = session ?: return
            val request = state.value.request ?: return
            val selected = request.movies.filter { it.selected && it.selectable }.map { it.item.id }
            val first = selected.firstOrNull() ?: return
            val quality = movieRequestForQuality(request.profiles, first, profileName) ?: return
            if (request.submitting || request.loadingProfiles || request.profileError || request.sessionInvalid ||
                request.owner != active.owner || !active.isCurrent() || quality.is4k
            ) {
                return
            }
            state.value = state.value.copy(request = request.copy(submitting = true))
            val token = generation
            submitJob =
                viewModelScope.launch {
                    try {
                        for (id in selected) {
                            if (token != generation || !active.isCurrent()) {
                                invalidateRemaining(active, selected)
                                return@launch
                            }
                            val item = request.movies.first { it.item.id == id }.item
                            try {
                                val eligibility = active.backend.requestEligibility(item)
                                if (token != generation || !active.isCurrent()) {
                                    invalidateRemaining(active, selected)
                                    return@launch
                                }
                                if (eligibility != CollectionRequestEligibility.ELIGIBLE) {
                                    updateOutcome(active, id, CollectionRequestOutcome.NO_LONGER_ELIGIBLE, eligibility)
                                    continue
                                }
                                val payload = movieRequestForQuality(request.profiles, id, profileName)
                                if (payload == null) {
                                    updateOutcome(active, id, CollectionRequestOutcome.FAILED, eligibility, "Quality profile unavailable")
                                    continue
                                }
                                activeSubmissionId = id
                                val response = active.backend.submit(payload)
                                activeSubmissionId = null
                                if (response.status in
                                    setOf(
                                        RequestStatus.PENDING.status,
                                        RequestStatus.APPROVED.status,
                                        RequestStatus.COMPLETED.status,
                                    )
                                ) {
                                    updateOutcome(active, id, CollectionRequestOutcome.REQUESTED, CollectionRequestEligibility.REQUESTED)
                                } else {
                                    updateOutcome(active, id, CollectionRequestOutcome.UNKNOWN_RESULT, CollectionRequestEligibility.UNKNOWN)
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                if (!active.isCurrent()) {
                                    invalidateRemaining(active, selected)
                                    return@launch
                                }
                                val reconciled =
                                    try {
                                        active.backend.requestEligibility(item)
                                    } catch (
                                        _: Exception,
                                    ) {
                                        CollectionRequestEligibility.UNKNOWN
                                    }
                                when (reconciled) {
                                    CollectionRequestEligibility.ELIGIBLE -> {
                                        val ambiguous = error is IOException || (error is ClientException && error.statusCode >= 500)
                                        updateOutcome(
                                            active,
                                            id,
                                            if (ambiguous) CollectionRequestOutcome.UNKNOWN_RESULT else CollectionRequestOutcome.FAILED,
                                            reconciled,
                                        )
                                    }

                                    CollectionRequestEligibility.REQUESTED,
                                    CollectionRequestEligibility.PROCESSING,
                                    CollectionRequestEligibility.AVAILABLE,
                                    -> {
                                        updateOutcome(active, id, CollectionRequestOutcome.NO_LONGER_ELIGIBLE, reconciled)
                                    }

                                    else -> {
                                        updateOutcome(active, id, CollectionRequestOutcome.UNKNOWN_RESULT, reconciled)
                                    }
                                }
                            }
                            activeSubmissionId = null
                        }
                    } finally {
                        activeSubmissionId = null
                        updateRequest(active) { it.copy(submitting = false) }
                        if (token == generation && active.isCurrent()) retry()
                    }
                }
        }

        private fun updateOutcome(
            active: CollectionsSession,
            id: Int,
            outcome: CollectionRequestOutcome,
            eligibility: CollectionRequestEligibility,
            error: String? = null,
        ) = updateRequest(active) { request ->
            request.copy(
                movies =
                    request.movies.map {
                        if (it.item.id ==
                            id
                        ) {
                            it.copy(eligibility = eligibility, selected = false, outcome = outcome, error = error)
                        } else {
                            it
                        }
                    },
                submitted = request.submitted + if (outcome == CollectionRequestOutcome.REQUESTED) 1 else 0,
                failed =
                    request.failed +
                        if (outcome == CollectionRequestOutcome.FAILED || outcome == CollectionRequestOutcome.UNKNOWN_RESULT) 1 else 0,
            )
        }

        private fun invalidateRemaining(
            active: CollectionsSession,
            selected: List<Int>,
        ) = updateRequest(active) { request ->
            request.copy(
                submitting = false,
                sessionInvalid = true,
                movies =
                    request.movies.map {
                        if (it.item.id in selected &&
                            it.outcome == null
                        ) {
                            it.copy(selected = false, outcome = CollectionRequestOutcome.NOT_ATTEMPTED)
                        } else {
                            it
                        }
                    },
            )
        }

        fun position(index: Int) {
            val current = state.value
            val movie = current.details?.movies?.getOrNull(index) ?: return
            state.value = current.copy(focusedKey = "tmdb:movie:${movie.id}", focusedIndex = index)
        }

        fun open(movie: DiscoverItem) {
            if (session?.isCurrent() != true) return
            val current = state.value
            val details = current.details ?: return
            val index = details.movies.indexOfFirst { it.id == movie.id }
            if (index < 0) return
            state.value = current.copy(focusedKey = "tmdb:movie:${movie.id}", focusedIndex = index)
            // No Jellyfin ID is inferred from Seerr availability; Discover resolves ordinary details.
            navigation.navigateTo(movie.destination)
        }

        private fun request(id: Int) {
            val active = session ?: return
            if (active.owner != expectedOwner || !active.isCurrent()) return
            val token = ++generation
            load?.cancel()
            state.value = state.value.copy(loading = true, error = false, denied = false)
            load =
                viewModelScope.launch {
                    try {
                        val details = active.backend.detail(id)
                        if (token != generation || !active.isCurrent() || state.value.collectionId != id) return@launch
                        require(details.id == id)
                        val focused = state.value.focusedKey
                        val index = details.movies.indexOfFirst { "tmdb:movie:${it.id}" == focused }.takeIf { it >= 0 } ?: 0
                        state.value =
                            state.value.copy(
                                details = details,
                                focusedKey = details.movies.getOrNull(index)?.let { "tmdb:movie:${it.id}" },
                                focusedIndex = index,
                                loading = false,
                                error = false,
                                denied = false,
                                requestEligibility = emptyMap(),
                                requestEligibilityLoading = true,
                            )
                        scanEligibility(active, details, token)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: ClientException) {
                        if (token == generation && active.isCurrent()) {
                            state.value =
                                state.value.copy(loading = false, error = error.statusCode != 403, denied = error.statusCode == 403)
                        }
                    } catch (error: Exception) {
                        if (token == generation && active.isCurrent()) state.value = state.value.copy(loading = false, error = true)
                    }
                }
        }

        private fun scanEligibility(
            active: CollectionsSession,
            details: CollectionDetailsData,
            token: Long,
        ) {
            eligibilityLoad?.cancel()
            eligibilityLoad =
                viewModelScope.launch {
                    val semaphore = Semaphore(4)
                    val results =
                        coroutineScope {
                            details.movies
                                .map { item ->
                                    async {
                                        val eligibility =
                                            try {
                                                semaphore.withPermit { active.backend.requestEligibility(item) }
                                            } catch (cancel: CancellationException) {
                                                throw cancel
                                            } catch (_: Exception) {
                                                CollectionRequestEligibility.UNKNOWN
                                            }
                                        item.id to eligibility
                                    }
                                }.awaitAll()
                                .toMap()
                        }
                    if (token == generation && active.isCurrent() && state.value.collectionId == details.id) {
                        state.value = state.value.copy(requestEligibility = results, requestEligibilityLoading = false)
                    }
                }
        }
    }
