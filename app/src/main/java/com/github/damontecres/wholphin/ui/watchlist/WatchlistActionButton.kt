package com.github.damontecres.wholphin.ui.watchlist

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.services.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistActionViewModel
    @Inject
    constructor(
        val repository: WatchlistRepository,
    ) : ViewModel()

/** Details are a bounded single-title consumer, not a second membership authority. */
@Composable
fun WatchlistActionButton(
    mediaKey: MediaKey.Catalog,
    title: String,
    viewModel: WatchlistActionViewModel? = null,
) {
    if (LocalInspectionMode.current) return
    val repository = (viewModel ?: hiltViewModel()).repository
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember(mediaKey, state.owner) { mutableStateOf<String?>(null) }
    var reading by remember(mediaKey, state.owner) { mutableStateOf(false) }

    suspend fun read() {
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
    Column {
        Button(
            enabled = !reading && mediaKey !in state.busy,
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
        ) {
            Text(
                when (member) {
                    WatchlistMembership.MEMBER -> stringResource(R.string.watchlist_remove)
                    WatchlistMembership.NOT_MEMBER -> stringResource(R.string.watchlist_add)
                    WatchlistMembership.UNKNOWN -> stringResource(R.string.watchlist_check)
                },
            )
        }
        error?.let { Text(it) }
    }
}
