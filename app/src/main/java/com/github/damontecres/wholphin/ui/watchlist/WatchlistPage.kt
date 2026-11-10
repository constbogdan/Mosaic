package com.github.damontecres.wholphin.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun WatchlistPage(
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
    var menu by remember(state.owner, state.category) { mutableStateOf<WatchlistEntry?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TabbedPage(
        selectedTabIndex = state.category.ordinal,
        tabs = tabs,
        updateSelectedTabIndex = { viewModel.category(WatchlistCategory.entries[it]) },
        isShowClock = false,
        modifier = modifier,
    ) { _, tab ->
        Column(Modifier.fillMaxSize()) {
            Text("${stringResource(R.string.watchlist)} (${state.totalResults})", Modifier.padding(16.dp))
            state.error?.let { error ->
                Text(error, Modifier.padding(16.dp))
                Button(onClick = viewModel::retry, enabled = state.owner != null && !state.loading) { Text(stringResource(R.string.retry)) }
            }
            if (state.loading && state.entries.isEmpty()) {
                LoadingPage(Modifier.fillMaxSize())
            } else if (state.entries.isEmpty() && state.error == null) {
                Text(stringResource(R.string.watchlist_empty), Modifier.padding(16.dp))
                LaunchedEffect(state.owner, state.category) { tab.tabFocusRequester.tryRequestFocus() }
            } else if (state.entries.isNotEmpty()) {
                key(state.owner, state.category, state.gridEpoch) {
                    LaunchedEffect(Unit) { tab.contentFocusRequester.tryRequestFocus() }
                    CardGrid(
                        pager = state.entries,
                        itemKey = { state.entries[it].gridId },
                        onClickItem = { _, item -> viewModel.open(item) },
                        onLongClickItem = { _, item -> menu = item },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = tab.contentFocusRequester,
                        initialPosition = state.focusedIndex,
                        positionCallback = { _, index -> viewModel.position(index) },
                        showJumpButtons = false,
                        showLetterButtons = false,
                        modifier = Modifier.weight(1f),
                        cardContent = { (entry, _, onClick, onLongClick, _, cardModifier) ->
                            Box(cardModifier) {
                                DiscoverItemCard(
                                    item = entry?.item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    showOverlay = true,
                                    useFallbackText = true,
                                )
                                if (entry != null && membership.membership[entry.key] == WatchlistMembership.MEMBER) {
                                    val label = stringResource(R.string.watchlist_saved)
                                    Text(
                                        "\uf02e",
                                        fontFamily = FontAwesome,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).semantics { contentDescription = label },
                                    )
                                }
                            }
                        },
                    )
                }
                if (state.loading) Text("Loading…", Modifier.padding(8.dp))
                if (!state.loading && state.error == null && state.page < state.totalPages) {
                    Button(onClick = { viewModel.position(state.entries.lastIndex) }) { Text(stringResource(R.string.more)) }
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
