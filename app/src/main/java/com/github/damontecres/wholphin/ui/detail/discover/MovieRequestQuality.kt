package com.github.damontecres.wholphin.ui.detail.discover

internal const val MOVIE_PROFILE_1080P_HDR = "1080p HDR"
internal const val MOVIE_PROFILE_2160P_HDR = "2160p HDR"

/** Resolve only a uniquely named profile on the configured standard Radarr server. */
internal fun movieRequestForQuality(
    data: SeerrRequestData,
    movieId: Int?,
    profileName: String,
): MovieRequest? {
    // Seerr server IDs are zero-based; -1 is our unknown-server sentinel.
    if (data.serverId < 0 || movieId == null || movieId <= 0) return null
    val profile = data.profiles.filter { it.name.trim().equals(profileName, ignoreCase = true) && it.id > 0 }.singleOrNull() ?: return null
    val folder = data.rootFolders.firstOrNull { it.default } ?: data.rootFolders.firstOrNull()
    return MovieRequest(data, movieId, false, profile.id, folder?.path)
}
