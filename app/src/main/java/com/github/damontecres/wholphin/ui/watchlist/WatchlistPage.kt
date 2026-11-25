package com.github.damontecres.wholphin.ui.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistFilter
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptionsDialog
import com.github.damontecres.wholphin.ui.components.rememberCustomPageDetailsReturnEpoch
import com.github.damontecres.wholphin.ui.components.rememberCustomPageFocusOwner
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.nav.LocalActiveDrawerFocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus

internal fun watchlistFilterIconColor(selection: WatchlistFilter): Color =
    when (selection) {
        WatchlistFilter.ALL -> Color.Unspecified
        WatchlistFilter.YES -> AppColors.Discover.Blue
        WatchlistFilter.NO -> AppColors.Discover.Red
    }

private val watchlistPresentationOptions =
    listOf<AppPreference<ViewOptions, out Any?>>(
        ViewOptions.ViewOptionsColumns,
        ViewOptions.ViewOptionsSpacing,
        ViewOptions.ViewOptionsShowTitles,
        ViewOptions.ViewOptionsBackdrop,
        ViewOptions.ViewOptionsReset,
    )

private fun WatchlistCategory.icon() =
    when (this) {
        WatchlistCategory.ALL -> R.string.fa_list_ul
        WatchlistCategory.MOVIES -> R.string.fa_film
        WatchlistCategory.SHOWS -> R.string.fa_tv
        WatchlistCategory.ANIMATION -> R.string.fa_images
    }

private fun WatchlistCategory.title() =
    when (this) {
        WatchlistCategory.ALL -> R.string.watchlist_all
        WatchlistCategory.MOVIES -> R.string.watchlist_movies
        WatchlistCategory.SHOWS -> R.string.watchlist_shows
        WatchlistCategory.ANIMATION -> R.string.watchlist_animation
    }

