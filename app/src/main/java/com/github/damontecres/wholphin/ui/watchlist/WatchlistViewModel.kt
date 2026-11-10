package com.github.damontecres.wholphin.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.watchlistFocusAfterRemoval
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.WatchlistRepository
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WatchlistScreenState(
    val owner: String? = null,
    val category: WatchlistCategory = WatchlistCategory.ALL,
    val entries: List<WatchlistEntry> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val failedPage: Int? = null,
    val focusedIndex: Int = 0,
    val gridEpoch: Long = 0,
)

@HiltViewModel
class WatchlistViewModel
    @Inject
    constructor(
        val repository: WatchlistRepository,
        private val navigation: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(WatchlistScreenState())
        private var load: Job? = null
        private var generation = 0L
        private var lastRefresh = 0L

        init {
            viewModelScope.launch {
                repository.state
                    .map { Triple(it.owner, it.revision, it.busy.isNotEmpty()) }
                    .distinctUntilChanged()
                    .collect { (owner, _, busy) ->
                        load?.cancel()
                        generation++
                        val previous = state.value
                        state.value = if (owner != previous.owner) WatchlistScreenState(owner = owner) else previous.copy(loading = false)
                        if (owner == null) {
                            state.value =
                                state.value.copy(
                                    error = "Personal Watchlist requires a Jellyfin-linked Seerr login, not a shared API key.",
                                )
                        } else if (!busy) {
                            refresh(force = true)
                        }
                    }
            }
        }

        fun category(category: WatchlistCategory) {
            if (category == state.value.category) return
            load?.cancel()
            generation++
            state.value = WatchlistScreenState(owner = state.value.owner, category = category)
            refresh(force = true)
        }

        fun refresh(force: Boolean = false) {
            if (state.value.owner == null || state.value.loading) return
            if (!force && System.currentTimeMillis() - lastRefresh < 60_000) return
            request(1, force = true)
        }

        fun position(index: Int) {
            state.value = state.value.copy(focusedIndex = index)
            val current = state.value
            if (!current.loading && current.error == null && current.page < current.totalPages && index >= current.entries.size - 6) {
                request(current.page + 1, force = false)
            }
        }

        fun retry() {
            if (state.value.owner == null || state.value.loading) return
            request(state.value.failedPage ?: 1, force = true)
        }

        private fun request(
            page: Int,
            force: Boolean,
        ) {
            val token = ++generation
            val current = state.value
            state.value = current.copy(loading = true, error = null, failedPage = null)
            load =
                viewModelScope.launch {
                    try {
                        val result = withContext(WholphinDispatchers.IO) { repository.page(current.category, page, force) }
                        if (token != generation || repository.state.value.owner != current.owner) return@launch
                        val unchangedFirstPage =
                            page == 1 && current.page > 0 &&
                                current.totalResults == result.totalResults && current.entries.take(20) == result.entries
                        val entries =
                            when {
                                unchangedFirstPage -> current.entries
                                page == 1 -> result.entries.distinctBy { it.key }
                                else -> (current.entries + result.entries).distinctBy { it.key }
                            }
                        state.value =
                            current.copy(
                                entries = entries,
                                page = if (unchangedFirstPage) current.page else page,
                                totalPages = result.totalPages,
                                totalResults = result.totalResults,
                                loading = false,
                                error = null,
                                failedPage = null,
                                focusedIndex = watchlistFocusAfterRemoval(state.value.focusedIndex, entries.size),
                                gridEpoch = if (page == 1 && !unchangedFirstPage) current.gridEpoch + 1 else current.gridEpoch,
                            )
                        lastRefresh = System.currentTimeMillis()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        if (token ==
                            generation
                        ) {
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    failedPage = page,
                                    error =
                                        if (error is com.github.damontecres.wholphin.services.WatchlistUnavailable) {
                                            error.message
                                        } else {
                                            "Watchlist unavailable. Check the connection and backend API support, then retry."
                                        },
                                )
                        }
                    }
                }
        }

        fun open(entry: WatchlistEntry) = navigation.navigateTo(Destination.DiscoveredItem(entry.item))

        fun remove(entry: WatchlistEntry) = changeMembership(entry, false)

        fun changeMembership(
            entry: WatchlistEntry,
            member: Boolean,
        ) {
            val owner = state.value.owner
            viewModelScope.launch {
                try {
                    if (!withContext(WholphinDispatchers.IO) { repository.setMembership(entry.key, member, entry.title) } &&
                        state.value.owner == owner
                    ) {
                        state.value =
                            state.value.copy(
                                error = "Watchlist change was not confirmed. Membership was reconciled; retry when connected.",
                            )
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    if (state.value.owner == owner) {
                        state.value = state.value.copy(error = "Watchlist change could not be confirmed. No offline operation was queued.")
                    }
                }
            }
        }
    }
