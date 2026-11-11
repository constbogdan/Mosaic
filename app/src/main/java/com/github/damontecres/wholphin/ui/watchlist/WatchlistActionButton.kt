package com.github.damontecres.wholphin.ui.watchlist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.services.WatchlistRepository
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class WatchlistActionViewModel
    @Inject
    constructor(
        val repository: WatchlistRepository,
    ) : ViewModel()

@DrawableRes
internal fun watchlistBookmarkIcon(member: WatchlistMembership): Int =
    if (member == WatchlistMembership.MEMBER) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline

/** Details are a bounded single-title consumer, not a second membership authority. */
@Composable
fun WatchlistActionButton(
    mediaKey: MediaKey.Catalog,
    title: String,
    modifier: Modifier = Modifier,
    viewModel: WatchlistActionViewModel? = null,
) {
    if (LocalInspectionMode.current) return
    val repository = (viewModel ?: hiltViewModel()).repository
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember(mediaKey, state.owner) { mutableStateOf<String?>(null) }
    var reading by remember(mediaKey, state.owner) { mutableStateOf(false) }

    suspend fun read() {
        if (reading) return
        reading = true
        try {
            repository.membership(setOf(mediaKey), refresh = error != null)
            error = null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = "Watchlist membership unavailable. Retry without submitting an unverified change."
        } finally {
            reading = false
        }
    }
    LaunchedEffect(mediaKey, state.owner) { if (state.owner != null) read() }
    if (state.owner == null) return
    val member = state.membership[mediaKey] ?: WatchlistMembership.UNKNOWN
    val label =
        when {
            reading -> R.string.watchlist_unavailable
            member == WatchlistMembership.UNKNOWN && error != null -> R.string.watchlist_retry_action
            member == WatchlistMembership.UNKNOWN -> R.string.watchlist_unavailable
            member == WatchlistMembership.MEMBER -> R.string.watchlist_remove_action
            else -> R.string.watchlist_add_action
        }
    val description = stringResource(label)
    Column {
        ExpandablePlayButton(
            title = label,
            resume = Duration.ZERO,
            icon = painterResource(watchlistBookmarkIcon(member)),
            modifier = modifier.semantics { contentDescription = description },
            enabled = !reading && (member != WatchlistMembership.UNKNOWN || error != null) && mediaKey !in state.busy,
            onClick = {
                scope.launch {
                    if (member == WatchlistMembership.UNKNOWN) {
                        read()
                    } else {
                        try {
                            error =
                                if (repository.setMembership(
                                        mediaKey,
                                        member != WatchlistMembership.MEMBER,
                                        title,
                                    )
                                ) {
                                    null
                                } else {
                                    "Watchlist change was not confirmed."
                                }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            error = "Watchlist change could not be confirmed. Check membership before retrying."
                        }
                    }
                }
            },
        )
        error?.let { Text(it) }
    }
}
