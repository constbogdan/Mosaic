package com.github.damontecres.wholphin.ui.fresh

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.FreshStatus
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.FreshCategory
import com.github.damontecres.wholphin.services.FreshSort
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptionsDialog
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.tryRequestFocus

private val freshPresentationOptions =
    listOf<AppPreference<ViewOptions, out Any?>>(
        ViewOptions.ViewOptionsColumns,
        ViewOptions.ViewOptionsSpacing,
        ViewOptions.ViewOptionsShowTitles,
        ViewOptions.ViewOptionsBackdrop,
        ViewOptions.ViewOptionsReset,
    )

private fun FreshCategory.icon() =
    when (this) {
        FreshCategory.ALL -> R.string.fa_list_ul
        FreshCategory.MOVIES -> R.string.fa_film
        FreshCategory.SHOWS -> R.string.fa_tv
        FreshCategory.ANIMATION -> R.string.fa_images
        FreshCategory.DOCUMENTARY -> R.string.fa_file_video
    }

private fun FreshCategory.title() =
    when (this) {
        FreshCategory.ALL -> R.string.fresh_all
        FreshCategory.MOVIES -> R.string.fresh_movies
        FreshCategory.SHOWS -> R.string.fresh_shows
        FreshCategory.ANIMATION -> R.string.fresh_animation
        FreshCategory.DOCUMENTARY -> R.string.fresh_documentary
    }

