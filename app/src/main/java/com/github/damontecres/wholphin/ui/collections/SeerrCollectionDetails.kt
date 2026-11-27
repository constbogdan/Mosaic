package com.github.damontecres.wholphin.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.rememberCustomPageFocusOwner
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalActiveDrawerFocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus

internal enum class CollectionFocusSection { POSTER, MOVIES, SIMILAR }

internal val collectionHeaderHeight = 226.dp
internal val collectionOverviewPosterWidth = 115.dp

internal fun collectionMovieUpTargetsPoster(section: CollectionFocusSection?): Boolean = section == CollectionFocusSection.MOVIES

internal fun collectionEntryFocusSection(selection: CollectionPreviewSelection): CollectionFocusSection =
    when (selection) {
        CollectionPreviewSelection.Collection -> CollectionFocusSection.POSTER
        is CollectionPreviewSelection.Movie -> CollectionFocusSection.MOVIES
        is CollectionPreviewSelection.Similar -> CollectionFocusSection.SIMILAR
    }

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
    val drawerFocusRequester = LocalActiveDrawerFocusRequester.current
    val gridFocus = remember { FocusRequester() }
    val posterFocus = remember { FocusRequester() }
    val similarFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val similarListState = rememberLazyListState()
    var requestWasOpen by remember { mutableStateOf(false) }
    var returnFocusPending by remember { mutableStateOf(false) }
    var retryHadFocus by remember(destination.entryToken) { mutableStateOf(false) }
    var focusSection by remember(destination.entryToken) { mutableStateOf<CollectionFocusSection?>(null) }
    var backShortcutArmed by rememberSaveable(destination.entryToken) { mutableStateOf(state.similarFocusedId != null) }
    var lastSimilarFocusId by rememberSaveable(destination.entryToken) { mutableStateOf<Int?>(null) }
    var similarEntryFocused by remember(destination.entryToken) { mutableStateOf(false) }
    var memberFocusEpoch by remember(destination.entryToken) { mutableStateOf(0L) }
    var similarFocusEpoch by remember(destination.entryToken) { mutableStateOf(0L) }
    var overviewFocusEpoch by remember(destination.entryToken) { mutableStateOf(0L) }
    var entryHandoffStarted by remember(destination.entryToken) { mutableStateOf(false) }
    var previewFocus by rememberSaveable(destination.entryToken, destination.owner, stateSaver = CollectionPreviewFocusStateSaver) {
        mutableStateOf(CollectionPreviewFocusState())
    }
    var browsingRequested by rememberSaveable(destination.entryToken) { mutableStateOf(false) }
    var enteringMembers by remember(destination.entryToken) { mutableStateOf(false) }
    val entryLifecycleOwner = LocalLifecycleOwner.current
    var entryResumed by remember(destination.entryToken, entryLifecycleOwner) {
        mutableStateOf(entryLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { entryResumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { entryResumed = false }
    val posterActionable =
        state.owner == destination.owner && state.details != null && !state.loading && !state.error && !state.denied &&
            !state.requestEligibilityLoading &&
            state.requestEligibility.values.any { it == CollectionRequestEligibility.ELIGIBLE }
    val counts =
        collectionDetailCounts(
            state.details?.availableMovies,
            state.details?.totalMovies,
            destination.availableMovies,
            destination.totalMovies,
        )
    val unreleasedPosterFilter = remember { ColorMatrixColorFilter(collectionUnreleasedPosterMatrix()) }
    val currentDetails = state.details?.takeIf { state.owner == destination.owner && state.collectionId == destination.collectionId }
    val isTop = viewModel.isCollectionTop(destination)
    val backdropTarget = currentDetails?.let { collectionBackdropTarget(it, state.similar, previewFocus.selection) }
    LaunchedEffect(isTop, backdropTarget) {
        if (isTop) viewModel.updateBackdrop(backdropTarget)
    }
    val preview =
        currentDetails?.let { collectionPreviewData(it, state.similar, previewFocus.selection) }
            ?: CollectionPreviewData(CollectionPreviewKind.COLLECTION, stringResource(R.string.collections_details_title), null, null)
    val showingOverview = previewFocus.selection == CollectionPreviewSelection.Collection
    val entryTarget = collectionEntryFocusSection(previewFocus.selection)
    var memberTargetReady by remember(destination.entryToken, state.focusedKey) { mutableStateOf(false) }
    var similarTargetReady by remember(destination.entryToken, state.similarFocusedId) { mutableStateOf(false) }
    val entryTargetRequester =
        if (state.error && currentDetails == null) {
            retryFocus
        } else {
            when (entryTarget) {
                CollectionFocusSection.POSTER -> posterFocus
                CollectionFocusSection.MOVIES -> gridFocus
                CollectionFocusSection.SIMILAR -> similarFocus
            }
        }
    // Claim focus before collection data arrives. The loading screen otherwise has no
    // content focus owner and the navigation drawer becomes the fallback target.
    val pageFocusModifier =
        rememberCustomPageFocusOwner(
            isTop = isTop,
            contentReady =
                (state.error && currentDetails == null) ||
                    (
                        currentDetails != null &&
                            when (entryTarget) {
                                CollectionFocusSection.POSTER -> true
                                CollectionFocusSection.MOVIES -> memberTargetReady
                                CollectionFocusSection.SIMILAR -> similarTargetReady
                            }
                    ),
            mayFocusGrid = entryResumed,
            gridFocusRequester = entryTargetRequester,
        )
    val restoreOverview: () -> Unit = {
        enteringMembers = false
        browsingRequested = false
        previewFocus = previewFocus.posterFocused()
        overviewFocusEpoch++
    }
    val enterMembers: () -> Boolean = {
        val first = currentDetails?.movies?.firstOrNull()
        if (first == null) {
            false
        } else {
            // Keep the overview focus target mounted until CardGrid owns focus.
            // Removing the poster first lets navigation fall through to the drawer.
            enteringMembers = true
            viewModel.position(0)
            memberFocusEpoch++
            true
        }
    }
    LaunchedEffect(destination.collectionId, destination.owner, destination.entryToken) {
        viewModel.show(destination.collectionId, destination.owner, destination.entryToken)
    }
    LaunchedEffect(state.owner, state.details) {
        if (state.owner != destination.owner || state.details == null) {
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
        restoreOverview()
    }
    LaunchedEffect(overviewFocusEpoch) {
        if (overviewFocusEpoch > 0) {
            withFrameNanos { }
            posterFocus.tryRequestFocus()
        }
    }
    LaunchedEffect(state.details, state.similar.isNotEmpty(), destination.entryToken, entryResumed) {
        if (!entryHandoffStarted && entryResumed && state.owner == destination.owner &&
            state.collectionId == destination.collectionId && state.details != null
        ) {
            if (state.similarFocusedId != null && state.similar.isEmpty()) return@LaunchedEffect
            entryHandoffStarted = true
            val target = collectionEntryFocusSection(previewFocus.selection)
            when (target) {
                CollectionFocusSection.POSTER -> overviewFocusEpoch++
                CollectionFocusSection.MOVIES -> memberFocusEpoch++
                CollectionFocusSection.SIMILAR -> similarFocusEpoch++
            }
        }
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
            if (state.details != null) restoreOverview()
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
            .then(pageFocusModifier)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    if (it.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                        backShortcutArmed = true
                    }
                }
                false
            },
    ) {
        if (showingOverview) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(collectionHeaderHeight)
                        .padding(start = 24.dp, top = 20.dp, end = 40.dp, bottom = 12.dp)
                        .testTag("collectionOverviewSection"),
            ) {
                val overviewName = currentDetails?.name ?: stringResource(R.string.collections_details_title)
                val requestLabel = stringResource(R.string.collections_request)
                CollectionCard(
                    name = overviewName,
                    posterUrl = currentDetails?.posterUrl ?: currentDetails?.backdropUrl,
                    available = counts?.available,
                    total = counts?.total,
                    onClick = { if (posterActionable) viewModel.openRequest() },
                    onLongClick = {},
                    modifier = Modifier.width(collectionOverviewPosterWidth).testTag("collectionOverviewCard"),
                    showTitles = false,
                    cardModifier =
                        Modifier
                            .focusRequester(posterFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusSection = CollectionFocusSection.POSTER
                                    previewFocus = previewFocus.posterFocused()
                                } else if (focusSection == CollectionFocusSection.POSTER) {
                                    focusSection = null
                                }
                            }.onPreviewKeyEvent {
                                it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && enterMembers()
                            }.semantics { contentDescription = if (posterActionable) requestLabel else overviewName }
                            .testTag(if (posterActionable) "collectionRequestPoster" else "collectionPoster"),
                )
                CollectionDetailsOverview(
                    title = overviewName,
                    overview = currentDetails?.overview,
                    metadata = currentDetails?.movies?.let(::collectionMemberMetadata) ?: CollectionMemberMetadata(emptyList(), null),
                    genreNames = state.movieGenreNames,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            CollectionDetailsPreview(
                preview = preview,
                genreNames = state.movieGenreNames,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(collectionHeaderHeight)
                        .padding(start = 24.dp, top = 20.dp, end = 40.dp, bottom = 12.dp)
                        .testTag("collectionBrowsingPreview"),
            )
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
            }

            state.details?.movies?.isEmpty() == true -> {
                Text(stringResource(R.string.collections_details_empty), Modifier.padding(24.dp))
            }

            state.details != null -> {
                val movies = state.details.movies
                val hasAvailableReleasedMember = collectionHasAvailableReleasedMember(movies)
                val columns = 6
                val memberFocusKey =
                    (if (enteringMembers) null else state.focusedKey)?.takeIf { key -> movies.any { "tmdb:movie:${it.id}" == key } }
                        ?: movies.firstOrNull()?.let { "tmdb:movie:${it.id}" }
                val similarFocusTargetId =
                    (state.similarFocusedId ?: lastSimilarFocusId)?.takeIf { id -> state.similar.any { it.id == id } }
                CardGrid(
                    pager = movies,
                    totalItems = movies.size,
                    focusAnchorKey = memberFocusKey,
                    backToTopOnBack = false,
                    itemKey = { "tmdb:movie:${movies[it].id}" },
                    onClickItem = { _, item ->
                        backShortcutArmed = true
                        if (collectionMemberActionable(item)) viewModel.open(item)
                    },
                    onLongClickItem = { _, item ->
                        backShortcutArmed = true
                        if (collectionMemberActionable(item)) {
                            quickMovieRequest.open(item, onOpenDetails = { viewModel.open(item) })
                        }
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
                        if (browsingRequested) {
                            movies.getOrNull(index)?.let { previewFocus = previewFocus.memberFocused(it.id) }
                        }
                    },
                    onGridFocusChanged = { hasFocus ->
                        if (hasFocus && enteringMembers) {
                            enteringMembers = false
                            browsingRequested = true
                            movies.firstOrNull()?.let { previewFocus = previewFocus.memberFocused(it.id) }
                        }
                        if (!hasFocus && focusSection != CollectionFocusSection.POSTER) focusSection = null
                    },
                    onFocusTargetReady = { memberTargetReady = true },
                    onFirstRowUp = {
                        if (collectionMovieUpTargetsPoster(focusSection)) {
                            restoreOverview()
                            true
                        } else {
                            false
                        }
                    },
                    showJumpButtons = false,
                    showLetterButtons = false,
                    footerFocusRequester = similarFocus.takeIf { state.similar.isNotEmpty() },
                    autoScrollToFooterOnRequester = false,
                    footerFocusRequestEpoch = similarFocusEpoch,
                    useFooterAsFocusRestorer = state.similarFocusedId != null,
                    prepareFooterFocus = {
                        val targetIndex = state.similar.indexOfFirst { it.id == similarFocusTargetId }.coerceAtLeast(0)
                        if (similarSelectionNeedsScroll(similarListState.firstVisibleItemIndex, columns, targetIndex)) {
                            similarListState.scrollToItem(targetIndex)
                        }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 24.dp, end = 40.dp)
                            .onPreviewKeyEvent {
                                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when {
                                    focusSection == CollectionFocusSection.SIMILAR && it.key == Key.DirectionUp -> {
                                        (previewFocus.lastMemberId ?: movies.getOrNull(state.focusedIndex)?.id)?.let { memberId ->
                                            previewFocus = previewFocus.returningToMember(memberId)
                                        }
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
                        val actionable = item?.let { collectionMemberActionable(it) } == true
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier.testTag("collectionMemberCard"),
                            showOverlay = false,
                            showAvailableIndicator = false,
                            artworkAlpha =
                                collectionMovieArtworkAlpha(
                                    item?.availability,
                                    actionable,
                                    hasAvailableReleasedMember,
                                ),
                            artworkColorFilter = if (actionable) null else unreleasedPosterFilter,
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
                                            restoreSelectedFocus =
                                                !similarEntryFocused && focusSection == null && state.similarFocusedId != null,
                                            focusRequester = similarFocus,
                                            listState = similarListState,
                                            columns = columns,
                                            drawerFocusRequester = drawerFocusRequester,
                                            onFocus = {
                                                similarEntryFocused = true
                                                browsingRequested = true
                                                focusSection = CollectionFocusSection.SIMILAR
                                                lastSimilarFocusId = it
                                                viewModel.focusSimilar(it)
                                                previewFocus = previewFocus.similarFocused(it)
                                            },
                                            onOpen = {
                                                backShortcutArmed = true
                                                viewModel.openSimilar(it)
                                            },
                                            onFocusTargetReady = { similarTargetReady = true },
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
        // Keep the focused Retry node mounted, but invisible and without layout space,
        // while a retry loads. Removing it before the poster is ready would let focus
        // fall through to the drawer's avatar.
        if (state.error || (retryHadFocus && focusSection == null)) {
            Button(
                onClick = viewModel::retry,
                modifier =
                    Modifier
                        .focusRequester(retryFocus)
                        .onFocusChanged { if (it.isFocused) retryHadFocus = true }
                        .then(if (state.error) Modifier else Modifier.height(0.dp).alpha(0f)),
            ) { Text(stringResource(R.string.retry)) }
        }
    }
}

internal fun collectionMovieArtworkAlpha(
    availability: SeerrAvailability?,
    actionable: Boolean,
    hasAvailableReleasedMember: Boolean,
): Float = if (actionable && hasAvailableReleasedMember && availability != SeerrAvailability.AVAILABLE) .45f else 1f

@Composable
private fun SimilarCollectionsRow(
    collections: List<CollectionDetailsData>,
    selectedId: Int?,
    focusTargetId: Int?,
    restoreSelectedFocus: Boolean,
    focusRequester: FocusRequester,
    listState: LazyListState,
    columns: Int,
    drawerFocusRequester: FocusRequester?,
    onFocus: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onFocusTargetReady: () -> Unit,
) {
    var initialSelectionRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedId, collections) {
        val index = collections.indexOfFirst { it.id == selectedId }
        if (!initialSelectionRestored && index >= 0) {
            if (similarSelectionNeedsScroll(listState.firstVisibleItemIndex, columns, index)) {
                listState.scrollToItem(index)
            }
            initialSelectionRestored = true
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 16.dp
        val cardWidth = similarPosterWidth(maxWidth, columns, spacing)
        // CardGrid already spaces its footer from the last member row by 16.dp.
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.collections_recommended),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                modifier = Modifier.fillMaxWidth().height(cardWidth / AspectRatios.TALL + 72.dp),
            ) {
                items(collections, key = { it.id }) { item ->
                    if (restoreSelectedFocus && selectedId == item.id) {
                        // Entry restoration only. A selected card must not reclaim focus during Similar -> member traversal.
                        LaunchedEffect(item.id) { focusRequester.tryRequestFocus() }
                    }
                    CollectionCard(
                        name = item.name,
                        posterUrl = item.posterUrl,
                        available = item.availableMovies,
                        total = item.totalMovies,
                        onClick = { onOpen(item.id) },
                        onLongClick = {},
                        modifier = Modifier.width(cardWidth).testTag("similarCollectionCard"),
                        cardModifier =
                            Modifier
                                .then(
                                    if (item.id == collections.first().id && drawerFocusRequester != null) {
                                        Modifier.focusProperties { left = drawerFocusRequester }
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (focusTargetId == item.id ||
                                        (focusTargetId == null && item.id == collections.first().id)
                                    ) {
                                        Modifier.focusRequester(focusRequester)
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (focusTargetId == item.id ||
                                        (focusTargetId == null && item.id == collections.first().id)
                                    ) {
                                        Modifier.onGloballyPositioned { onFocusTargetReady() }
                                    } else {
                                        Modifier
                                    },
                                ).onFocusChanged { if (it.isFocused) onFocus(item.id) },
                    )
                }
            }
        }
    }
}
