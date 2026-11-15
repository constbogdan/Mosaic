package com.github.damontecres.wholphin.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
internal fun SeerrCollectionDetails(
    destination: Destination.SeerrCollection,
    modifier: Modifier = Modifier,
    viewModel: SeerrCollectionDetailsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val quickMovieRequest = LocalQuickMovieRequest.current
    val gridFocus = remember { FocusRequester() }
    val requestFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
    val similarListState = rememberLazyListState()
    var requestWasOpen by remember { mutableStateOf(false) }
    var returnFocusPending by remember { mutableStateOf(false) }
    LaunchedEffect(destination.collectionId, destination.owner, destination.entryToken) {
        viewModel.show(destination.collectionId, destination.owner)
    }
    LaunchedEffect(state.request, state.loading, state.requestEligibilityLoading, state.details) {
        if (state.request != null) {
            requestWasOpen = true
            returnFocusPending = false
        } else if (requestWasOpen) {
            requestWasOpen = false
            returnFocusPending = true
        }
        if (returnFocusPending && !state.loading && !state.requestEligibilityLoading) {
            returnFocusPending = false
            if (state.requestEligibility.values.any { it == CollectionRequestEligibility.ELIGIBLE }) {
                requestFocus.tryRequestFocus()
            } else {
                gridFocus.tryRequestFocus()
            }
        }
    }
    state.request?.let { request ->
        CollectionRequestDialog(
            state = request,
            onDismiss = viewModel::closeRequest,
            onToggle = viewModel::toggleRequest,
            onToggleAll = viewModel::toggleAllRequests,
            onSubmit = viewModel::submitRequests,
        )
    }
    Column(modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 40.dp, bottom = 12.dp),
        ) {
            Box(Modifier.width(140.dp).height(190.dp), contentAlignment = Alignment.Center) {
                val image = state.details?.posterUrl ?: state.details?.backdropUrl
                if (image != null) {
                    AsyncImage(model = image, contentDescription = state.details?.name, modifier = Modifier.fillMaxSize())
                } else {
                    Text(stringResource(R.string.collections_no_artwork))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    state.details?.name ?: stringResource(R.string.collections_details_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val available = destination.availableMovies
                val total = destination.totalMovies
                if (state.owner == destination.owner && state.details != null && available != null && total != null &&
                    available in 0..total
                ) {
                    Text(stringResource(R.string.collections_count, available, total))
                    Text(stringResource(R.string.collections_available_missing, available, total - available))
                } else if (state.owner == destination.owner && state.details != null) {
                    Text(stringResource(R.string.collections_count_unknown))
                }
                state.details?.overview?.let { Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                if (!state.loading && !state.error && !state.denied && !state.requestEligibilityLoading &&
                    state.owner == destination.owner &&
                    state.requestEligibility.values.any { it == CollectionRequestEligibility.ELIGIBLE }
                ) {
                    Button(onClick = viewModel::openRequest, modifier = Modifier.focusRequester(requestFocus)) {
                        Text(stringResource(R.string.collections_request))
                    }
                }
            }
        }
        when {
            state.owner == null -> {
                Text(stringResource(R.string.collections_connection_required), Modifier.padding(24.dp))
            }

            state.owner != destination.owner -> {
                Text(stringResource(R.string.collections_session_changed), Modifier.padding(24.dp))
            }

            state.denied -> {
                Text(stringResource(R.string.collections_permission_required), Modifier.padding(24.dp))
            }

            state.loading && state.details == null -> {
                LoadingPage(Modifier.weight(1f), focusEnabled = false)
            }

            state.error -> {
                Text(stringResource(R.string.collections_unavailable), Modifier.padding(24.dp))
                Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
            }

            state.details?.movies?.isEmpty() == true -> {
                Text(stringResource(R.string.collections_details_empty), Modifier.padding(24.dp))
            }

            state.details != null -> {
                val movies = state.details.movies
                LaunchedEffect(state.owner, destination.collectionId) {
                    if (state.similarFocusedId == null) gridFocus.tryRequestFocus()
                }
                CardGrid(
                    pager = movies,
                    totalItems = movies.size,
                    focusAnchorKey = state.focusedKey,
                    backToTopOnBack = false,
                    itemKey = { "tmdb:movie:${movies[it].id}" },
                    onClickItem = { _, item -> viewModel.open(item) },
                    onLongClickItem = { _, item -> quickMovieRequest.open(item, onOpenDetails = { viewModel.open(item) }) },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocus,
                    initialPosition = state.focusedIndex,
                    positionCallback = { _, index -> viewModel.position(index) },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    footerFocusRequester = similarFocus.takeIf { state.similarFocusedId != null },
                    modifier = Modifier.weight(1f),
                    cardContent = { (item, _, onClick, onLongClick, _, cardModifier) ->
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                            showOverlay = true,
                            useFallbackText = true,
                            width = Dp.Unspecified,
                        )
                    },
                    footerContent =
                        if (state.owner == destination.owner && (state.similarLoading || state.similar.isNotEmpty())) {
                            {
                                if (state.similarLoading) {
                                    LoadingPage(Modifier.fillMaxWidth().height(120.dp), focusEnabled = false)
                                } else {
                                    SimilarCollectionsRow(
                                        collections = state.similar,
                                        selectedId = state.similarFocusedId,
                                        focusRequester = similarFocus,
                                        listState = similarListState,
                                        onFocus = viewModel::focusSimilar,
                                        onOpen = viewModel::openSimilar,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun SimilarCollectionsRow(
    collections: List<CollectionDetailsData>,
    selectedId: Int?,
    focusRequester: FocusRequester,
    listState: LazyListState,
    onFocus: (Int) -> Unit,
    onOpen: (Int) -> Unit,
) {
    var initialSelectionRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedId, collections) {
        val index = collections.indexOfFirst { it.id == selectedId }
        if (!initialSelectionRestored && index >= 0) {
            listState.scrollToItem(index)
            initialSelectionRestored = true
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) {
        Text(
            stringResource(R.string.collections_similar),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().height(235.dp).padding(start = 24.dp, end = 40.dp),
        ) {
            items(collections, key = { it.id }) { item ->
                if (selectedId == item.id) {
                    LaunchedEffect(item.id) {
                        focusRequester.tryRequestFocus()
                    }
                }
                CollectionCard(
                    name = item.name,
                    posterUrl = item.posterUrl,
                    available = null,
                    total = null,
                    onClick = { onOpen(item.id) },
                    onLongClick = {},
                    modifier = Modifier.width(125.dp),
                    cardModifier =
                        Modifier
                            .then(if (selectedId == item.id) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { if (it.isFocused) onFocus(item.id) },
                )
            }
        }
    }
}
