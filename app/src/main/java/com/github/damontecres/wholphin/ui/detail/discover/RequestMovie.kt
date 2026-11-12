package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun RequestMovieDialog(
    loading: LoadingState,
    data: SeerrRequestData,
    requestEnabled: Boolean,
    movie: MovieDetails?,
    onSubmit: (MovieRequest) -> Unit,
    onDismissRequest: () -> Unit,
    confirmationReady: Boolean = true,
    dialogModifier: Modifier = Modifier,
    posterUrl: String? = null,
    rating: DiscoverRating? = null,
    fallbackItem: DiscoverItem? = null,
    confirmAction: () -> Boolean = { true },
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = dialogModifier,
    ) {
        MovieRequestDialogContent(
            loading = loading,
            data = data,
            requestEnabled = requestEnabled,
            movie = movie,
            posterUrl = posterUrl,
            rating = rating,
            fallbackItem = fallbackItem,
            onSubmit = onSubmit,
            confirmAction = confirmAction,
            confirmationReady = confirmationReady,
        )
    }
}

@Composable
fun ChooseProfile(
    selectedProfile: SeerrProfile,
    profiles: List<SeerrProfile>,
    onClickProfile: (SeerrProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultStr = stringResource(R.string.is_default)
    val qualityProfileStr = stringResource(R.string.quality_profile)
    var showProfileDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text(qualityProfileStr)
        },
        supportingContent = {
            val text =
                remember(selectedProfile) {
                    if (selectedProfile.default) "${selectedProfile.name} ($defaultStr)" else selectedProfile.name
                }
            Text(text)
        },
        onClick = { showProfileDialog = true },
        modifier = modifier,
    )
    if (showProfileDialog) {
        val params =
            remember {
                val items =
                    profiles.map {
                        DialogItem(
                            headlineContent = {
                                Text(it.name)
                            },
                            supportingContent = {
                                if (it.default) {
                                    Text(defaultStr)
                                }
                            },
                            leadingContent = {
                                SelectedLeadingContent(it == selectedProfile)
                            },
                            onClick = { onClickProfile.invoke(it) },
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = qualityProfileStr,
                    items = items,
                )
            }
        DialogPopup(
            params = params,
            onDismissRequest = { showProfileDialog = false },
            dismissOnClick = true,
            elevation = 3.dp,
        )
    }
}

@Composable
fun ChooseFolder(
    selectedFolder: SeerrRootFolder,
    folders: List<SeerrRootFolder>,
    onClickFolder: (SeerrRootFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultStr = stringResource(R.string.is_default)
    val rootFolderStr = stringResource(R.string.root_folder)
    var showFolderDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text(rootFolderStr)
        },
        supportingContent = {
            val text =
                remember(selectedFolder) {
                    if (selectedFolder.default) "${selectedFolder.path} ($defaultStr)" else selectedFolder.path
                }
            Text(text)
        },
        trailingContent = {
            Text(selectedFolder.freeSpace)
        },
        onClick = { showFolderDialog = true },
        modifier = modifier,
    )
    if (showFolderDialog) {
        val params =
            remember {
                val items =
                    folders.map {
                        DialogItem(
                            headlineContent = {
                                Text(it.path)
                            },
                            supportingContent = {
                                if (it.default) {
                                    Text(defaultStr)
                                }
                            },
                            trailingContent = {
                                Text(it.freeSpace)
                            },
                            leadingContent = {
                                SelectedLeadingContent(it == selectedFolder)
                            },
                            onClick = { onClickFolder.invoke(it) },
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = rootFolderStr,
                    items = items,
                )
            }
        DialogPopup(
            params = params,
            onDismissRequest = { showFolderDialog = false },
            dismissOnClick = true,
            elevation = 3.dp,
        )
    }
}

@PreviewTvSpec
@Composable
fun MovieRequestPreview() {
    WholphinTheme {
        MovieRequestDialogContent(
            loading = LoadingState.Success,
            data =
                SeerrRequestData(
                    profiles =
                        listOf(
                            SeerrProfile(1, MOVIE_PROFILE_1080P_HDR, true),
                            SeerrProfile(2, MOVIE_PROFILE_2160P_HDR, false),
                        ),
                    serverId = 11,
                    rootFolders =
                        listOf(
                            SeerrRootFolder(1, "/movies", "400GB", true),
                        ),
                ),
            requestEnabled = true,
            movie = MovieDetails(id = 1, title = "Movie Name", releaseDate = "2026"),
            posterUrl = null,
            rating = null,
            confirmationReady = true,
            onSubmit = {},
            confirmAction = { true },
        )
    }
}
