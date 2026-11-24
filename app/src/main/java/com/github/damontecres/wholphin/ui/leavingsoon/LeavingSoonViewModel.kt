package com.github.damontecres.wholphin.ui.leavingsoon

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.LEAVING_SOON_PAGE_SIZE
import com.github.damontecres.wholphin.services.LeavingSoonCategory
import com.github.damontecres.wholphin.services.LeavingSoonEntry
import com.github.damontecres.wholphin.services.LeavingSoonPageData
import com.github.damontecres.wholphin.services.LeavingSoonSession
import com.github.damontecres.wholphin.services.LeavingSoonSessionSource
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val MAX_WINDOW_PAGES = 5

private class LeavingSoonPageShifted : IllegalStateException()

internal data class LeavingSoonScreenState(
    val owner: String? = null,
    val category: LeavingSoonCategory = LeavingSoonCategory.ALL,
    val entries: List<LeavingSoonEntry> = emptyList(),
    val firstPage: Int = 1,
    val lastPage: Int = 0,
    val totalPages: Int = 0,
    val totalResults: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val failedPage: Int? = null,
    val focusedIndex: Int = 0,
    val focusedKey: String? = null,
    val gridEpoch: Long = 0,
    val viewOptions: ViewOptions = ViewOptions(),
)

internal fun leavingSoonViewOptions(value: String?): ViewOptions =
    runCatching { value?.let { Json.decodeFromString<ViewOptions>(it) } }
        .getOrNull()
        ?.let {
            ViewOptions(
                columns = it.columns.coerceIn(1, 12),
                spacing = it.spacing.coerceIn(0, 32),
                showTitles = it.showTitles,
            )
        } ?: ViewOptions()

internal fun validateLeavingSoonPage(
    result: LeavingSoonPageData,
    requestedPage: Int,
) {
    require(result.page == requestedPage && result.pageSize == LEAVING_SOON_PAGE_SIZE && result.totalResults >= 0)
    require(result.totalPages == (result.totalResults + LEAVING_SOON_PAGE_SIZE - 1) / LEAVING_SOON_PAGE_SIZE)
    val expected = (result.totalResults - (requestedPage - 1) * LEAVING_SOON_PAGE_SIZE).coerceIn(0, LEAVING_SOON_PAGE_SIZE)
    require(result.entries.size == expected)
    require(result.entries.all { it.canonicalMediaId > 0 })
    require(
        result.entries
            .map { it.key }
            .toSet()
            .size == result.entries.size,
    )
}

