package com.github.damontecres.wholphin.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R

@Composable
internal fun CollectionMovieIndicator(
    state: CollectionMovieLinkState,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val link = state.link
    if (link == null && !(state.error && state.retryable)) return
    Column(modifier = modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.collection), style = MaterialTheme.typography.titleLarge)
        Button(onClick = if (link != null) onOpen else onRetry) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (link?.posterUrl != null) {
                    AsyncImage(
                        model = link.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.width(48.dp).height(72.dp),
                    )
                }
                Column {
                    Text(
                        link?.name ?: stringResource(R.string.collections_unavailable),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(if (link != null) R.string.collections_view_collection else R.string.retry),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
