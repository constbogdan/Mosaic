package com.github.damontecres.wholphin.ui.fresh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.FreshStatus
import com.github.damontecres.wholphin.services.FRESH_PAGE_SIZE
import com.github.damontecres.wholphin.services.FreshCategory
import com.github.damontecres.wholphin.services.FreshEntry
import com.github.damontecres.wholphin.services.FreshPageData
import com.github.damontecres.wholphin.services.FreshSession
import com.github.damontecres.wholphin.services.FreshSessionSource
import com.github.damontecres.wholphin.services.FreshSort
import com.github.damontecres.wholphin.services.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FreshScreenState(
    val owner: String? = null,
    val category: FreshCategory = FreshCategory.ALL,
    val sort: FreshSort = FreshSort.FRESHEST,
    val entries: List<FreshEntry> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val totalResults: Int = 0,
    val status: FreshStatus.Status? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val failedPage: Int? = null,
    val focusedIndex: Int = 0,
    val focusedKey: String? = null,
    val gridEpoch: Long = 0,
)

private class FreshPageShifted : IllegalStateException()

internal fun validateFreshPage(
    result: FreshPageData,
    requestedPage: Int,
) {
    require(result.page == requestedPage && result.totalResults >= 0 && result.totalPages >= 0)
    if (result.status == FreshStatus.Status.DISABLED || result.status == FreshStatus.Status.UNAVAILABLE) {
        require(result.entries.isEmpty())
        return
    }
    val expected = (result.totalResults - (requestedPage - 1) * FRESH_PAGE_SIZE).coerceIn(0, FRESH_PAGE_SIZE)
    require(
        result.entries.size == expected && result.entries
            .map { it.key }
            .toSet()
            .size == result.entries.size,
    )
}

@HiltViewModel
internal class FreshViewModel
    @Inject
    constructor(
        source: FreshSessionSource,
        private val navigation: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(FreshScreenState())
        private var session: FreshSession? = null
        private var load: Job? = null
        private var generation = 0L
        private var columns = 6
        private var lastRefresh = 0L
        private var shiftedOnce = false

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.epoch === next?.epoch && session?.owner == next?.owner) return@collect
                    load?.cancel()
                    generation++
                    session = next
                    state.value = FreshScreenState(owner = next?.owner)
                    lastRefresh = 0
                    shiftedOnce = false
                    if (next != null) request(1)
                }
            }
        }

        fun category(value: FreshCategory) {
            if (value == state.value.category) return
            reset(state.value.copy(category = value))
        }

        fun sort(value: FreshSort) {
            if (value == state.value.sort) return
            reset(state.value.copy(sort = value))
        }

        private fun reset(current: FreshScreenState) {
            load?.cancel()
            generation++
            shiftedOnce = false
            state.value =
                FreshScreenState(owner = current.owner, category = current.category, sort = current.sort, gridEpoch = current.gridEpoch + 1)
            request(1)
        }

        fun refresh() {
            // Replacing a multi-page window on return from details would discard the originating card.
            if (state.value.owner != null && state.value.page <= 1 && !state.value.loading &&
                System.currentTimeMillis() - lastRefresh >= 60_000
            ) {
                shiftedOnce = false
                request(1)
            }
        }

        fun retry() {
            if (state.value.owner == null || state.value.loading) return
            val failedPage = state.value.failedPage ?: 1
            if (failedPage == 1) {
                shiftedOnce = false
                restartPageOne()
            } else {
                request(failedPage)
            }
        }

        private fun restartPageOne() {
            val current = state.value
            state.value =
                current.copy(
                    entries = emptyList(),
                    page = 0,
                    totalPages = 0,
                    totalResults = 0,
                    status = null,
                    loading = false,
                    error = null,
                    failedPage = null,
                    focusedIndex = 0,
                    gridEpoch = current.gridEpoch + 1,
                )
            // Retain the typed focusedKey until the new page can re-anchor it, or fall back to its first card.
            request(1)
        }

        fun position(
            index: Int,
            columns: Int,
        ) {
            this.columns = columns.coerceAtLeast(1)
            val current = state.value
            val key = current.entries.getOrNull(index)?.key ?: return
            state.value = current.copy(focusedIndex = index, focusedKey = key)
            if (!current.loading && current.error == null && current.page < current.totalPages &&
                index >= current.entries.size - this.columns * 3
            ) {
                request(current.page + 1)
            }
        }

        fun open(entry: FreshEntry) {
            val current = state.value
            val index = current.entries.indexOfFirst { it.key == entry.key }
            if (index >= 0) state.value = current.copy(focusedIndex = index, focusedKey = entry.key)
            navigation.navigateTo(entry.item.destination)
        }

        private fun request(page: Int) {
            val active = session ?: return
            if (!active.isCurrent()) return
            val token = ++generation
            val before = state.value
            load?.cancel()
            state.value = before.copy(loading = true, error = null, failedPage = null)
            load =
                viewModelScope.launch {
                    try {
                        val result = active.backend.page(before.category, before.sort, page)
                        if (token != generation || !active.isCurrent()) return@launch
                        validateFreshPage(result, page)
                        if (page > 1 && before.totalResults != result.totalResults) throw FreshPageShifted()
                        val append = page > 1 && page == before.page + 1
                        val entries = if (append) before.entries + result.entries else result.entries
                        if (entries.map { it.key }.toSet().size != entries.size) throw FreshPageShifted()
                        val activeFocus = state.value
                        val focusedIndex =
                            entries.indexOfFirst { it.key == activeFocus.focusedKey }.takeIf { it >= 0 }
                                ?: if (append) activeFocus.focusedIndex else 0
                        state.value =
                            before.copy(
                                entries = entries,
                                page = result.page,
                                totalPages = result.totalPages,
                                totalResults = result.totalResults,
                                status = result.status,
                                loading = false,
                                error = null,
                                focusedIndex = focusedIndex,
                                focusedKey = entries.getOrNull(focusedIndex)?.key,
                                gridEpoch = if (append) before.gridEpoch else before.gridEpoch + 1,
                            )
                        lastRefresh = System.currentTimeMillis()
                        if (append) position(focusedIndex, columns)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        if (token == generation && active.isCurrent()) {
                            val shifted = error is FreshPageShifted || (page > 1 && error is IllegalArgumentException)
                            if (shifted && !shiftedOnce) {
                                shiftedOnce = true
                                restartPageOne()
                                return@launch
                            }
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    error =
                                        if (shifted) {
                                            "Fresh changed while loading. Retry to refresh."
                                        } else if (page > 1) {
                                            "Fresh page unavailable. Retry to continue browsing."
                                        } else {
                                            "Fresh is unavailable. Retry when connected."
                                        },
                                    failedPage = if (shifted) 1 else page,
                                )
                        }
                    }
                }
        }
    }
