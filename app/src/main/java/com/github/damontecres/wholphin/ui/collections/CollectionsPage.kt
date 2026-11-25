package com.github.damontecres.wholphin.ui.collections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.CollectionsEntry
import com.github.damontecres.wholphin.services.CollectionsFilter
import com.github.damontecres.wholphin.services.CollectionsSort
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptionsDialog
import com.github.damontecres.wholphin.ui.components.rememberCustomPageDetailsReturnEpoch
import com.github.damontecres.wholphin.ui.components.rememberCustomPageFocusOwner
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.nav.LocalActiveDrawerFocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus

private val collectionsPresentationOptions =
    listOf<AppPreference<ViewOptions, out Any?>>(
        ViewOptions.ViewOptionsColumns,
        ViewOptions.ViewOptionsSpacing,
        ViewOptions.ViewOptionsShowTitles,
        ViewOptions.ViewOptionsBackdrop,
        ViewOptions.ViewOptionsReset,
    )

private fun CollectionsFilter.icon() =
    when (this) {
        CollectionsFilter.ALL -> R.string.fa_list_ul
        CollectionsFilter.INCOMPLETE -> R.string.fa_filter
        CollectionsFilter.COMPLETE -> R.string.fa_check
    }

@Composable
internal fun CollectionsPage(
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    var ribbonVisible by remember(state.owner) { mutableStateOf(false) }
    var ribbonHasFocus by remember(state.owner) { mutableStateOf(false) }
    var requestFilterFocus by remember(state.owner) { mutableStateOf(false) }
    var showViewOptions by remember(state.owner) { mutableStateOf(false) }
    var restoreViewOptionsFocus by remember(state.owner) { mutableStateOf(false) }
    val filterFocusRequesters = remember(state.owner) { CollectionsFilter.entries.associateWith { FocusRequester() } }
    val gridFocusRequester = remember(state.owner, state.filter, state.sort) { FocusRequester() }
    val drawerFocusRequester = LocalActiveDrawerFocusRequester.current
    var gridReady by remember(state.owner, state.filter, state.sort, state.gridEpoch, state.viewOptions.columns) {
        mutableStateOf(false)
    }
    val isTop = viewModel.isCollectionsTop()
    val restoreFocusEpoch =
        rememberCustomPageDetailsReturnEpoch(
            isTop = isTop,
            focusAnchorKey = state.focusedKey,
            ribbonHasFocus = ribbonHasFocus,
            scopeKey = Triple(state.owner, state.filter, state.sort),
        )
    val pageFocusModifier =
        rememberCustomPageFocusOwner(
            isTop = isTop,
            contentReady = gridReady && state.entries.isNotEmpty(),
            mayFocusGrid = !ribbonVisible && !showViewOptions,
            gridFocusRequester = gridFocusRequester,
            ribbonVisible = ribbonVisible && !showViewOptions,
            ribbonFocusRequester = filterFocusRequesters[state.filter],
        )
    val viewOptionsFocusRequester = remember(state.owner) { FocusRequester() }
    val retryFocusRequester = remember(state.owner) { FocusRequester() }
    val focused = state.entries.getOrNull(state.focusedIndex)
    LaunchedEffect(isTop, state.viewOptions.showBackdrop, focused?.gridId, focused?.backdropUrl) {
        if (isTop) viewModel.updateBackdrop(focused.takeIf { state.viewOptions.showBackdrop })
    }
    LaunchedEffect(state.owner, state.loading, state.entries.isEmpty(), state.status, state.error) {
        // An uninitialized or PENDING result is transient. Revealing and focusing
        // the ribbon here makes the first populated grid collapse under focus.
        if (state.entries.isEmpty() && !state.loading && (state.owner != null || state.error != null) &&
            (
                state.error != null || state.denied ||
                    (state.status != null && state.status != LibraryCollectionsResponse.Status.PENDING)
            )
        ) {
            ribbonVisible = true
            if (state.error != null && state.owner != null && !ribbonHasFocus) {
                withFrameNanos { }
                retryFocusRequester.tryRequestFocus()
            } else if (state.error == null && !ribbonHasFocus) {
                requestFilterFocus = true
            }
        }
    }
    LaunchedEffect(ribbonVisible, requestFilterFocus, state.filter) {
        if (ribbonVisible && requestFilterFocus) {
            withFrameNanos { }
            if (filterFocusRequesters[state.filter]?.tryRequestFocus() == true) requestFilterFocus = false
        }
    }
    LaunchedEffect(restoreViewOptionsFocus, showViewOptions) {
        if (restoreViewOptionsFocus && !showViewOptions) {
            withFrameNanos { }
            if (viewOptionsFocusRequester.tryRequestFocus()) restoreViewOptionsFocus = false
        }
    }
    Column(modifier.fillMaxSize().then(pageFocusModifier)) {
        AnimatedVisibility(visible = ribbonVisible, enter = expandVertically(), exit = shrinkVertically()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 40.dp, bottom = 4.dp)
                        .onFocusChanged { ribbonHasFocus = it.hasFocus }
                        .focusGroup()
                        .onPreviewKeyEvent {
                            it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown &&
                                state.entries.isNotEmpty() && gridFocusRequester.tryRequestFocus()
                        },
            ) {
                CollectionsFilter.entries.forEach { filter ->
                    val description = stringResource(collectionsFilterTitle(filter))
                    ExpandableFaButton(
                        title = collectionsFilterTitle(filter),
                        iconStringRes = filter.icon(),
                        iconColor = if (state.filter == filter) AppColors.Discover.Blue else Color.Unspecified,
                        onClick = { viewModel.filter(filter) },
                        enabled = state.owner != null,
                        modifier =
                            Modifier.focusRequester(filterFocusRequesters.getValue(filter)).semantics {
                                selected = state.filter == filter
                                contentDescription = description
                            },
                    )
                }
                Spacer(Modifier.weight(1f))
                val optionsDescription = stringResource(R.string.view_options)
                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = { showViewOptions = true },
                    enabled = state.owner != null,
                    modifier = Modifier.focusRequester(viewOptionsFocusRequester).semantics { contentDescription = optionsDescription },
                )
                val sortTitle = collectionsSortTitle(state.sort)
                val sortLabel = stringResource(sortTitle)
                ExpandableFaButton(
                    title = sortTitle,
                    iconStringRes = R.string.fa_clock,
                    onClick = { viewModel.sort(CollectionsSort.entries[(state.sort.ordinal + 1) % CollectionsSort.entries.size]) },
                    enabled = state.owner != null,
                    modifier = Modifier.semantics { contentDescription = "Sort: $sortLabel" },
                )
            }
        }
        if (state.status == LibraryCollectionsResponse.Status.PARTIAL || state.failedCollections > 0) {
            Text(stringResource(R.string.collections_partial), Modifier.padding(8.dp))
        }
        if (state.error != null) {
            Text(state.error, Modifier.padding(16.dp))
            Button(
                onClick = viewModel::retry,
                enabled = state.owner != null && !state.loading,
                modifier = Modifier.focusRequester(retryFocusRequester),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
        if (state.loading && state.entries.isEmpty()) {
            LoadingPage(Modifier.weight(1f), focusEnabled = false)
        } else if (state.entries.isEmpty()) {
            val text =
                when {
                    state.owner == null -> R.string.collections_connection_required
                    state.denied -> R.string.collections_permission_required
                    state.status == LibraryCollectionsResponse.Status.PENDING -> R.string.collections_preparing
                    state.status == LibraryCollectionsResponse.Status.UNAVAILABLE -> R.string.collections_unavailable
                    else -> R.string.collections_empty
                }
            Text(stringResource(text), Modifier.padding(16.dp))
        } else {
            key(state.owner, state.filter, state.sort, state.gridEpoch, state.viewOptions.columns) {
                CardGrid(
                    pager = state.entries,
                    totalItems = state.total,
                    focusAnchorKey = state.focusedKey,
                    focusRequestEpoch = restoreFocusEpoch,
                    onGridFocusChanged = { focused ->
                        if (focused) ribbonVisible = false
                    },
                    onViewportRowsChanged = viewModel::viewportRows,
                    onFirstRowUp = {
                        ribbonVisible = true
                        requestFilterFocus = true
                        true
                    },
                    useBackDispatcherFallback = isTop,
                    itemKey = { state.entries[it].gridId },
                    onClickItem = { _, item -> viewModel.open(item) },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocusRequester,
                    leftmostItemFocusRequester = drawerFocusRequester,
                    onFocusTargetReady = { gridReady = true },
                    initialPosition = state.focusedIndex,
                    positionCallback = { columns, index -> viewModel.position(index, columns) },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    columns = state.viewOptions.columns,
                    spacing = state.viewOptions.spacing.dp,
                    modifier = Modifier.weight(1f),
                    cardContent = { (item, _, onClick, onLongClick, _, cardModifier) ->
                        CollectionsCard(item, onClick, onLongClick, cardModifier, state.viewOptions.showTitles)
                    },
                )
            }
        }
    }
    if (showViewOptions) {
        ViewOptionsDialog(
            viewOptions = state.viewOptions,
            defaultViewOptions = ViewOptions(),
            optionsOverride = collectionsPresentationOptions,
            onViewOptionsChange = viewModel::saveViewOptions,
            onDismissRequest = {
                showViewOptions = false
                restoreViewOptionsFocus = true
            },
        )
    }
}

