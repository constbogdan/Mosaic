package com.github.damontecres.wholphin.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.services.CollectionsSession
import com.github.damontecres.wholphin.services.CollectionsSessionSource
import com.github.damontecres.wholphin.services.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
        private var generation = 0L

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.owner == next?.owner && session?.epoch === next?.epoch) return@collect
                    load?.cancel()
                    generation++
                    session = next
                    val id = state.value.collectionId
                    state.value = CollectionDetailsState(owner = next?.owner, collectionId = id)
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
            generation++
            expectedOwner = owner
            state.value = CollectionDetailsState(owner = session?.owner, collectionId = id)
            request(id)
        }

        fun retry() {
            val current = state.value
            if (current.collectionId > 0 && current.owner != null && !current.loading) request(current.collectionId)
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
                            )
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
    }
