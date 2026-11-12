package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.ui.detail.discover.MovieRequest

/** The single movie request payload used by details and card Quick Request. */
suspend fun SeerrApiClient.submitMovieRequest(request: MovieRequest): MediaRequest =
    requestApi.requestPost(
        RequestPostRequest(
            is4k = request.is4k,
            mediaId = requireNotNull(request.movieId),
            mediaType = RequestPostRequest.MediaType.MOVIE,
            serverId =
                when {
                    request.profileId == null && request.folder == null -> null
                    request.is4k -> request.data.server4kId
                    else -> request.data.serverId
                },
            profileId = request.profileId,
            rootFolder = request.folder,
            tags = emptyList(),
        ),
    )