private fun collectionsFilterTitle(filter: CollectionsFilter): Int =
    when (filter) {
        CollectionsFilter.ALL -> R.string.collections_all
        CollectionsFilter.INCOMPLETE -> R.string.collections_incomplete
        CollectionsFilter.COMPLETE -> R.string.collections_complete
    }

private fun collectionsSortTitle(sort: CollectionsSort): Int =
    when (sort) {
        CollectionsSort.NAME_ASC -> R.string.collections_name_asc
        CollectionsSort.NAME_DESC -> R.string.collections_name_desc
        CollectionsSort.COMPLETION_ASC -> R.string.collections_completion_asc
        CollectionsSort.COMPLETION_DESC -> R.string.collections_completion_desc
        CollectionsSort.AVAILABLE_ASC -> R.string.collections_available_asc
        CollectionsSort.AVAILABLE_DESC -> R.string.collections_available_desc
    }

@Composable
private fun CollectionsCard(
    item: CollectionsEntry?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    showTitles: Boolean,
) {
    CollectionCard(
        name = item?.summary?.name.orEmpty(),
        posterUrl = item?.posterUrl,
        available = item?.summary?.availableMovies,
        total = item?.summary?.totalMovies,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        showTitles = showTitles,
    )
}

@Composable
internal fun CollectionCard(
    name: String,
    posterUrl: String?,
    available: Int?,
    total: Int?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    showTitles: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().then(cardModifier),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
        ) {
            Box(Modifier.fillMaxWidth()) {
                ItemCardImage(
                    imageUrl = posterUrl,
                    name = name,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    numberOfVersions = -1,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxWidth().aspectRatio(AspectRatios.TALL),
                )
                CollectionCompletionBadge(available, total, Modifier.align(Alignment.TopEnd))
            }
        }
        if (showTitles) {
            Text(
                name,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

@Composable
internal fun CollectionCompletionBadge(
    available: Int?,
    total: Int?,
    modifier: Modifier = Modifier,
) {
    val counts = collectionBadgeCounts(available, total) ?: return
    val count = stringResource(R.string.collections_count, counts.available, counts.total)
    Text(
        text = count,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier =
            modifier
                .padding(6.dp)
                .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                .padding(5.dp),
    )
}

internal data class CollectionBadgeCounts(
    val available: Int,
    val total: Int,
)

internal fun collectionBadgeCounts(
    available: Int?,
    total: Int?,
): CollectionBadgeCounts? =
    if (available != null && total != null && total > 0 && available in 1..total) {
        CollectionBadgeCounts(available, total)
    } else {
        null
    }

internal fun collectionDetailCounts(
    detailAvailable: Int?,
    detailTotal: Int?,
    navigationAvailable: Int?,
    navigationTotal: Int?,
): CollectionBadgeCounts? =
    // A valid authoritative zero is known and suppresses a stale positive navigation badge.
    collectionKnownCounts(detailAvailable, detailTotal)
        ?: collectionKnownCounts(navigationAvailable, navigationTotal)

private fun collectionKnownCounts(
    available: Int?,
    total: Int?,
): CollectionBadgeCounts? =
    if (available != null && total != null && total >= 0 && available in 0..total) {
        CollectionBadgeCounts(available, total)
    } else {
        null
    }
