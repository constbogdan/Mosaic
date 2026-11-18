package com.github.damontecres.wholphin.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus

internal enum class CollectionFocusSection { POSTER, MOVIES, SIMILAR }

internal fun collectionMovieUpTargetsPoster(section: CollectionFocusSection?): Boolean = section == CollectionFocusSection.MOVIES

internal fun collectionMovieDownTargetsSimilar(
    section: CollectionFocusSection?,
    focusedIndex: Int,
    movieCount: Int,
    columns: Int,
    hasSimilar: Boolean,
): Boolean =
    section == CollectionFocusSection.MOVIES &&
        hasSimilar && movieCount > 0 && columns > 0 &&
        focusedIndex in 0 until movieCount && focusedIndex / columns == (movieCount - 1) / columns

@Composable
internal fun SeerrCollectionDetails(
    destination: Destination.SeerrCollection,
    modifier: Modifier = Modifier,
    viewModel: SeerrCollectionDetailsViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsState().value
    val quickMovieRequest = LocalQuickMovieRequest.current
    val gridFocus = remember { FocusRequester() }
    val posterFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
    val similarListState = rememberLazyListState()
    var requestWasOpen by remember { mutableStateOf(false) }
    var returnFocusPending by remember { mutableStateOf(false) }
    var focusSection by remember(destination.entryToken) { mutableStateOf<CollectionFocusSection?>(null) }
    var backShortcutArmed by rememberSaveable(destination.entryToken) { mutableStateOf(state.similarFocusedId != null) }
    var lastSimilarFocusId by rememberSaveable(destination.entryToken) { mutableStateOf<Int?>(null) }
    var memberFocusEpoch by remember(destination.entryToken) { mutableStateOf(0L) }
    var similarFocusEpoch by remember(destination.entryToken) { mutableStateOf(0L) }
    var entryFocusRestored by remember(destination.entryToken) { mutableStateOf(false) }
    val posterActionable =
        state.owner == destination.owner && state.details != null && !state.loading && !state.error && !state.denied &&
            !state.requestEligibilityLoading &&
            state.requestEligibility.values.any { it == CollectionRequestEligibility.ELIGIBLE }
    LaunchedEffect(destination.collectionId, destination.owner, destination.entryToken) {
        viewModel.show(destination.collectionId, destination.owner, destination.entryToken)
    }
    LaunchedEffect(state.owner, state.details) {
        if (state.owner != destination.owner || state.details == null) {
            entryFocusRestored = false
            focusSection = null
            lastSimilarFocusId = null
        }
    }
    DisposableEffect(viewModel, destination.collectionId, destination.owner, destination.entryToken) {
        onDispose { viewModel.onScreenHidden(destination.entryToken) }
    }
    BackHandler(
        enabled =
            backShortcutArmed &&
                focusSection in setOf(CollectionFocusSection.MOVIES, CollectionFocusSection.SIMILAR) &&
                state.owner == destination.owner && state.details != null && state.request == null,
    ) {
        posterFocus.tryRequestFocus()
    }
    LaunchedEffect(state.request, state.loading, state.requestEligibilityLoading, state.details, posterActionable) {
        if (state.request != null) {
            requestWasOpen = true
            returnFocusPending = false
        } else if (requestWasOpen) {
            requestWasOpen = false
            returnFocusPending = true
        }
        if (returnFocusPending && !state.loading && !state.requestEligibilityLoading) {
            returnFocusPending = false
            if (posterActionable) {
                posterFocus.tryRequestFocus()
            } else if (state.details?.movies?.isNotEmpty() == true) {
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
    Column(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown &&
                    it.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
                ) {
                    backShortcutArmed = true
                }
                false
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 40.dp, bottom = 12.dp),
        ) {
            val posterModifier = Modifier.width(140.dp).height(190.dp)
            val posterContent: @Composable () -> Unit = {
                CollectionDetailsPosterContent(
                    name = state.details?.name,
                    image = state.details?.posterUrl ?: state.details?.backdropUrl,
                    available = destination.availableMovies,
                    total = destination.totalMovies,
                    showCount = state.owner == destination.owner && state.details != null,
                )
            }
            if (posterActionable) {
                val requestLabel = stringResource(R.string.collections_request)
                Card(
                    onClick = viewModel::openRequest,
                    modifier =
                        posterModifier
                            .focusRequester(posterFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusSection = CollectionFocusSection.POSTER
                                } else if (focusSection == CollectionFocusSection.POSTER) {
                                    focusSection = null
                                }
                            }.onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                    memberFocusEpoch++
                                    true
                                } else {
                                    false
                                }
                            }.semantics { contentDescription = requestLabel }
                            .testTag("collectionRequestPoster"),
                    colors = CardDefaults.colors(containerColor = Color.Transparent),
                ) {
                    posterContent()
                }
            } else {
                var posterFocused by remember { mutableStateOf(false) }
                Box(
                    posterModifier
                        .focusRequester(posterFocus)
                        .onFocusChanged {
                            posterFocused = it.isFocused
                            if (it.isFocused) {
                                focusSection = CollectionFocusSection.POSTER
                            } else if (focusSection == CollectionFocusSection.POSTER) {
                                focusSection = null
                            }
                        }.onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                memberFocusEpoch++
                                true
                            } else {
                                false
                            }
                        }.border(
                            3.dp,
                            if (posterFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        ).focusable()
                        .testTag("collectionPoster"),
                    contentAlignment = Alignment.Center,
                ) { posterContent() }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    state.details?.name ?: stringResource(R.string.collections_details_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                state.details?.overview?.let { Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis) }
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
                val columns = 6
                val memberFocusKey =
                    state.focusedKey?.takeIf { key -> movies.any { "tmdb:movie:${it.id}" == key } }
                        ?: movies.firstOrNull()?.let { "tmdb:movie:${it.id}" }
                val similarFocusTargetId =
                    (state.similarFocusedId ?: lastSimilarFocusId)?.takeIf { id -> state.similar.any { it.id == id } }
                LaunchedEffect(state.details, state.similar.isNotEmpty(), destination.entryToken) {
                    if (!entryFocusRestored) {
                        if (state.similarFocusedId != null && state.similar.isEmpty()) return@LaunchedEffect
                        entryFocusRestored = true
                        when {
                            state.similarFocusedId != null -> similarFocusEpoch++
                            else -> memberFocusEpoch++
                        }
                    }
                }
                CardGrid(
                    pager = movies,
                    totalItems = movies.size,
                    focusAnchorKey = memberFocusKey,
                    backToTopOnBack = false,
                    itemKey = { "tmdb:movie:${movies[it].id}" },
                    onClickItem = { _, item ->
                        backShortcutArmed = true
                        viewModel.open(item)
                    },
                    onLongClickItem = { _, item ->
                        backShortcutArmed = true
                        quickMovieRequest.open(item, onOpenDetails = { viewModel.open(item) })
                    },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocus,
                    initialPosition = state.focusedIndex,
                    columns = columns,
                    focusRequestEpoch = memberFocusEpoch,
                    positionCallback = { _, index ->
                        focusSection = CollectionFocusSection.MOVIES
                        viewModel.position(index)
                    },
                    onGridFocusChanged = { hasFocus ->
                        if (!hasFocus && focusSection != CollectionFocusSection.POSTER) focusSection = null
                    },
                    onFirstRowUp = {
                        collectionMovieUpTargetsPoster(focusSection) && posterFocus.tryRequestFocus()
                    },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    footerFocusRequester = similarFocus.takeIf { state.similar.isNotEmpty() },
                    autoScrollToFooterOnRequester = false,
                    footerFocusRequestEpoch = similarFocusEpoch,
                    useFooterAsFocusRestorer = state.similarFocusedId != null,
                    prepareFooterFocus = {
                        val targetIndex = state.similar.indexOfFirst { it.id == similarFocusTargetId }.coerceAtLeast(0)
                        similarListState.scrollToItem(targetIndex)
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 24.dp, end = 40.dp)
                            .onPreviewKeyEvent {
                                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    focusSection == CollectionFocusSection.SIMILAR && it.key == Key.DirectionUp -> {
                                        memberFocusEpoch++
                                        true
                                    }

                                    it.key == Key.DirectionDown &&
                                        collectionMovieDownTargetsSimilar(
                                            focusSection,
                                            state.focusedIndex,
                                            movies.size,
                                            columns,
                                            state.similar.isNotEmpty(),
                                        ) -> {
                                        similarFocusEpoch++
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            },
                    cardContent = { (item, _, onClick, onLongClick, _, cardModifier) ->
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                            showOverlay = false,
                            showAvailableIndicator = false,
                            artworkAlpha = collectionMovieArtworkAlpha(item?.availability),
                            useFallbackText = true,
                            width = Dp.Unspecified,
                        )
                    },
                    footerContent =
                        if (state.owner == destination.owner &&
                            (state.similarLoading || state.similar.isNotEmpty() || state.similarReadError || state.similarTriggerError)
                        ) {
                            {
                                Column {
                                    if (state.similar.isNotEmpty()) {
                                        SimilarCollectionsRow(
                                            collections = state.similar,
                                            selectedId = state.similarFocusedId,
                                            focusTargetId = similarFocusTargetId,
                                            restoreSelectedFocus = focusSection == null && state.similarFocusedId != null,
                                            focusRequester = similarFocus,
                                            listState = similarListState,
                                            onFocus = {
                                                focusSection = CollectionFocusSection.SIMILAR
                                                lastSimilarFocusId = it
                                                viewModel.focusSimilar(it)
                                            },
                                            onOpen = {
                                                backShortcutArmed = true
                                                viewModel.openSimilar(it)
                                            },
                                        )
                                    }
                                    if (state.similarLoading && state.similar.isEmpty()) {
                                        LoadingPage(Modifier.fillMaxWidth().height(120.dp), focusEnabled = false)
                                    }
                                    if (state.similarReadError || state.similarTriggerError) {
                                        Text(stringResource(R.string.collections_similar_unavailable), Modifier.padding(24.dp))
                                    }
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
private fun CollectionDetailsPosterContent(
    name: String?,
    image: String?,
    available: Int?,
    total: Int?,
    showCount: Boolean,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            AsyncImage(model = image, contentDescription = name, modifier = Modifier.fillMaxSize())
        } else {
            Text(stringResource(R.string.collections_no_artwork))
        }
        if (showCount) CollectionCompletionBadge(available, total, Modifier.align(Alignment.TopEnd))
    }
}

internal fun collectionMovieArtworkAlpha(availability: SeerrAvailability?): Float =
    when (availability) {
        SeerrAvailability.PENDING,
        SeerrAvailability.PROCESSING,
        SeerrAvailability.BLOCKLISTED,
        SeerrAvailability.DELETED,
        -> .45f

        else -> 1f
    }

@Composable
private fun SimilarCollectionsRow(
    collections: List<CollectionDetailsData>,
    selectedId: Int?,
    focusTargetId: Int?,
    restoreSelectedFocus: Boolean,
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
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp),
            modifier = Modifier.fillMaxWidth().height(235.dp),
        ) {
            items(collections, key = { it.id }) { item ->
                if (restoreSelectedFocus && selectedId == item.id) {
                    // Entry restoration only. A selected card must not reclaim focus during Similar -> member traversal.
                    LaunchedEffect(item.id) { focusRequester.tryRequestFocus() }
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
                            .then(
                                if (focusTargetId == item.id ||
                                    (focusTargetId == null && item.id == collections.first().id)
                                ) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                },
                            ).onFocusChanged { if (it.isFocused) onFocus(item.id) },
                )
            }
        }
    }
}