@Composable
fun WatchlistPage(
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val quickMovieRequest = com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest.current
    // Outgoing lazy-grid keys must keep the same row snapshot during a category reset.
    val state = viewModel.state.collectAsState().value
    val membership by viewModel.repository.state.collectAsState()
    var menu by remember(state.owner, state.category, state.filters) { mutableStateOf<WatchlistEntry?>(null) }
    var ribbonVisible by remember(state.owner) { mutableStateOf(false) }
    var ribbonHasFocus by remember(state.owner) { mutableStateOf(false) }
    var requestCategoryFocus by remember(state.owner) { mutableStateOf(false) }
    var restoreViewOptionsFocus by remember(state.owner) { mutableStateOf(false) }
    var showViewOptions by remember(state.owner) { mutableStateOf(false) }
    val categoryFocusRequesters = remember(state.owner) { WatchlistCategory.entries.associateWith { FocusRequester() } }
    val gridFocusRequester = remember(state.owner, state.category, state.filters) { FocusRequester() }
    val randomFocusRequester = remember(state.owner) { FocusRequester() }
    val viewOptionsFocusRequester = remember(state.owner) { FocusRequester() }
    val retryFocusRequester = remember(state.owner) { FocusRequester() }
    val watchlistIsTop = viewModel.isWatchlistTop()
    val restoreFocusEpoch =
        rememberCustomPageDetailsReturnEpoch(
            isTop = watchlistIsTop,
            focusAnchorKey = state.focusedKey?.let { "${it.mediaType}:${it.tmdbId}" },
            ribbonHasFocus = ribbonHasFocus,
            // Random may consume its return flag before ON_RESUME; its visible ribbon
            // still owns focus and must not be replaced by the saved grid card.
            suppress = state.restoreRandomFocus || ribbonVisible,
            scopeKey = Triple(state.owner, state.category, state.filters),
        )
    val drawerFocusRequester = LocalActiveDrawerFocusRequester.current
    var gridReady by remember(state.owner, state.category, state.filters, state.gridEpoch, state.viewOptions.columns) {
        mutableStateOf(false)
    }
    val pageFocusModifier =
        rememberCustomPageFocusOwner(
            isTop = watchlistIsTop,
            contentReady = gridReady && state.entries.isNotEmpty(),
            mayFocusGrid = !ribbonVisible && !showViewOptions && !state.restoreRandomFocus,
            gridFocusRequester = gridFocusRequester,
            ribbonVisible = ribbonVisible && !showViewOptions && !state.restoreRandomFocus,
            ribbonFocusRequester = categoryFocusRequesters[state.category],
            claimInitialFocus = !state.restoreRandomFocus,
        )
    val focused = state.entries.getOrNull(state.focusedIndex)
    LaunchedEffect(watchlistIsTop, state.viewOptions.showBackdrop, focused?.gridId, focused?.item?.backDropUrl) {
        if (watchlistIsTop) viewModel.updateBackdrop(focused.takeIf { state.viewOptions.showBackdrop })
    }
    BackHandler(enabled = watchlistIsTop && state.randomPending) { viewModel.cancelRandom() }
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
    LaunchedEffect(state.restoreRandomFocus, watchlistIsTop) {
        if (state.restoreRandomFocus && watchlistIsTop) {
            ribbonVisible = true
            repeat(5) {
                withFrameNanos { }
                if (randomFocusRequester.tryRequestFocus()) {
                    viewModel.consumeRandomReturnFocus()
                    viewModel.refresh()
                    return@LaunchedEffect
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!viewModel.state.value.restoreRandomFocus) viewModel.refresh()
    }
    Box(modifier.fillMaxSize().then(pageFocusModifier)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = ribbonVisible,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp + 24.dp, bottom = 4.dp)
                            .onFocusChanged { ribbonHasFocus = it.hasFocus }
                            .focusGroup()
                            .onPreviewKeyEvent {
                                it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown &&
                                    state.entries.isNotEmpty() && gridFocusRequester.tryRequestFocus()
                            },
                ) {
                    WatchlistCategory.entries.forEach { category ->
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
                    val viewOptionsDescription = stringResource(R.string.view_options)
                    ExpandableFaButton(
                        title = R.string.view_options,
                        iconStringRes = R.string.fa_sliders,
                        onClick = { showViewOptions = true },
                        enabled = state.owner != null,
                        modifier =
                            Modifier
                                .focusRequester(viewOptionsFocusRequester)
                                .semantics { contentDescription = viewOptionsDescription },
                    )
                    listOf(
                        Triple(R.string.watchlist_owned, state.filters.owned, viewModel::owned),
                        Triple(R.string.watchlist_watched, state.filters.watched, viewModel::watched),
                    ).forEach { (labelId, selected, select) ->
                        key(state.owner, labelId) {
                            val owned = labelId == R.string.watchlist_owned
                            val titles =
                                if (owned) {
                                    listOf(R.string.watchlist_owned_all, R.string.watchlist_owned_yes, R.string.watchlist_owned_no)
                                } else {
                                    listOf(R.string.watchlist_watched_all, R.string.watchlist_watched_yes, R.string.watchlist_watched_no)
                                }
                            val title = titles[selected.ordinal]
                            val description = stringResource(title)
                            ExpandableFaButton(
                                title = title,
                                iconStringRes = if (owned) R.string.fa_film else R.string.fa_check,
                                iconColor = watchlistFilterIconColor(selected),
                                onClick = { select(WatchlistFilter.entries[(selected.ordinal + 1) % WatchlistFilter.entries.size]) },
                                enabled = state.owner != null,
                                modifier = Modifier.semantics { contentDescription = description },
                            )
                        }
                    }
                    val randomDescription = stringResource(R.string.sort_by_random)
                    ExpandableFaButton(
                        title = R.string.sort_by_random,
                        iconStringRes = R.string.fa_dice,
                        onClick = viewModel::random,
                        enabled = state.owner != null && state.totalResults > 0,
                        modifier = Modifier.focusRequester(randomFocusRequester).semantics { contentDescription = randomDescription },
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
            if ((state.loading || state.owner == null) && state.entries.isEmpty() && state.error == null) {
                LoadingPage(Modifier.fillMaxSize(), focusEnabled = false)
            } else if (state.entries.isEmpty() && state.error == null) {
                Text(stringResource(R.string.watchlist_empty), Modifier.padding(16.dp))
            } else if (state.entries.isNotEmpty()) {
                key(state.owner, state.category, state.filters, state.gridEpoch, state.viewOptions.columns) {
                    CardGrid(
                        pager = state.entries,
                        totalItems = state.totalResults,
                        positionOffset = (state.firstPage - 1) * WATCHLIST_PAGE_SIZE,
                        alignWindowToAbsoluteRows = true,
                        focusAnchorKey = state.focusedKey?.let { "${it.mediaType}:${it.tmdbId}" },
                        focusRequestEpoch = restoreFocusEpoch,
                        onGridFocusChanged = { focused ->
                            if (focused) ribbonVisible = false
                        },
                        onViewportRowsChanged = viewModel::viewportRows,
                        onFirstRowUp = {
                            ribbonVisible = true
                            requestCategoryFocus = true
                            true
                        },
                        onBackToFirstResult = viewModel::backToFirst,
                        useBackDispatcherFallback = watchlistIsTop,
                        itemKey = { state.entries[it].gridId },
                        onClickItem = { _, item -> viewModel.open(item) },
                        onLongClickItem = { _, item ->
                            if (item.item.type == com.github.damontecres.wholphin.data.model.SeerrItemType.MOVIE) {
                                quickMovieRequest.open(item.item, onUnavailable = { menu = item }, onOpenDetails = { viewModel.open(item) })
                            } else {
                                menu = item
                            }
                        },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = gridFocusRequester,
                        leftmostItemFocusRequester = drawerFocusRequester,
                        onFocusTargetReady = { gridReady = true },
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
                                savedToWatchlist = entry != null && membership.membership[entry.key] == WatchlistMembership.MEMBER,
                            )
                        },
                    )
                }
            }
        }
    }
    if (showViewOptions) {
        ViewOptionsDialog(
            viewOptions = state.viewOptions,
            defaultViewOptions = ViewOptions(),
            optionsOverride = watchlistPresentationOptions,
            onViewOptionsChange = viewModel::saveViewOptions,
            onDismissRequest = {
                showViewOptions = false
                restoreViewOptionsFocus = true
            },
        )
    }
    menu?.let { entry ->
        val currentMembership = membership.membership[entry.key] ?: WatchlistMembership.UNKNOWN
        val add = currentMembership == WatchlistMembership.NOT_MEMBER
        DialogPopup(
            showDialog = true,
            title = entry.title,
            waitToLoad = true,
            onDismissRequest = { menu = null },
            dialogItems =
                listOf(
                    DialogItem(
                        headlineContent = { Text(stringResource(if (add) R.string.watchlist_add else R.string.watchlist_remove)) },
                        leadingContent = { Icon(Icons.Default.Delete, null) },
                        enabled = entry.key !in membership.busy && currentMembership != WatchlistMembership.UNKNOWN,
                        onClick = {
                            menu = null
                            viewModel.changeMembership(entry, add)
                        },
                    ),
                ),
        )
    }
}
