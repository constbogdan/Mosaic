package com.github.damontecres.wholphin.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CollectionMovieLink
import com.github.damontecres.wholphin.services.CollectionsSession
import com.github.damontecres.wholphin.services.CollectionsSessionSource
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.collectionProviderIds
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

internal data class CollectionMovieLinkState(
    val owner: String? = null,
    val link: CollectionMovieLink? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val retryable: Boolean = false,
)

private sealed interface CollectionMovieInput {
    val origin: Destination

    data class Discovered(
        val movie: MovieDetails,
        override val origin: Destination,
    ) : CollectionMovieInput

    data class Local(
        val movie: BaseItem,
        override val origin: Destination,
    ) : CollectionMovieInput
}

@HiltViewModel
internal class CollectionMovieLinkViewModel
    @Inject
    constructor(
        source: CollectionsSessionSource,
        private val navigation: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(CollectionMovieLinkState())
        private var session: CollectionsSession? = null
        private var input: CollectionMovieInput? = null
        private var load: Job? = null
        private var generation = 0L

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.owner == next?.owner && session?.epoch === next?.epoch) return@collect
                    session = next
                    refresh()
                }
            }
        }

        fun showDiscover(
            movie: MovieDetails,
            origin: Destination,
        ) {
            val next = CollectionMovieInput.Discovered(movie, origin)
            if (input == next) return
            input = next
            refresh()
        }

        fun showLocal(
            movie: BaseItem,
            origin: Destination,
        ) {
            val next = CollectionMovieInput.Local(movie, origin)
            if (input == next) return
            input = next
            refresh()
        }

        fun retry() {
            if (state.value.error && !state.value.loading) refresh()
        }

        fun open(link: CollectionMovieLink) {
            val active = session ?: return
            if (state.value.link != link || state.value.owner != active.owner || !active.isCurrent()) return
            if (navigation.backStack.lastOrNull() != input?.origin) return
            navigation.navigateTo(Destination.SeerrCollection(link.id, null, null, active.owner))
        }

        private fun refresh() {
            load?.cancel()
            val token = ++generation
            val active = session
            val currentInput = input
            state.value = CollectionMovieLinkState(owner = active?.owner)
            if (active == null || !active.isCurrent() || currentInput == null) return
            when (currentInput) {
                is CollectionMovieInput.Discovered -> {
                    val movie = currentInput.movie
                    if (movie.id != (currentInput.origin as? Destination.DiscoveredItem)?.item?.id) return
                    state.value = state.value.copy(link = active.backend.linkFromMovie(movie))
                }

                is CollectionMovieInput.Local -> {
                    val movie = currentInput.movie
                    if (movie.type != BaseItemKind.MOVIE || movie.id != (currentInput.origin as? Destination.MediaItem)?.itemId) return
                    val ids = collectionProviderIds(movie.data.providerIds) ?: return
                    state.value = state.value.copy(loading = true, retryable = true)
                    load =
                        viewModelScope.launch {
                            try {
                                val link = active.backend.linkFromJellyfinMovie(ids.first, ids.second)
                                if (token == generation && active.isCurrent()) {
                                    state.value = state.value.copy(link = link, loading = false, error = false)
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                if (token == generation && active.isCurrent()) {
                                    state.value = state.value.copy(loading = false, error = true)
                                }
                            }
                        }
                }
            }
        }
    }
