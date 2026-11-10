package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.WatchlistApi
import com.github.damontecres.wholphin.api.seerr.model.PersonalWatchlistCreate
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistPage
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistSessionSource
    @Inject
    constructor(
        private val seerr: SeerrServerRepository,
        private val jellyfin: ServerRepository,
        private val api: SeerrApi,
    ) {
        internal val sessions =
            combine(seerr.connection, jellyfin.current) { connection, local ->
                val current = (connection.connection as? SeerrConnectionStatus.Success)?.current
                val owner = personalWatchlistIdentity(current, local)
                if (owner == null || current == null || local == null || !api.active) {
                    null
                } else {
                    val captured = api.api
                    WatchlistSession(
                        owner = owner,
                        backend = PersonalWatchlistBackend(captured, current),
                        isCurrent = {
                            val live = (seerr.connection.value.connection as? SeerrConnectionStatus.Success)?.current
                            val localNow = jellyfin.current.value
                            api.api === captured && api.active && personalWatchlistIdentity(live, localNow) == owner
                        },
                        epoch = captured,
                    )
                }
            }.distinctUntilChanged { previous, next -> previous?.owner == next?.owner && previous?.epoch === next?.epoch }
    }

internal fun personalWatchlistIdentity(
    current: CurrentSeerr?,
    local: CurrentUser?,
): String? {
    if (current == null || local == null || current.user.authMethod == SeerrAuthMethod.API_KEY ||
        current.config.id <= 0 || current.user.jellyfinUserRowId != local.user.rowId ||
        current.config.jellyfinUserId?.toUUIDOrNull() != local.user.id
    ) {
        return null
    }
    return "${current.server.url}:${current.config.id}:${local.server.id}:${local.server.url}:${local.user.rowId}"
}

internal fun MediaKey.Catalog.watchlistType(): String = if (mediaType == CatalogMediaType.MOVIE) "movie" else "tv"

internal fun watchlistKey(
    type: String,
    id: Int,
): MediaKey.Catalog =
    MediaKey.Catalog(
        when (type) {
            "movie" -> CatalogMediaType.MOVIE
            "tv" -> CatalogMediaType.SERIES
            else -> throw WatchlistUnavailable("Unsupported Watchlist identity")
        },
        id,
    )

internal class PersonalWatchlistBackend(
    private val client: SeerrApiClient,
    private val current: CurrentSeerr,
) : WatchlistBackend {
    override suspend fun page(
        category: WatchlistCategory,
        page: Int,
    ): WatchlistPage {
        val result =
            client.watchlistApi.watchlistPage(
                owner = "me",
                category = category.query,
                sort = "added_desc",
                watched = "all",
                libraryStatus = "any",
                includeArtwork = true,
                page = page,
            )
        if (result.source != "local") throw WatchlistUnavailable("This server does not provide the personal local Watchlist API.")
        return WatchlistPage(
            result.page,
            result.totalPages,
            result.totalResults,
            result.results.map { row ->
                val key = watchlistKey(row.mediaType, row.tmdbId)
                WatchlistEntry(
                    key,
                    row.title,
                    Instant.parse(row.createdAt),
                    row.artwork?.posterPath,
                    row.artwork?.backdropPath,
                    row.genreIds,
                    DiscoverItem(
                        id = key.tmdbId,
                        type = if (key.mediaType == CatalogMediaType.MOVIE) SeerrItemType.MOVIE else SeerrItemType.TV,
                        title = row.title,
                        subtitle = null,
                        overview = null,
                        availability = SeerrAvailability.UNKNOWN,
                        releaseDate = null,
                        posterUrl = row.artwork?.posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
                        backDropUrl = row.artwork?.backdropPath?.let { current.imageUrlBuilder(ImageType.BACKDROP, it) },
                        logoUrl = null,
                        jellyfinItemId = null,
                    ),
                )
            },
        )
    }

    override suspend fun membership(keys: Set<MediaKey.Catalog>): Map<MediaKey.Catalog, Boolean> {
        require(keys.size in 1..100)
        val rows =
            try {
                client.watchlistApi.watchlistMembership(keys.joinToString(",") { "${it.watchlistType()}:${it.tmdbId}" }).results
            } catch (error: com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException) {
                if (error.statusCode == 404 ||
                    error.statusCode == 405
                ) {
                    throw WatchlistUnavailable(
                        "Update Seerr-custom: the personal Watchlist membership API is unavailable.",
                    )
                }
                throw error
            }
        val values = rows.associate { watchlistKey(it.mediaType, it.tmdbId) to it.onWatchlist }
        require(values.size == rows.size && values.keys == keys) { "Incomplete Watchlist membership response" }
        return values
    }

    override suspend fun add(
        key: MediaKey.Catalog,
        title: String,
    ) {
        client.watchlistApi.watchlistAdd(PersonalWatchlistCreate(key.tmdbId, key.watchlistType(), title))
    }

    override suspend fun remove(key: MediaKey.Catalog) {
        client.watchlistApi.watchlistRemove(
            key.tmdbId.toString(),
            if (key.mediaType ==
                CatalogMediaType.MOVIE
            ) {
                WatchlistApi.MediaTypeWatchlistRemove.MOVIE
            } else {
                WatchlistApi.MediaTypeWatchlistRemove.TV
            },
        )
    }
}
