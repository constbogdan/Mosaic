package com.github.damontecres.wholphin.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.services.COLLECTION_PAGE_SIZE
import com.github.damontecres.wholphin.services.CollectionsEntry
import com.github.damontecres.wholphin.services.CollectionsFilter
import com.github.damontecres.wholphin.services.CollectionsPageData
import com.github.damontecres.wholphin.services.CollectionsSession
import com.github.damontecres.wholphin.services.CollectionsSessionSource
import com.github.damontecres.wholphin.services.CollectionsSort
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class CollectionsScreenState(
    val owner: String? = null,
    val filter: CollectionsFilter = CollectionsFilter.ALL,
    val sort: CollectionsSort = CollectionsSort.NAME_ASC,
    val entries: List<CollectionsEntry> = emptyList(),
    val total: Int = 0,
    val status: LibraryCollectionsResponse.Status? = null,
    val stale: Boolean = false,
    val failedCollections: Int = 0,
    val loading: Boolean = false,
    val denied: Boolean = false,
    val error: String? = null,
    val failedSkip: Int? = null,
    val focusedKey: String? = null,
    val focusedIndex: Int = 0,
    val gridEpoch: Long = 0,
)

internal fun validateCollectionsPage(
    result: CollectionsPageData,
    skip: Int,
) {
    require(result.skip == skip && result.pageSize == COLLECTION_PAGE_SIZE && result.total >= 0)
    require(
        result.entries.size <= COLLECTION_PAGE_SIZE && result.entries
            .map { it.gridId }
            .distinct()
            .size == result.entries.size,
    )
    require(result.entries.all { it.summary.id > 0 && it.summary.availableMovies?.let { count -> count >= 0 } != false })
    require(result.entries.all { it.summary.totalMovies?.let { count -> count >= 0 } != false })
    if (result.status == LibraryCollectionsResponse.Status.COMPLETE) {
        require(result.entries.size == (result.total - skip).coerceIn(0, COLLECTION_PAGE_SIZE))
    }
}

@HiltViewModel
internal class CollectionsViewModel
    @Inject
    constructor(
        source: CollectionsSessionSource,
        private val navigation: NavigationManager,
    ) : ViewModel() {
        val state = MutableStateFlow(CollectionsScreenState())
        private var session: CollectionsSession? = null
        private var load: Job? = null
        private var generation = 0L
        private var columns = 6
        private var pendingPolls = 0

        init {
            viewModelScope.launch {
                source.sessions.collect { next ->
                    if (session?.owner == next?.owner && session?.epoch === next?.epoch) return@collect
                    load?.cancel()
                    generation++
                    session = next
                    pendingPolls = 0
                    state.value = CollectionsScreenState(owner = next?.owner)
                    if (next != null) request(0)
                }
            }
        }

        fun filter(value: CollectionsFilter) {
            if (state.value.filter != value) reset(state.value.copy(filter = value))
        }

        fun sort(value: CollectionsSort) {
            if (state.value.sort != value) reset(state.value.copy(sort = value))
        }

        private fun reset(before: CollectionsScreenState) {
            load?.cancel()
            generation++
            pendingPolls = 0
            state.value =
                CollectionsScreenState(
                    owner = before.owner,
                    filter = before.filter,
                    sort = before.sort,
                    gridEpoch = before.gridEpoch + 1,
                )
            request(0)
        }

        fun retry() {
            val current = state.value
            if (current.owner != null && !current.loading) {
                pendingPolls = 0
                request(current.failedSkip ?: 0)
            }
        }

        fun position(
            index: Int,
            countColumns: Int,
        ) {
            columns = countColumns.coerceAtLeast(1)
            val current = state.value
            val key = current.entries.getOrNull(index)?.gridId ?: return
            state.value = current.copy(focusedKey = key, focusedIndex = index)
            if (!current.loading && current.error == null && !current.denied && current.entries.size < current.total &&
                index >= current.entries.size - columns * 3
            ) {
                request(current.entries.size)
            }
        }

        fun open(entry: CollectionsEntry) {
            val current = state.value
            if (current.owner == null || session?.isCurrent() != true) return
            val index = current.entries.indexOfFirst { it.gridId == entry.gridId }
            if (index >= 0) state.value = current.copy(focusedKey = entry.gridId, focusedIndex = index)
            navigation.navigateTo(
                Destination.SeerrCollection(
                    entry.summary.id,
                    entry.summary.availableMovies,
                    entry.summary.totalMovies,
                    current.owner,
                ),
            )
        }

        private fun request(skip: Int) {
            val active = session ?: return
            if (!active.isCurrent()) return
            val token = ++generation
            val before = state.value
            load?.cancel()
            state.value = before.copy(loading = true, error = null, failedSkip = null)
            load =
                viewModelScope.launch {
                    try {
                        val result = active.backend.page(skip, before.filter, before.sort)
                        if (token != generation || !active.isCurrent()) return@launch
                        validateCollectionsPage(result, skip)
                        if (skip > 0 && result.total != before.total) {
                            reset(before)
                            return@launch
                        }
                        val entries = if (skip == 0) result.entries else before.entries + result.entries
                        require(entries.map { it.gridId }.distinct().size == entries.size)
                        val focused = state.value.focusedKey
                        val index = entries.indexOfFirst { it.gridId == focused }.takeIf { it >= 0 } ?: 0
                        state.value =
                            before.copy(
                                entries = entries,
                                total = result.total,
                                status = result.status,
                                stale = result.stale,
                                failedCollections = result.failedCollections,
                                loading = false,
                                denied = false,
                                error = null,
                                failedSkip = null,
                                focusedKey = entries.getOrNull(index)?.gridId,
                                focusedIndex = index,
                                gridEpoch = if (skip == 0) before.gridEpoch + 1 else before.gridEpoch,
                            )
                        if (result.status == LibraryCollectionsResponse.Status.PENDING && entries.isEmpty() && pendingPolls++ < 5) {
                            delay(2_000)
                            if (token == generation && active.isCurrent()) request(0)
                        } else if (result.status == LibraryCollectionsResponse.Status.PENDING && entries.isEmpty()) {
                            state.value = state.value.copy(error = "Collections are preparing. Retry to check again.", failedSkip = 0)
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: ClientException) {
                        if (token == generation && active.isCurrent()) {
                            state.value =
                                state.value.copy(
                                    entries = if (error.statusCode == 403) emptyList() else state.value.entries,
                                    loading = false,
                                    denied = error.statusCode == 403,
                                    error = if (error.statusCode == 403) null else "Collections are unavailable. Retry when connected.",
                                    failedSkip = skip,
                                )
                        }
                    } catch (error: Exception) {
                        if (token == generation && active.isCurrent()) {
                            state.value =
                                state.value.copy(
                                    loading = false,
                                    error = "Collections are unavailable. Retry when connected.",
                                    failedSkip = skip,
                                )
                        }
                    }
                }
        }
    }
