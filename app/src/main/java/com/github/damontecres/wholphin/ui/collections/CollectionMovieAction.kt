package com.github.damontecres.wholphin.ui.collections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton

@Composable
internal fun CollectionMovieAction(
    state: CollectionMovieLinkState,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val link = state.link
    if (link == null && !(state.error && state.retryable)) return
    ExpandablePlayButton(
        title = link?.name ?: stringResource(R.string.retry),
        icon = painterResource(R.drawable.ic_animated_images),
        onClick = if (link != null) onOpen else onRetry,
        modifier = modifier.testTag("collectionMovieAction"),
    )
}