@HiltViewModel
internal class LeavingSoonViewModel
    @Inject
    constructor(
        source: LeavingSoonSessionSource,
        private val navigation: NavigationManager,
        private val preferences: DataStore<AppPreferences>,
    ) : ViewModel() {
        val state = MutableStateFlow(LeavingSoonScreenState())
        private var session: LeavingSoonSession? = null
        private var load: Job? = null
        private var generation = 0L
        private var columns = 6
        private var viewportRows: Int? = null
        private var lastRefresh = 0L
        private var shiftedOnce = false

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.epoch === next?.epoch && session?.owner == next?.owner) return@collect
                    load?.cancel()
                    generation++
                    session = next
                    state.value = LeavingSoonScreenState(owner = next?.owner)
                    lastRefresh = 0L
                    shiftedOnce = false
                    viewportRows = null
                    if (next != null) {
                        val saved = preferences.data.first()
                        if (session !== next || !next.isCurrent()) return@collect
                        val options = leavingSoonViewOptions(saved.leavingSoonViewOptionsMap[next.owner])
                        state.value = LeavingSoonScreenState(owner = next.owner, viewOptions = options)
                        columns = options.columns
                        request(1, eagerInitialFill = true)
                    }
                }
            }
        }

        fun isLeavingSoonTop() = navigation.backStack.lastOrNull() == Destination.LeavingSoon

        fun saveViewOptions(value: ViewOptions) {
            val owner = state.value.owner ?: return
            val options = leavingSoonViewOptions(Json.encodeToString(value))
            if (options == state.value.viewOptions) return
            state.value = state.value.copy(viewOptions = options)
            columns = options.columns
            prefetch()
            viewModelScope.launch {
                preferences.updateData { current ->
                    current.toBuilder().putLeavingSoonViewOptions(owner, Json.encodeToString(options)).build()
                }
            }
        }

        fun category(value: LeavingSoonCategory) {
            if (state.value.category == value) return
            load?.cancel()
            generation++
            shiftedOnce = false
            val before = state.value
            state.value =
                LeavingSoonScreenState(
                    owner = before.owner,
                    category = value,
                    gridEpoch = before.gridEpoch + 1,
                    viewOptions = before.viewOptions,
                )
            request(1, eagerInitialFill = true)
        }

        fun refresh() {
            val current = state.value
            if (current.owner != null && current.lastPage <= 1 && !current.loading &&
                System.currentTimeMillis() - lastRefresh >= 60_000
            ) {
                restartPageOne()
            }
        }

        fun retry() {
            val current = state.value
            if (current.owner == null || current.loading) return
            val page = current.failedPage ?: 1
            if (page == 1) restartPageOne() else request(page)
        }

        fun reload() {
            if (state.value.owner != null && !state.value.loading) restartPageOne()
        }

        private fun restartPageOne(resetShiftGuard: Boolean = true) {
            load?.cancel()
            generation++
            if (resetShiftGuard) shiftedOnce = false
            val current = state.value
            state.value =
                LeavingSoonScreenState(
                    owner = current.owner,
                    category = current.category,
                    focusedKey = current.focusedKey,
                    gridEpoch = current.gridEpoch + 1,
                    viewOptions = current.viewOptions,
                )
            request(1, eagerInitialFill = true)
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
            if (current.loading || current.error != null || current.entries.isEmpty()) return
            when {
                current.firstPage > 1 && current.focusedIndex < columns * 3 -> {
                    request(current.firstPage - 1)
                }

                current.lastPage < current.totalPages &&
                    (
                        current.entries.size <
                            (current.focusedIndex + ((viewportRows ?: 0) + 3) * columns).coerceAtMost(
                                current.totalResults,
                            ) ||
                            current.focusedIndex >= current.entries.size - columns * 3
                    ) -> {
                    request(current.lastPage + 1)
                }
            }
        }

        fun open(entry: LeavingSoonEntry) {
            if (!entry.hasTmdbIdentity || session?.isCurrent() != true) return
            val current = state.value
            val index = current.entries.indexOfFirst { it.key == entry.key }
            if (index < 0) return
            state.value = current.copy(focusedIndex = index, focusedKey = entry.key)
            navigation.navigateToSeerrItem(entry.item)
        }

        private fun request(
            page: Int,
            eagerInitialFill: Boolean = false,
        ) {
            val active = session ?: return
            if (!active.isCurrent()) return
            val before = state.value
            if (before.loading || page < 1 ||
                (before.lastPage > 0 && page != before.firstPage - 1 && page != before.lastPage + 1)
            ) {
                return
            }
            val token = ++generation
            load?.cancel()
            state.value = before.copy(loading = true, error = null, failedPage = null)
            load =
                viewModelScope.launch {
                    try {
                        val result = active.backend.page(before.category, page)
                        if (token != generation || !active.isCurrent()) return@launch
                        validateLeavingSoonPage(result, page)
                        if (before.lastPage > 0 && result.totalResults != before.totalResults) throw LeavingSoonPageShifted()
                        val prepend = before.lastPage > 0 && page == before.firstPage - 1
                        val append = before.lastPage > 0 && page == before.lastPage + 1
                        if ((prepend || append) && result.entries.any { incoming -> before.entries.any { it.key == incoming.key } }) {
                            throw LeavingSoonPageShifted()
                        }
                        var entries =
                            when {
                                prepend -> result.entries + before.entries
                                append -> before.entries + result.entries
                                else -> result.entries
                            }
                        var firstPage = if (prepend) page else before.firstPage
                        var lastPage =
                            if (append) {
                                page
                            } else if (prepend) {
                                before.lastPage
                            } else {
                                page
                            }
                        if (lastPage - firstPage + 1 > MAX_WINDOW_PAGES) {
                            if (prepend) {
                                val lastSize =
                                    (result.totalResults - (lastPage - 1) * LEAVING_SOON_PAGE_SIZE)
                                        .coerceIn(0, LEAVING_SOON_PAGE_SIZE)
                                entries = entries.dropLast(lastSize)
                                lastPage--
                            } else {
                                entries = entries.drop(LEAVING_SOON_PAGE_SIZE)
                                firstPage++
                            }
                        }
                        val activeFocus = state.value
                        val focusedIndex = entries.indexOfFirst { it.key == activeFocus.focusedKey }.takeIf { it >= 0 } ?: 0
                        state.value =
                            before.copy(
                                entries = entries,
                                firstPage = firstPage,
                                lastPage = lastPage,
                                totalPages = result.totalPages,
                                totalResults = result.totalResults,
                                loading = false,
                                error = null,
                                focusedIndex = focusedIndex,
                                focusedKey = entries.getOrNull(focusedIndex)?.key,
                                gridEpoch = if (before.lastPage == 0) before.gridEpoch + 1 else before.gridEpoch,
                            )
                        lastRefresh = System.currentTimeMillis()
                        if (page > 1 || prepend || eagerInitialFill) prefetch()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        if (token == generation && active.isCurrent()) {
                            val shifted = error is LeavingSoonPageShifted || error is IllegalArgumentException
                            if (shifted && !shiftedOnce) {
                                shiftedOnce = true
                                restartPageOne(resetShiftGuard = false)
                                return@launch
                            }
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    error =
                                        if (shifted) {
                                            "Leaving Soon changed while loading. Retry to refresh."
                                        } else {
                                            "Leaving Soon is unavailable. Retry when connected."
                                        },
                                    failedPage = if (shifted) 1 else page,
                                )
                        }
                    }
                }
        }
    }
