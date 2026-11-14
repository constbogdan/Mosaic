package com.github.damontecres.wholphin.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.detail.discover.MOVIE_PROFILE_1080P_HDR
import com.github.damontecres.wholphin.ui.detail.discover.MOVIE_PROFILE_2160P_HDR
import com.github.damontecres.wholphin.ui.detail.discover.QualityButton
import com.github.damontecres.wholphin.ui.detail.discover.movieRequestForQuality

@Composable
internal fun CollectionRequestDialog(
    state: CollectionRequestState,
    onDismiss: () -> Unit,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = !state.submitting,
                dismissOnClickOutside = !state.submitting,
                usePlatformDefaultWidth = false,
            ),
        modifier = Modifier.fillMaxWidth(.9f).fillMaxHeight(.85f).testTag("collectionRequestDialog"),
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.collections_request), style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onToggleAll, enabled = !state.submitting && !state.sessionInvalid && state.eligibleCount > 0) {
                    Text(stringResource(if (state.allSelected) R.string.collections_deselect_all else R.string.collections_select_all))
                }
                Text(stringResource(R.string.collections_selected_count, state.selectedCount))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(90.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("collectionRequestGrid"),
            ) {
                items(state.movies, key = { "tmdb:movie:${it.item.id}" }) { movie ->
                    Box(Modifier.width(90.dp)) {
                        DiscoverItemCard(
                            item = movie.item,
                            onClick = { if (!state.submitting && !state.sessionInvalid && movie.selectable) onToggle(movie.item.id) },
                            onLongClick = {},
                            modifier = Modifier.alpha(if (movie.selectable) 1f else .5f).testTag("collectionRequestMovie:${movie.item.id}"),
                            width = 86.dp,
                            showOverlay = false,
                            useFallbackText = true,
                            compactLabels = true,
                        )
                        if (movie.selected) {
                            androidx.tv.material3.Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.collections_selected),
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            )
                        }
                        movie.outcome?.let { outcome ->
                            Text(
                                text = outcomeLabel(outcome),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }
            if (state.submitting) Text(stringResource(R.string.collections_submitting))
            if (state.sessionInvalid) Text(stringResource(R.string.collections_session_changed))
            if (state.profileError) Text(stringResource(R.string.collections_profiles_unavailable))
            if (state.submitted > 0 || state.failed > 0) {
                Text(stringResource(R.string.collections_request_summary, state.submitted, state.failed))
            }
            val first =
                state.movies
                    .firstOrNull { it.selected && it.selectable }
                    ?.item
                    ?.id
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                QualityButton(
                    R.drawable.request_1080p,
                    MOVIE_PROFILE_1080P_HDR,
                    first != null && !state.loadingProfiles && !state.profileError && !state.submitting && !state.sessionInvalid &&
                        movieRequestForQuality(state.profiles, first, MOVIE_PROFILE_1080P_HDR) != null,
                    onFocused = {},
                    onClick = { onSubmit(MOVIE_PROFILE_1080P_HDR) },
                )
                QualityButton(
                    R.drawable.request_2160p,
                    MOVIE_PROFILE_2160P_HDR,
                    first != null && !state.loadingProfiles && !state.profileError && !state.submitting && !state.sessionInvalid &&
                        movieRequestForQuality(state.profiles, first, MOVIE_PROFILE_2160P_HDR) != null,
                    onFocused = {},
                    onClick = { onSubmit(MOVIE_PROFILE_2160P_HDR) },
                )
                if (!state.submitting) Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun outcomeLabel(outcome: CollectionRequestOutcome): String =
    when (outcome) {
        CollectionRequestOutcome.REQUESTED -> stringResource(R.string.collections_requested)
        CollectionRequestOutcome.NO_LONGER_ELIGIBLE -> stringResource(R.string.collections_no_longer_eligible)
        CollectionRequestOutcome.FAILED -> stringResource(R.string.collections_failed)
        CollectionRequestOutcome.UNKNOWN_RESULT -> stringResource(R.string.collections_unknown_result)
        CollectionRequestOutcome.NOT_ATTEMPTED -> stringResource(R.string.collections_not_attempted)
    }