@Composable
internal fun FreshPage(
    modifier: Modifier = Modifier,
    viewModel: FreshViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val quickMovieRequest = LocalQuickMovieRequest.current
    var ribbonVisible by remember(state.owner) { mutableStateOf(false) }
    var ribbonHasFocus by remember(state.owner) { mutableStateOf(false) }
    var gridHasFocus by remember(state.owner, state.category, state.sort) { mutableStateOf(false) }
    var requestCategoryFocus by remember(state.owner) { mutableStateOf(false) }
    var showViewOptions by remember(state.owner) { mutableStateOf(false) }
    var restoreViewOptionsFocus by remember(state.owner) { mutableStateOf(false) }
    var backToTopEpoch by remember(state.owner, state.category, state.sort, state.gridEpoch, state.viewOptions.columns) {
        mutableLongStateOf(0L)
    }
    val categoryFocusRequesters = remember(state.owner) { FreshCategory.entries.associateWith { FocusRequester() } }
    val gridFocusRequester = remember(state.owner, state.category, state.sort) { FocusRequester() }
    val viewOptionsFocusRequester = remember(state.owner) { FocusRequester() }
    val retryFocusRequester = remember(state.owner) { FocusRequester() }
    val isTop = viewModel.isFreshTop()
    val focused = state.entries.getOrNull(state.focusedIndex)
    LaunchedEffect(isTop, state.viewOptions.showBackdrop, focused?.key, focused?.item?.backDropUrl) {
        if (isTop) viewModel.updateBackdrop(focused.takeIf { state.viewOptions.showBackdrop })
    }
    BackHandler(enabled = isTop && gridHasFocus && state.entries.isNotEmpty() && state.focusedIndex >= state.viewOptions.columns) {
        ribbonVisible = true
        backToTopEpoch++
    }
    LaunchedEffect(state.owner, state.loading, state.entries.isEmpty(), state.error) {
        if (state.entries.isEmpty() && !state.loading && (state.owner != null || state.error != null)) {
            ribbonVisible = true
            if (state.error != null && state.owner != null && !ribbonHasFocus) {
                withFrameNanos { }
                retryFocusRequester.tryRequestFocus()
            } else if (state.error == null && !ribbonHasFocus) {
                requestCategoryFocus = true
            }
        }
    }
    LaunchedEffect(ribbonVisible, requestCategoryFocus, state.category) {
        if (ribbonVisible && requestCategoryFocus) {
            withFrameNanos { }
            if (categoryFocusRequesters[state.category]?.tryRequestFocus() == true) requestCategoryFocus = false
        }
    }
    LaunchedEffect(restoreViewOptionsFocus, showViewOptions) {
        if (restoreViewOptionsFocus && !showViewOptions) {
            withFrameNanos { }
            if (viewOptionsFocusRequester.tryRequestFocus()) restoreViewOptionsFocus = false
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Column(modifier.fillMaxSize()) {
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
                FreshCategory.entries.forEach { category ->
                    val description = stringResource(category.title())
                    ExpandableFaButton(
                        title = category.title(),
                        iconStringRes = category.icon(),
                        iconColor = if (state.category == category) AppColors.Discover.Blue else Color.Unspecified,
                        onClick = { viewModel.category(category) },
                        enabled = state.owner != null,
                        modifier =
                            Modifier
                                .focusRequester(categoryFocusRequesters.getValue(category))
                                .semantics {
                                    selected = state.category == category
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
                val sortTitle =
                    when (state.sort) {
                        FreshSort.FRESHEST -> R.string.fresh_sort_freshest
                        FreshSort.OLDEST -> R.string.fresh_sort_oldest
                        FreshSort.RATING -> R.string.fresh_sort_rating
                    }
                val sortDescription = stringResource(sortTitle)
                ExpandableFaButton(
                    title = sortTitle,
                    iconStringRes = R.string.fa_clock,
                    onClick = { viewModel.sort(FreshSort.entries[(state.sort.ordinal + 1) % FreshSort.entries.size]) },
                    enabled = state.owner != null,
                    modifier = Modifier.semantics { contentDescription = sortDescription },
                )
            }
        }
        state.error?.let { error ->
            Text(error, Modifier.padding(16.dp))
            Button(
                onClick = viewModel::retry,
                enabled = state.owner != null && !state.loading,
                modifier = Modifier.focusRequester(retryFocusRequester),
            ) { Text(stringResource(R.string.retry)) }
        }
        if (state.loading && state.entries.isEmpty()) {
            LoadingPage(Modifier.fillMaxSize(), focusEnabled = !ribbonHasFocus)
        } else if (state.entries.isEmpty()) {
            val message =
                when {
                    state.owner == null -> R.string.fresh_connection_required

                    state.status == FreshStatus.Status.DISABLED -> R.string.fresh_disabled

                    state.status == FreshStatus.Status.PREPARING ||
                        state.status == FreshStatus.Status.REFRESHING -> R.string.fresh_preparing

                    state.status == FreshStatus.Status.UNAVAILABLE -> R.string.fresh_unavailable

                    else -> R.string.fresh_empty
                }
            Text(stringResource(message), Modifier.padding(16.dp))
        } else {
            key(state.owner, state.category, state.sort, state.gridEpoch, state.viewOptions.columns) {
                LaunchedEffect(Unit) {
                    if (!ribbonVisible && !showViewOptions) {
                        repeat(5) {
                            withFrameNanos { }
                            if (gridFocusRequester.tryRequestFocus()) return@LaunchedEffect
                        }
                    }
                }
                CardGrid(
                    pager = state.entries,
                    totalItems = state.totalResults,
                    focusAnchorKey = state.focusedKey,
                    onGridFocusChanged = { focused ->
                        gridHasFocus = focused
                        if (focused) ribbonVisible = false
                    },
                    onViewportRowsChanged = viewModel::viewportRows,
                    onFirstRowUp = {
                        ribbonVisible = true
                        requestCategoryFocus = true
                        true
                    },
                    backToTopOnBack = false,
                    backToTopRequestEpoch = backToTopEpoch,
                    itemKey = { state.entries[it].key },
                    onClickItem = { _, entry -> viewModel.open(entry) },
                    onLongClickItem = { _, entry ->
                        if (entry.item.type == SeerrItemType.MOVIE) {
                            quickMovieRequest.open(entry.item, onOpenDetails = { viewModel.open(entry) })
                        }
                    },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocusRequester,
                    initialPosition = state.focusedIndex,
                    positionCallback = { columns, index ->
                        viewModel.position(index, columns)
                    },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    columns = state.viewOptions.columns,
                    spacing = state.viewOptions.spacing.dp,
                    modifier = Modifier.weight(1f),
                    cardContent = { (entry, _, onClick, onLongClick, _, cardModifier) ->
                        DiscoverItemCard(
                            item = entry?.item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                            showOverlay = true,
                            useFallbackText = true,
                            showTitles = state.viewOptions.showTitles,
                            width = Dp.Unspecified,
                        )
                    },
                )
            }
        }
    }
    if (showViewOptions) {
        ViewOptionsDialog(
            viewOptions = state.viewOptions,
            defaultViewOptions = ViewOptions(),
            optionsOverride = freshPresentationOptions,
            onViewOptionsChange = viewModel::saveViewOptions,
            onDismissRequest = {
                showViewOptions = false
                restoreViewOptionsFocus = true
            },
        )
    }
}

/** CardGrid reports (columns, index); FreshViewModel records (index, columns). */
internal fun freshGridPositionCallback(position: (index: Int, columns: Int) -> Unit): (columns: Int, index: Int) -> Unit =
    { columns, index -> position(index, columns) }
