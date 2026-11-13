package com.github.damontecres.wholphin.ui.fresh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.github.damontecres.wholphin.services.FreshCategory
import com.github.damontecres.wholphin.services.FreshSort
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
internal fun FreshPage(
    modifier: Modifier = Modifier,
    viewModel: FreshViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val quickMovieRequest = LocalQuickMovieRequest.current
    val tabs =
        remember {
            listOf(
                R.string.fresh_all,
                R.string.fresh_movies,
                R.string.fresh_shows,
                R.string.fresh_animation,
                R.string.fresh_documentary,
            ).map(::TabDetails)
        }
    val sortFocusRequester = remember { FocusRequester() }
    var toolbarHasFocus by remember(state.owner) { mutableStateOf(false) }
    var gridHasFocus by remember(state.owner, state.category, state.sort) { mutableStateOf(false) }
    BackHandler(enabled = gridHasFocus && state.entries.isNotEmpty()) { sortFocusRequester.tryRequestFocus() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TabbedPage(
        selectedTabIndex = state.category.ordinal,
        tabs = tabs,
        updateSelectedTabIndex = { viewModel.category(FreshCategory.entries[it]) },
        isShowClock = false,
        modifier = modifier,
    ) { _, tab ->
        Column(Modifier.fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 40.dp)
                        .onFocusChanged { toolbarHasFocus = it.hasFocus }
                        .focusGroup(),
            ) {
                val title =
                    when (state.sort) {
                        FreshSort.FRESHEST -> R.string.fresh_sort_freshest
                        FreshSort.OLDEST -> R.string.fresh_sort_oldest
                        FreshSort.RATING -> R.string.fresh_sort_rating
                    }
                val label = stringResource(title)
                ExpandableFaButton(
                    title = title,
                    iconStringRes = R.string.fa_clock,
                    onClick = { viewModel.sort(FreshSort.entries[(state.sort.ordinal + 1) % FreshSort.entries.size]) },
                    enabled = state.owner != null,
                    modifier = Modifier.focusRequester(sortFocusRequester).semantics { contentDescription = label },
                )
            }
            state.error?.let { error ->
                Text(error, Modifier.padding(16.dp))
                Button(onClick = viewModel::retry, enabled = state.owner != null && !state.loading) {
                    Text(stringResource(R.string.retry))
                }
            }
            if (state.loading && state.entries.isEmpty()) {
                LoadingPage(Modifier.fillMaxSize(), focusEnabled = !toolbarHasFocus)
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
                LaunchedEffect(state.owner, state.category, state.sort) {
                    if (!toolbarHasFocus) tab.tabFocusRequester.tryRequestFocus()
                }
            } else {
                key(state.owner, state.category, state.sort, state.gridEpoch) {
                    LaunchedEffect(Unit) {
                        if (!toolbarHasFocus) tab.contentFocusRequester.tryRequestFocus()
                    }
                    CardGrid(
                        pager = state.entries,
                        totalItems = state.totalResults,
                        focusAnchorKey = state.focusedKey,
                        onGridFocusChanged = { gridHasFocus = it },
                        onFirstRowUp = { sortFocusRequester.tryRequestFocus() },
                        backToTopOnBack = false,
                        itemKey = { state.entries[it].key },
                        onClickItem = { _, entry -> viewModel.open(entry) },
                        onLongClickItem = { _, entry ->
                            if (entry.item.type == SeerrItemType.MOVIE) {
                                quickMovieRequest.open(entry.item, onOpenDetails = { viewModel.open(entry) })
                            }
                        },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = tab.contentFocusRequester,
                        initialPosition = state.focusedIndex,
                        positionCallback = freshGridPositionCallback(viewModel::position),
                        showJumpButtons = false,
                        showLetterButtons = false,
                        modifier = Modifier.weight(1f),
                        cardContent = { (entry, _, onClick, onLongClick, _, cardModifier) ->
                            DiscoverItemCard(
                                item = entry?.item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                modifier = cardModifier,
                                showOverlay = true,
                                useFallbackText = true,
                                width = Dp.Unspecified,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** CardGrid reports (columns, index); FreshViewModel records (index, columns). */
internal fun freshGridPositionCallback(position: (index: Int, columns: Int) -> Unit): (columns: Int, index: Int) -> Unit =
    { columns, index -> position(index, columns) }
