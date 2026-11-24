package com.github.damontecres.wholphin.ui.watchlist

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistFilter
import com.github.damontecres.wholphin.data.model.WatchlistFilters
import com.github.damontecres.wholphin.data.model.watchlistFocusAfterRemoval
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.WatchlistRepository
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.random.Random

internal const val WATCHLIST_PAGE_SIZE = 20

private class WatchlistPageShifted : IllegalStateException()

internal data class WatchlistRandomTarget(
    val index: Int,
    val page: Int,
    val offset: Int,
)

internal fun watchlistRandomTarget(
    totalResults: Int,
    nextIndex: (Int) -> Int = { Random.nextInt(it) },
): WatchlistRandomTarget {
    require(totalResults > 0)
    val index = nextIndex(totalResults)
    require(index in 0 until totalResults)
    return WatchlistRandomTarget(index, index / WATCHLIST_PAGE_SIZE + 1, index % WATCHLIST_PAGE_SIZE)
}

data class WatchlistScreenState(
    val owner: String? = null,
    val category: WatchlistCategory = WatchlistCategory.ALL,
    val filters: WatchlistFilters = WatchlistFilters(),
    val entries: List<WatchlistEntry> = emptyList(),
    val firstPage: Int = 1,
    val page: Int = 0,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val failedPage: Int? = null,
    val focusedIndex: Int = 0,
    val focusedKey: MediaKey.Catalog? = null,
    val gridEpoch: Long = 0,
    val randomPending: Boolean = false,
    val restoreRandomFocus: Boolean = false,
    val failedRandomIndex: Int? = null,
    val viewOptions: ViewOptions = ViewOptions(),
)

internal fun watchlistViewOptions(value: String?): ViewOptions =
    runCatching { value?.let { Json.decodeFromString<ViewOptions>(it) } }
        .getOrNull()
        ?.let {
            ViewOptions(
                columns = it.columns.coerceIn(1, 12),
                spacing = it.spacing.coerceIn(0, 32),
                showBackdrop = it.showBackdrop,
                showTitles = it.showTitles,
            )
        }
        ?: ViewOptions()

