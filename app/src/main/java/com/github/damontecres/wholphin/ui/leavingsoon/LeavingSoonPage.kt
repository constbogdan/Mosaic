package com.github.damontecres.wholphin.ui.leavingsoon

import androidx.activity.compose.BackHandler
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.LeavingSoonCategory
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.discover.LocalQuickMovieRequest
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val tabs =
        remember {
            listOf(R.string.leaving_soon_all, R.string.leaving_soon_movies, R.string.leaving_soon_series).map(::TabDetails)
        }
    val refreshFocusRequester = remember { FocusRequester() }
    var toolbarHasFocus by remember(state.owner) { mutableStateOf(false) }
    var gridHasFocus by remember(state.owner, state.category) { mutableStateOf(false) }
    BackHandler(enabled = gridHasFocus && state.entries.isNotEmpty()) { refreshFocusRequester.tryRequestFocus() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TabbedPage(
        selectedTabIndex = state.category.ordinal,
        tabs = tabs,
        updateSelectedTabIndex = { viewModel.category(LeavingSoonCategory.entries[it]) },
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
                Text(stringResource(R.string.leaving_soon), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                val refreshLabel = stringResource(R.string.leaving_soon_refresh)
                ExpandableFaButton(
                    title = R.string.leaving_soon_refresh,
                    iconStringRes = R.string.fa_clock,
                    onClick = viewModel::reload,
                    enabled = state.owner != null,
                    modifier = Modifier.focusRequester(refreshFocusRequester).semantics { contentDescription = refreshLabel },
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
                val message = if (state.owner == null) R.string.leaving_soon_connection_required else R.string.leaving_soon_empty
                if (state.error == null) Text(stringResource(message), Modifier.padding(16.dp))
                LaunchedEffect(state.owner, state.category) {
                    if (!toolbarHasFocus) tab.tabFocusRequester.tryRequestFocus()
                }
            } else {
                key(state.owner, state.category, state.gridEpoch) {
                    LaunchedEffect(Unit) {
                        if (!toolbarHasFocus) tab.contentFocusRequester.tryRequestFocus()
                    }
                    CardGrid(
                        pager = state.entries,
                        totalItems = state.totalResults,
                        positionOffset = (state.firstPage - 1) * 20,
                        alignWindowToAbsoluteRows = true,
                        focusAnchorKey = state.focusedKey,
                        onGridFocusChanged = { gridHasFocus = it },
                        onFirstRowUp = { refreshFocusRequester.tryRequestFocus() },
                        backToTopOnBack = false,
                        itemKey = { state.entries[it].key },
                        onClickItem = { _, entry -> viewModel.open(entry) },
                        onLongClickItem = { _, entry ->
                            if (entry.hasTmdbIdentity && entry.item.type == SeerrItemType.MOVIE) {
                                quickMovieRequest.open(entry.item, onOpenDetails = { viewModel.open(entry) })
                            }
                        },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = tab.contentFocusRequester,
                        initialPosition = state.focusedIndex,
                        positionCallback = { columns, index -> viewModel.position(index, columns) },
                        showJumpButtons = false,
                        showLetterButtons = false,
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
                                width = Dp.Unspecified,
                                bottomBadge = badge,
                            )
                        },
                    )
                }
            }
        }
    }
}
