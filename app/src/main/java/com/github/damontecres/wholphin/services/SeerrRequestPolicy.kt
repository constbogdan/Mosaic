package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.ui.detail.discover.isRequestable
import com.github.damontecres.wholphin.ui.detail.discover.toRequestSeasons

internal fun CurrentSeerr?.permitsRequest(
    type: SeerrItemType,
    is4k: Boolean,
): Boolean =
    when (type) {
        SeerrItemType.MOVIE -> if (is4k) this?.request4kMovieEnabled == true else this?.requestMovieEnabled == true
        SeerrItemType.TV -> if (is4k) this?.request4kTvEnabled == true else this?.requestTvEnabled == true
        else -> false
    }

internal fun CurrentSeerr?.canRequestMovie(
    movie: MovieDetails?,
    is4k: Boolean,
): Boolean {
    if (!permitsRequest(SeerrItemType.MOVIE, is4k) || movie?.id?.let { it > 0 } != true) return false
    val media = movie.mediaInfo
    if (media?.status == SeerrAvailability.BLOCKLISTED.status || (media?.tmdbId != null && media.tmdbId != movie.id)) return false
    val status = if (is4k) media?.status4k else media?.status
    if (status != null && status !in setOf(SeerrAvailability.UNKNOWN.status, SeerrAvailability.DELETED.status)) return false
    return media?.requests.orEmpty().none {
        (it.is4k == true) == is4k && it.status != RequestStatus.DECLINED.status &&
            (it.status != RequestStatus.COMPLETED.status || status != SeerrAvailability.DELETED.status)
    }
}

internal fun CurrentSeerr?.canRequestTv(
    tv: TvDetails?,
    is4k: Boolean,
): Boolean =
    permitsRequest(SeerrItemType.TV, is4k) && tv?.id?.let { it > 0 } == true &&
        tv.mediaInfo?.status != SeerrAvailability.BLOCKLISTED.status &&
        (tv.mediaInfo?.tmdbId == null || tv.mediaInfo?.tmdbId == tv.id) &&
        tv.toRequestSeasons(this?.config?.id, is4k).any {
            it.isRequestable() || (it.editable && it.status == RequestStatus.PENDING)
        }

private fun MediaRequest.matchesMedia(
    media: MediaInfo,
    type: SeerrItemType,
    tmdbId: Int,
): Boolean =
    id > 0 && this.type == (if (type == SeerrItemType.TV) "tv" else "movie") &&
        (media.tmdbId == null || media.tmdbId == tmdbId) &&
        (this.media?.tmdbId == null || this.media?.tmdbId == tmdbId) &&
        (media.id == null || this.media?.id == null || media.id == this.media?.id)

/** The generic Cancel action has no quality/user selector: ambiguity must not choose a winner. */
internal fun MediaInfo?.singleCancellationTarget(
    user: SeerrUserConfig?,
    type: SeerrItemType,
    tmdbId: Int,
): MediaRequest? {
    if (this == null || user?.id == null || tmdbId <= 0) return null
    return requests
        .orEmpty()
        .filter { request ->
            request.matchesMedia(this, type, tmdbId) &&
                (
                    user.hasPermission(SeerrPermission.MANAGE_REQUESTS) ||
                        (request.requestedBy?.id == user.id && request.status == RequestStatus.PENDING.status)
                )
        }.singleOrNull()
}

internal fun TvDetails.pendingRequestFor(
    userId: Int?,
    is4k: Boolean,
    selectedSeasons: List<Int>,
): MediaRequest? {
    val media = mediaInfo ?: return null
    val tmdbId = id ?: return null
    val candidates =
        media.requests.orEmpty().filter { request ->
            userId != null && request.requestedBy?.id == userId && request.status == RequestStatus.PENDING.status &&
                (request.is4k == true) == is4k && request.matchesMedia(media, SeerrItemType.TV, tmdbId) &&
                request.seasons.orEmpty().any { it.seasonNumber in selectedSeasons }
        }
    check(candidates.size <= 1) { "Ambiguous pending TV requests" }
    return candidates.singleOrNull()
}
