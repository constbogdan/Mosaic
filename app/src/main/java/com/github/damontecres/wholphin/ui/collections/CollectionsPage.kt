package com.github.damontecres.wholphin.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
import com.github.damontecres.wholphin.services.CollectionsEntry
import com.github.damontecres.wholphin.services.CollectionsFilter
import com.github.damontecres.wholphin.services.CollectionsSort
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
internal fun CollectionsPage(
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val filterFocus = remember { FocusRequester() }
    var toolbarHasFocus by remember(state.owner) { mutableStateOf(false) }
    var gridHasFocus by remember(state.owner, state.filter, state.sort) { mutableStateOf(false) }
    BackHandler(enabled = gridHasFocus && state.entries.isNotEmpty()) { filterFocus.tryRequestFocus() }
    Column(modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 40.dp)
                    .onFocusChanged { toolbarHasFocus = it.hasFocus }
                    .focusGroup(),
        ) {
            val sortTitle = collectionsSortTitle(state.sort)
            val sortLabel = stringResource(sortTitle)
            ExpandableFaButton(
                title = sortTitle,
                iconStringRes = R.string.fa_clock,
                onClick = { viewModel.sort(CollectionsSort.entries[(state.sort.ordinal + 1) % CollectionsSort.entries.size]) },
                enabled = state.owner != null,
                modifier = Modifier.semantics { contentDescription = "Sort: $sortLabel" },
            )
            val filterTitle = collectionsFilterTitle(state.filter)
            val filterLabel = stringResource(filterTitle)
            ExpandableFaButton(
                title = filterTitle,
                iconStringRes = R.string.fa_filter,
                onClick = { viewModel.filter(CollectionsFilter.entries[(state.filter.ordinal + 1) % CollectionsFilter.entries.size]) },
                enabled = state.owner != null,
                modifier = Modifier.focusRequester(filterFocus).semantics { contentDescription = "Filter: $filterLabel" },
            )
        }
        if (state.stale) Text(stringResource(R.string.collections_stale), Modifier.padding(8.dp))
        if (state.status == LibraryCollectionsResponse.Status.PARTIAL || state.failedCollections > 0) {
            Text(stringResource(R.string.collections_partial), Modifier.padding(8.dp))
        }
        if (state.error != null) {
            Text(state.error, Modifier.padding(16.dp))
            Button(onClick = viewModel::retry, enabled = state.owner != null && !state.loading) {
                Text(stringResource(R.string.retry))
            }
        }
        if (state.loading && state.entries.isEmpty()) {
            LoadingPage(Modifier.weight(1f), focusEnabled = !toolbarHasFocus)
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
            LaunchedEffect(state.owner, state.filter, state.sort, state.denied) {
                if (!toolbarHasFocus) filterFocus.tryRequestFocus()
            }
        } else {
            key(state.owner, state.filter, state.sort, state.gridEpoch) {
                val gridFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (!toolbarHasFocus) gridFocus.tryRequestFocus() }
                CardGrid(
                    pager = state.entries,
                    totalItems = state.total,
                    focusAnchorKey = state.focusedKey,
                    onGridFocusChanged = { gridHasFocus = it },
                    onFirstRowUp = { filterFocus.tryRequestFocus() },
                    backToTopOnBack = false,
                    itemKey = { state.entries[it].gridId },
                    onClickItem = { _, item -> viewModel.open(item) },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocus,
                    initialPosition = state.focusedIndex,
                    positionCallback = { columns, index -> viewModel.position(index, columns) },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    modifier = Modifier.weight(1f),
                    cardContent = { (item, _, onClick, onLongClick, _, cardModifier) ->
                        CollectionsCard(item, onClick, onLongClick, cardModifier)
                    },
                )
            }
        }
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
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
        ) {
            Box(Modifier.fillMaxWidth()) {
                ItemCardImage(
                    imageUrl = item?.posterUrl,
                    name = item?.summary?.name,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    numberOfVersions = -1,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxWidth().aspectRatio(AspectRatios.TALL),
                )
                val available = item?.summary?.availableMovies
                val total = item?.summary?.totalMovies
                val count =
                    if (available != null && total != null && available <= total) {
                        stringResource(R.string.collections_count, available, total)
                    } else {
                        stringResource(R.string.collections_count_unknown)
                    }
                Text(
                    text = count,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                            .padding(5.dp),
                )
            }
        }
        Text(
            item?.summary?.name.orEmpty(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}