@HiltViewModel
class WatchlistViewModel
    @Inject
    constructor(
        val repository: WatchlistRepository,
        private val navigation: NavigationManager,
        private val preferences: DataStore<AppPreferences>,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(WatchlistScreenState())
        private var load: Job? = null
        private var generation = 0L
        private var lastRefresh = 0L
        private var columns: Int? = null
        private var viewportRows: Int? = null

        suspend fun updateBackdrop(entry: WatchlistEntry?) {
            val url = entry?.item?.backDropUrl
            if (url.isNullOrBlank()) {
                backdropService.clearBackdrop()
            } else {
                backdropService.submit(entry.gridId, url)
            }
        }

        init {
            viewModelScope.launch {
                repository.state
                    .map { Triple(it.owner, it.revision, it.busy.isNotEmpty()) }
                    .distinctUntilChanged()
                    .collect { (owner, _, busy) ->
                        load?.cancel()
                        generation++
                        val previous = state.value
                        val ownerChanged = owner != previous.owner
                        // Hide the old user's rows and selections before reading the new context.
                        state.value =
                            if (ownerChanged) {
                                columns = null
                                viewportRows = null
                                WatchlistScreenState()
                            } else {
                                previous.copy(loading = false, randomPending = false, restoreRandomFocus = false)
                            }
                        if (owner == null) {
                            state.value =
                                state.value.copy(
                                    error = "Personal Watchlist requires a Jellyfin-linked Seerr login, not a shared API key.",
                                )
                        } else if (!busy) {
                            if (ownerChanged) {
                                val saved = preferences.data.first()
                                if (repository.state.value.owner != owner) return@collect
                                state.value =
                                    WatchlistScreenState(
                                        owner = owner,
                                        filters = WatchlistFilters.decode(saved.watchlistFiltersMap[owner]),
                                        viewOptions = watchlistViewOptions(saved.watchlistViewOptionsMap[owner]),
                                    )
                                columns = state.value.viewOptions.columns
                            }
                            refresh(force = true, eagerInitialFill = ownerChanged)
                        }
                    }
            }
        }

        fun category(category: WatchlistCategory) {
            if (category == state.value.category) return
            load?.cancel()
            generation++
            state.value =
                WatchlistScreenState(
                    owner = state.value.owner,
                    category = category,
                    filters = state.value.filters,
                    viewOptions = state.value.viewOptions,
                )
            refresh(force = true, eagerInitialFill = true)
        }

        fun owned(value: WatchlistFilter) = updateFilters { it.copy(owned = value) }

        fun watched(value: WatchlistFilter) = updateFilters { it.copy(watched = value) }

        private fun updateFilters(change: (WatchlistFilters) -> WatchlistFilters) {
            val owner = state.value.owner ?: return
            cancelRandom()
            viewModelScope.launch {
                try {
                    val saved =
                        preferences.updateData { current ->
                            val filters = change(WatchlistFilters.decode(current.watchlistFiltersMap[owner]))
                            current.toBuilder().putWatchlistFilters(owner, filters.encode()).build()
                        }
                    if (state.value.owner != owner || repository.state.value.owner != owner) return@launch
                    val filters = WatchlistFilters.decode(saved.watchlistFiltersMap[owner])
                    if (filters == state.value.filters) return@launch
                    load?.cancel()
                    generation++
                    state.value =
                        WatchlistScreenState(
                            owner = owner,
                            category = state.value.category,
                            filters = filters,
                            viewOptions = state.value.viewOptions,
                        )
                    refresh(force = true, eagerInitialFill = true)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    if (state.value.owner ==
                        owner
                    ) {
                        state.value = state.value.copy(error = "Could not save Watchlist filters. Retry the selection.")
                    }
                }
            }
        }

        fun refresh(
            force: Boolean = false,
            eagerInitialFill: Boolean = false,
        ) {
            if (state.value.owner == null || state.value.loading) return
            if (!force && System.currentTimeMillis() - lastRefresh < 60_000) return
            request(state.value.firstPage, force = true, eagerInitialFill = eagerInitialFill)
        }

        fun saveViewOptions(value: ViewOptions) {
            val owner = state.value.owner ?: return
            // Only the options actually rendered by Watchlist may be persisted here.
            val options =
                ViewOptions(
                    columns = value.columns.coerceIn(1, 12),
                    spacing = value.spacing.coerceIn(0, 32),
                    showBackdrop = value.showBackdrop,
                    showTitles = value.showTitles,
                )
            if (options == state.value.viewOptions) return
            state.value = state.value.copy(viewOptions = options)
            columns = options.columns
            prefetch()
            viewModelScope.launch {
                preferences.updateData { current ->
                    current.toBuilder().putWatchlistViewOptions(owner, Json.encodeToString(options)).build()
                }
            }
        }

        fun position(
            index: Int,
            columns: Int,
        ) {
            this.columns = columns.coerceAtLeast(1)
            val current = state.value
            val key = current.entries.getOrNull(index)?.key ?: return
            state.value = current.copy(focusedIndex = index, focusedKey = key)
            prefetch()
        }

        fun viewportRows(
            columns: Int,
            rows: Int,
        ) {
            this.columns = columns.coerceAtLeast(1)
            viewportRows = rows.coerceAtLeast(1)
            prefetch()
        }

        private fun prefetch() {
            val current = state.value
            if (current.loading || current.randomPending || current.error != null || current.entries.isEmpty()) return
            // Initial fill uses saved columns before layout; the grid replaces them with
            // measured columns once it reports its viewport. API pages remain fixed at 20.
            val columns = columns ?: return
            // Fill the visible grid even while the ribbon owns focus, then keep three
            // additional rows ahead. API pages remain fixed at 20 entries.
            val aheadCount = (current.focusedIndex + ((viewportRows ?: 0) + 3) * columns).coerceAtMost(current.totalResults)
            when {
                current.firstPage > 1 && current.focusedIndex < columns * 3 -> {
                    request(current.firstPage - 1, force = false)
                }

                current.page < current.totalPages &&
                    (current.entries.size < aheadCount || current.focusedIndex >= current.entries.size - columns * 3) -> {
                    request(current.page + 1, force = false)
                }
            }
        }

        fun retry() {
            if (state.value.owner == null || state.value.loading) return
            val failedRandomIndex = state.value.failedRandomIndex
            if (failedRandomIndex != null) {
                jumpToRandom(failedRandomIndex)
            } else {
                request(state.value.failedPage ?: 1, force = true)
            }
        }

        fun random() {
            val total = state.value.totalResults
            if (state.value.owner == null || total <= 0 || state.value.loading || state.value.randomPending) return
            jumpToRandom(watchlistRandomTarget(total).index)
        }

        fun cancelRandom() {
            if (!state.value.randomPending) return
            load?.cancel()
            generation++
            state.value = state.value.copy(loading = false, randomPending = false)
        }

        fun isWatchlistTop() = navigation.backStack.lastOrNull() == Destination.Watchlist

        fun consumeRandomReturnFocus() {
            state.value = state.value.copy(restoreRandomFocus = false)
        }

        internal fun jumpToRandom(initialIndex: Int) {
            if (state.value.owner == null || state.value.randomPending || state.value.totalResults <= 0) return
            load?.cancel()
            val token = ++generation
            val current = state.value
            val sessionRevision = repository.state.value.revision
            val stillCurrent = {
                token == generation && repository.state.value.owner == current.owner &&
                    repository.state.value.revision == sessionRevision &&
                    state.value.category == current.category && state.value.filters == current.filters
            }
            state.value = current.copy(loading = true, randomPending = true, error = null, failedPage = null, failedRandomIndex = null)
            load =
                viewModelScope.launch {
                    try {
                        var target =
                            if (initialIndex in 0 until current.totalResults) {
                                watchlistRandomTarget(current.totalResults) { initialIndex }
                            } else {
                                watchlistRandomTarget(current.totalResults)
                            }
                        var recounted: com.github.damontecres.wholphin.data.model.WatchlistPage? = null
                        var result =
                            try {
                                withContext(WholphinDispatchers.IO) {
                                    repository.randomIdentityPage(current.category, target.page, filters = current.filters)
                                }
                            } catch (_: IllegalArgumentException) {
                                // A formerly valid absolute page can disappear as the filtered list shrinks.
                                val fresh =
                                    withContext(WholphinDispatchers.IO) {
                                        repository.page(current.category, 1, refresh = true, filters = current.filters)
                                    }
                                recounted = fresh
                                fresh
                            }
                        if (!stillCurrent()) return@launch
                        if (result.page != target.page || result.totalResults != current.totalResults ||
                            target.offset !in result.entries.indices
                        ) {
                            // Offset pagination is not a snapshot. Re-count and reselect only once.
                            val fresh =
                                recounted ?: withContext(WholphinDispatchers.IO) {
                                    repository.page(current.category, 1, refresh = true, filters = current.filters)
                                }
                            if (!stillCurrent()) return@launch
                            if (fresh.totalResults == 0) {
                                state.value =
                                    current.copy(
                                        entries = emptyList(),
                                        firstPage = 1,
                                        page = 1,
                                        totalPages = 1,
                                        totalResults = 0,
                                        loading = false,
                                        randomPending = false,
                                        focusedIndex = 0,
                                        gridEpoch = current.gridEpoch + 1,
                                    )
                                return@launch
                            }
                            target = watchlistRandomTarget(fresh.totalResults)
                            result =
                                withContext(WholphinDispatchers.IO) {
                                    repository.randomIdentityPage(current.category, target.page, filters = current.filters)
                                }
                            if (!stillCurrent()) return@launch
                            if (result.totalResults != fresh.totalResults || target.offset !in result.entries.indices) {
                                throw WatchlistPageShifted()
                            }
                        }
                        checkPage(result, target.page)
                        if (!stillCurrent()) return@launch
                        val selected = result.entries[target.offset]
                        var navigated = false
                        navigation.navigateToSeerrItem(
                            selected.item,
                            canNavigate = stillCurrent,
                            onNavigated = {
                                navigated = true
                                if (stillCurrent()) state.value = state.value.copy(restoreRandomFocus = true)
                            },
                            onFinished = {
                                if (stillCurrent()) {
                                    state.value =
                                        state.value.copy(
                                            loading = false,
                                            randomPending = false,
                                            error = if (navigated) null else "Could not open Watchlist item. Retry Random.",
                                        )
                                }
                            },
                        )
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        if (stillCurrent()) {
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    randomPending = false,
                                    failedRandomIndex = initialIndex,
                                    error = "Watchlist unavailable. Check the connection and backend API support, then retry.",
                                )
                        }
                    }
                }
        }

        private fun request(
            page: Int,
            force: Boolean,
            eagerInitialFill: Boolean = false,
        ) {
            val token = ++generation
            val current = state.value
            state.value = current.copy(loading = true, error = null, failedPage = null)
            load =
                viewModelScope.launch {
                    try {
                        val result = withContext(WholphinDispatchers.IO) { repository.page(current.category, page, force, current.filters) }
                        if (token != generation || repository.state.value.owner != current.owner) return@launch
                        checkPage(result, page)
                        val refreshingWindow = force && page == current.firstPage && current.page > 0
                        val unchangedWindow =
                            refreshingWindow && current.totalResults == result.totalResults &&
                                current.entries.take(result.entries.size).map { it.key } == result.entries.map { it.key }
                        if (current.page > 0 && !refreshingWindow && result.totalResults != current.totalResults) {
                            throw WatchlistPageShifted()
                        }
                        val prepend = !refreshingWindow && current.page > 0 && page == current.firstPage - 1
                        val append = !refreshingWindow && current.page > 0 && page == current.page + 1
                        if ((prepend || append) && result.entries.any { row -> current.entries.any { it.key == row.key } }) {
                            throw WatchlistPageShifted()
                        }
                        val entries =
                            when {
                                unchangedWindow -> result.entries + current.entries.drop(result.entries.size)
                                prepend -> result.entries + current.entries
                                append -> current.entries + result.entries
                                else -> result.entries
                            }
                        val newFirstPage = if (unchangedWindow || append) current.firstPage else page
                        val newLastPage = if (unchangedWindow || prepend) current.page else page
                        // Focus can move while an adjacent server page is in flight.
                        val activeFocus = state.value
                        val focusedIndex =
                            activeFocus.focusedKey?.let { key -> entries.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
                                ?: when {
                                    prepend -> activeFocus.focusedIndex + result.entries.size
                                    unchangedWindow || append -> activeFocus.focusedIndex
                                    else -> watchlistFocusAfterRemoval(activeFocus.focusedIndex, entries.size)
                                }
                        state.value =
                            current.copy(
                                entries = entries,
                                firstPage = newFirstPage,
                                page = newLastPage,
                                totalPages = result.totalPages,
                                totalResults = result.totalResults,
                                loading = false,
                                error = null,
                                failedPage = null,
                                failedRandomIndex = null,
                                focusedIndex = focusedIndex,
                                focusedKey = entries.getOrNull(focusedIndex)?.key,
                                gridEpoch =
                                    if (refreshingWindow &&
                                        !unchangedWindow
                                    ) {
                                        current.gridEpoch + 1
                                    } else {
                                        current.gridEpoch
                                    },
                            )
                        lastRefresh = System.currentTimeMillis()
                        // Focus may have advanced while the page was in flight. Recheck once;
                        // the loading guard and new boundary keep requests sequential and bounded.
                        if (page > 1 || eagerInitialFill) prefetch()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        if (token ==
                            generation
                        ) {
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    failedPage = if (error is WatchlistPageShifted || error is IllegalArgumentException) 1 else page,
                                    error =
                                        if (error is WatchlistPageShifted || error is IllegalArgumentException) {
                                            "Watchlist changed while loading. Retry to refresh."
                                        } else if (error is com.github.damontecres.wholphin.services.WatchlistUnavailable) {
                                            error.message
                                        } else {
                                            "Watchlist unavailable. Check the connection and backend API support, then retry."
                                        },
                                )
                        }
                    }
                }
        }

        private fun checkPage(
            result: com.github.damontecres.wholphin.data.model.WatchlistPage,
            page: Int,
        ) {
            val expected = (result.totalResults - (page - 1) * WATCHLIST_PAGE_SIZE).coerceIn(0, WATCHLIST_PAGE_SIZE)
            if (result.entries.size != expected || result.entries
                    .map { it.key }
                    .toSet()
                    .size != result.entries.size
            ) {
                throw WatchlistPageShifted()
            }
        }

        fun open(entry: WatchlistEntry) {
            cancelRandom()
            val current = state.value
            val index = current.entries.indexOfFirst { it.key == entry.key }
            if (index >= 0) state.value = current.copy(focusedIndex = index, focusedKey = entry.key)
            navigation.navigateToSeerrItem(entry.item)
        }

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
