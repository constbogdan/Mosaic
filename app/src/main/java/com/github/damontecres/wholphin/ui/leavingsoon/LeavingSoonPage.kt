package com.github.damontecres.wholphin.ui.leavingsoon

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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.LEAVING_SOON_PAGE_SIZE
import com.github.damontecres.wholphin.services.LeavingSoonCategory
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.components.ViewOptionsDialog
import com.github.damontecres.wholphin.ui.components.rememberCustomPageDetailsReturnEpoch
import com.github.damontecres.wholphin.ui.components.rememberCustomPageFocusOwner
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.nav.LocalActiveDrawerFocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val leavingSoonPresentationOptions =
    listOf<AppPreference<ViewOptions, out Any?>>(
        ViewOptions.ViewOptionsColumns,
        ViewOptions.ViewOptionsSpacing,
        ViewOptions.ViewOptionsShowTitles,
        ViewOptions.ViewOptionsReset,
    )

private fun LeavingSoonCategory.icon() =
    when (this) {
        LeavingSoonCategory.ALL -> R.string.fa_list_ul
        LeavingSoonCategory.MOVIES -> R.string.fa_film
        LeavingSoonCategory.SERIES -> R.string.fa_tv
    }

private fun LeavingSoonCategory.title() =
    when (this) {
        LeavingSoonCategory.ALL -> R.string.leaving_soon_all
        LeavingSoonCategory.MOVIES -> R.string.leaving_soon_movies
        LeavingSoonCategory.SERIES -> R.string.leaving_soon_series
    }

internal fun formatLeavingSoonDate(
    date: OffsetDateTime,
    locale: Locale = Locale.getDefault(),
): String = date.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM uuuu", locale))

@Composable
internal fun LeavingSoonPage(
    modifier: Modifier = Modifier,
    viewModel: LeavingSoonViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val quickMovieRequest = LocalQuickMovieRequest.current
    var ribbonVisible by remember(state.owner) { mutableStateOf(false) }
    var ribbonHasFocus by remember(state.owner) { mutableStateOf(false) }
    var requestCategoryFocus by remember(state.owner) { mutableStateOf(false) }
    var showViewOptions by remember(state.owner) { mutableStateOf(false) }
    var restoreViewOptionsFocus by remember(state.owner) { mutableStateOf(false) }
    val refreshFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember(state.owner) { FocusRequester() }
    val viewOptionsFocusRequester = remember(state.owner) { FocusRequester() }
    val gridFocusRequester = remember(state.owner, state.category) { FocusRequester() }
    val categoryFocusRequesters = remember(state.owner) { LeavingSoonCategory.entries.associateWith { FocusRequester() } }
    val drawerFocusRequester = LocalActiveDrawerFocusRequester.current
    val isTop = viewModel.isLeavingSoonTop()
    val restoreFocusEpoch =
        rememberCustomPageDetailsReturnEpoch(
            isTop = isTop,
            focusAnchorKey = state.focusedKey,
            ribbonHasFocus = ribbonHasFocus,
            scopeKey = state.owner to state.category,
        )
    var gridReady by remember(state.owner, state.category, state.gridEpoch, state.viewOptions.columns) { mutableStateOf(false) }
    val pageFocusModifier =
        rememberCustomPageFocusOwner(
            isTop = isTop,
            contentReady = gridReady && state.entries.isNotEmpty(),
            mayFocusGrid = !ribbonVisible && !showViewOptions,
            gridFocusRequester = gridFocusRequester,
            ribbonVisible = ribbonVisible && !showViewOptions,
            ribbonFocusRequester = categoryFocusRequesters[state.category],
        )
    LaunchedEffect(state.owner, state.loading, state.entries.isEmpty(), state.error) {
        if (state.entries.isEmpty() && !state.initializing && !state.loading && (state.owner != null || state.error != null)) {
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
                LeavingSoonCategory.entries.forEach { category ->
                    val description = stringResource(category.title())
                    ExpandableFaButton(
                        title = category.title(),
                        iconStringRes = category.icon(),
                        iconColor = if (state.category == category) AppColors.Discover.Blue else Color.Unspecified,
                        onClick = { viewModel.category(category) },
                        enabled = state.owner != null,
                        modifier =
                            Modifier.focusRequester(categoryFocusRequesters.getValue(category)).semantics {
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
                val refreshLabel = stringResource(R.string.leaving_soon_refresh)
                ExpandableFaButton(
                    title = R.string.leaving_soon_refresh,
                    iconStringRes = R.string.fa_clock,
                    onClick = viewModel::reload,
                    enabled = state.owner != null,
                    modifier = Modifier.focusRequester(refreshFocusRequester).semantics { contentDescription = refreshLabel },
                )
            }
        }
        state.error?.let { error ->
            Text(error, Modifier.padding(16.dp))
            Button(
                onClick = viewModel::retry,
                enabled = state.owner != null && !state.loading,
                modifier = Modifier.focusRequester(retryFocusRequester),
            ) {
                Text(stringResource(R.string.retry))
            }
        }
        if ((state.initializing || state.loading) && state.entries.isEmpty()) {
            LoadingPage(Modifier.fillMaxSize(), focusEnabled = false)
        } else if (state.entries.isEmpty()) {
            val message = if (state.owner == null) R.string.leaving_soon_connection_required else R.string.leaving_soon_empty
            if (state.error == null) Text(stringResource(message), Modifier.padding(16.dp))
        } else {
            key(state.owner, state.category, state.gridEpoch, state.viewOptions.columns) {
                CardGrid(
                    pager = state.entries,
                    totalItems = state.totalResults,
                    positionOffset = (state.firstPage - 1) * LEAVING_SOON_PAGE_SIZE,
                    alignWindowToAbsoluteRows = true,
                    focusAnchorKey = state.focusedKey,
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
                    useBackDispatcherFallback = isTop,
                    itemKey = { state.entries[it].key },
                    onClickItem = { _, entry -> viewModel.open(entry) },
                    onLongClickItem = { _, entry ->
                        if (entry.hasTmdbIdentity && entry.item.type == SeerrItemType.MOVIE) {
                            quickMovieRequest.open(entry.item, onOpenDetails = { viewModel.open(entry) })
                        }
                    },
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
                    cardContent = { (entry, _, onClick, onLongClick, _, cardModifier) ->
                        val badge =
                            entry?.let { loaded ->
                                loaded.removeAt?.let { stringResource(R.string.leaving_soon_date, formatLeavingSoonDate(it)) }
                                    ?: stringResource(R.string.leaving_soon_date_unknown)
                            }
                        DiscoverItemCard(
                            item = entry?.item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                            showOverlay = true,
                            useFallbackText = true,
                            showTitles = state.viewOptions.showTitles,
                            width = Dp.Unspecified,
                            bottomBadge = badge,
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
            optionsOverride = leavingSoonPresentationOptions,
            onViewOptionsChange = viewModel::saveViewOptions,
            onDismissRequest = {
                showViewOptions = false
                restoreViewOptionsFocus = true
            },
        )
    }
}
