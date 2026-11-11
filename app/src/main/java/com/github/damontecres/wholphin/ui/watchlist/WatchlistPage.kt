package com.github.damontecres.wholphin.ui.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.tryRequestFocus

internal fun watchlistFilterIconColor(selection: WatchlistFilter): Color =
    when (selection) {
        WatchlistFilter.ALL -> Color.Unspecified
        WatchlistFilter.YES -> AppColors.Discover.Blue
        WatchlistFilter.NO -> AppColors.Discover.Red
    }

@Composable
fun WatchlistPage(
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    // Outgoing lazy-grid keys must keep the same row snapshot during a category reset.
    val state = viewModel.state.collectAsState().value
    val membership by viewModel.repository.state.collectAsState()
    val tabs =
        remember {
            listOf(
                R.string.watchlist_all,
                R.string.watchlist_movies,
                R.string.watchlist_shows,
                R.string.watchlist_animation,
            ).map(::TabDetails)
        }
    var menu by remember(state.owner, state.category, state.filters) { mutableStateOf<WatchlistEntry?>(null) }
    var toolbarHasFocus by remember(state.owner) { mutableStateOf(false) }
    var gridHasFocus by remember(state.owner, state.category, state.filters) { mutableStateOf(false) }
    val randomFocusRequester = remember { FocusRequester() }
    BackHandler(enabled = gridHasFocus && state.entries.isNotEmpty() && menu == null) {
        randomFocusRequester.tryRequestFocus()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TabbedPage(
        selectedTabIndex = state.category.ordinal,
        tabs = tabs,
        updateSelectedTabIndex = { viewModel.category(WatchlistCategory.entries[it]) },
        isShowClock = false,
        modifier = modifier,
    ) { _, tab ->
        Column(Modifier.fillMaxSize()) {
            Row(
                // Match the Movies Library's CollectionFolderHeader: 16 dp start,
                // 16 + 24 dp end (grid side rail), 8 dp gaps, no extra vertical padding.
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp + 24.dp)
                        .onFocusChanged { toolbarHasFocus = it.hasFocus }
                        .focusGroup(),
            ) {
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
            state.error?.let { error ->
                Text(error, Modifier.padding(16.dp))
                Button(onClick = viewModel::retry, enabled = state.owner != null && !state.loading) { Text(stringResource(R.string.retry)) }
            }
            if (state.loading && state.entries.isEmpty()) {
                LoadingPage(Modifier.fillMaxSize(), focusEnabled = !toolbarHasFocus)
            } else if (state.entries.isEmpty() && state.error == null) {
                Text(stringResource(R.string.watchlist_empty), Modifier.padding(16.dp))
                LaunchedEffect(state.owner, state.category, state.filters) {
                    if (!toolbarHasFocus) tab.tabFocusRequester.tryRequestFocus()
                }
            } else if (state.entries.isNotEmpty()) {
                key(state.owner, state.category, state.filters, state.gridEpoch) {
                    LaunchedEffect(Unit) {
                        // A query reset must not steal focus from a filter being cycled.
                        if (!toolbarHasFocus) tab.contentFocusRequester.tryRequestFocus()
                    }
                    CardGrid(
                        pager = state.entries,
                        totalItems = state.totalResults,
                        positionOffset = (state.firstPage - 1) * WATCHLIST_PAGE_SIZE,
                        alignWindowToAbsoluteRows = true,
                        focusAnchorKey = state.focusedKey?.let { "${it.mediaType}:${it.tmdbId}" },
                        focusRequestEpoch = state.randomFocusEpoch,
                        onGridFocusChanged = { gridHasFocus = it },
                        onFirstRowUp = { randomFocusRequester.tryRequestFocus() },
                        backToTopOnBack = false,
                        itemKey = { state.entries[it].gridId },
                        onClickItem = { _, item -> viewModel.open(item) },
                        onLongClickItem = { _, item -> menu = item },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = tab.contentFocusRequester,
                        initialPosition = state.focusedIndex,
                        positionCallback = { columns, index -> viewModel.position(index, columns) },
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
                                savedToWatchlist = entry != null && membership.membership[entry.key] == WatchlistMembership.MEMBER,
                            )
                        },
                    )
                }
            }
        }
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
