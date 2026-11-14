package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.LibraryApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionItem
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.toLocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject
import javax.inject.Singleton

internal const val COLLECTION_PAGE_SIZE = 20

internal enum class CollectionsFilter(
    val query: LibraryApi.CompletenessLibraryCollectionsGet,
) {
    ALL(LibraryApi.CompletenessLibraryCollectionsGet.ALL),
    INCOMPLETE(LibraryApi.CompletenessLibraryCollectionsGet.INCOMPLETE),
    COMPLETE(LibraryApi.CompletenessLibraryCollectionsGet.COMPLETE),
}

/** Only server-supported global ordering is offered; client-side page sorting would be incorrect. */
internal enum class CollectionsSort(
    val query: LibraryApi.SortLibraryCollectionsGet,
    val direction: LibraryApi.SortDirectionLibraryCollectionsGet,
) {
    NAME_ASC(LibraryApi.SortLibraryCollectionsGet.NAME, LibraryApi.SortDirectionLibraryCollectionsGet.ASC),
    NAME_DESC(LibraryApi.SortLibraryCollectionsGet.NAME, LibraryApi.SortDirectionLibraryCollectionsGet.DESC),
    COMPLETION_ASC(LibraryApi.SortLibraryCollectionsGet.COMPLETENESS, LibraryApi.SortDirectionLibraryCollectionsGet.ASC),
    COMPLETION_DESC(LibraryApi.SortLibraryCollectionsGet.COMPLETENESS, LibraryApi.SortDirectionLibraryCollectionsGet.DESC),
    AVAILABLE_ASC(LibraryApi.SortLibraryCollectionsGet.AVAILABLE, LibraryApi.SortDirectionLibraryCollectionsGet.ASC),
    AVAILABLE_DESC(LibraryApi.SortLibraryCollectionsGet.AVAILABLE, LibraryApi.SortDirectionLibraryCollectionsGet.DESC),
}

internal data class CollectionsEntry(
    val summary: LibraryCollectionItem,
    val posterUrl: String?,
    val backdropUrl: String?,
) : CardGridItem {
    override val gridId: String get() = "tmdb:collection:${summary.id}"
    override val playable: Boolean get() = false
    override val sortName: String get() = summary.name
}

internal data class CollectionsPageData(
    val skip: Int,
    val pageSize: Int,
    val total: Int,
    val status: LibraryCollectionsResponse.Status,
    val stale: Boolean,
    val failedCollections: Int,
    val entries: List<CollectionsEntry>,
)

internal data class CollectionDetailsData(
    val id: Int,
    val name: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val movies: List<DiscoverItem>,
)

internal data class CollectionMovieLink(
    val id: Int,
    val name: String,
    val posterUrl: String?,
)

/** A Jellyfin movie's TMDB movie ID is required to verify a claimed collection membership. */
internal fun collectionProviderIds(providerIds: Map<String, String?>?): Pair<Int, Int?>? {
    val ids = providerIds ?: return null
    val movieId = ids["Tmdb"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val collectionId = ids["TmdbCollection"]?.toIntOrNull()?.takeIf { it > 0 }
    return movieId to collectionId
}

internal interface CollectionsBackend {
    suspend fun page(
        skip: Int,
        filter: CollectionsFilter,
        sort: CollectionsSort,
    ): CollectionsPageData

    suspend fun detail(id: Int): CollectionDetailsData

    fun linkFromMovie(movie: MovieDetails): CollectionMovieLink? = null

    suspend fun linkFromJellyfinMovie(
        movieId: Int,
        claimedCollectionId: Int?,
    ): CollectionMovieLink? = null
}

internal data class CollectionsSession(
    val owner: String,
    val epoch: SeerrApiClient,
    val backend: CollectionsBackend,
    val isCurrent: () -> Boolean,
)

@Singleton
internal class CollectionsSessionSource
    @Inject
    constructor(
        private val seerr: SeerrServerRepository,
        private val jellyfin: ServerRepository,
        private val api: SeerrApi,
    ) {
        val sessions =
            combine(seerr.connection, jellyfin.current) { connection, local ->
                val current = (connection.connection as? SeerrConnectionStatus.Success)?.current
                if (current == null || local == null || !api.active) {
                    null
                } else {
                    val client = api.api
                    val owner = "${current.server.url}:${current.config.id}:${local.server.id}:${local.user.id}"
                    CollectionsSession(
                        owner = owner,
                        epoch = client,
                        backend = SeerrCollectionsBackend(client, current),
                        isCurrent = {
                            val live = (seerr.connection.value.connection as? SeerrConnectionStatus.Success)?.current
                            val localNow = jellyfin.current.value
                            api.active && api.api === client && live != null && localNow != null &&
                                live.server.url == current.server.url && live.config.id == current.config.id &&
                                localNow.server.id == local.server.id && localNow.user.id == local.user.id
                        },
                    )
                }
            }.distinctUntilChanged { old, new -> old?.owner == new?.owner && old?.epoch === new?.epoch }
    }

internal class SeerrCollectionsBackend(
    private val client: SeerrApiClient,
    private val current: CurrentSeerr,
) : CollectionsBackend {
    override fun linkFromMovie(movie: MovieDetails): CollectionMovieLink? {
        val collection = movie.collection ?: return null
        val id = collection.id?.takeIf { it > 0 } ?: return null
        val name = collection.name?.takeIf { it.isNotBlank() } ?: return null
        return CollectionMovieLink(
            id,
            name,
            collection.posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
        )
    }

    override suspend fun linkFromJellyfinMovie(
        movieId: Int,
        claimedCollectionId: Int?,
    ): CollectionMovieLink? {
        if (movieId <= 0) return null
        if (claimedCollectionId != null && claimedCollectionId > 0) {
            val collection = detail(claimedCollectionId)
            if (collection.movies.none { it.id == movieId }) return null
            return CollectionMovieLink(collection.id, collection.name, collection.posterUrl)
        }
        val movie = client.moviesApi.movieMovieIdGet(movieId)
        if (movie.id != movieId) return null
        return linkFromMovie(movie)
    }

    override suspend fun page(
        skip: Int,
        filter: CollectionsFilter,
        sort: CollectionsSort,
    ): CollectionsPageData {
        val response =
            client.libraryApi.libraryCollectionsGet(
                take = COLLECTION_PAGE_SIZE,
                skip = skip,
                completeness = filter.query,
                sort = sort.query,
                sortDirection = sort.direction,
            )
        return CollectionsPageData(
            skip = skip,
            pageSize = response.pageInfo.pageSize,
            total = response.pageInfo.results,
            status = response.status,
            stale = response.stale,
            failedCollections = response.failedCollections,
            entries =
                response.results.map { row ->
                    CollectionsEntry(
                        summary = row,
                        posterUrl = row.posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
                        backdropUrl = row.backdropPath?.let { current.imageUrlBuilder(ImageType.BACKDROP, it) },
                    )
                },
        )
    }

    override suspend fun detail(id: Int): CollectionDetailsData {
        val response = client.collectionApi.collectionCollectionIdGet(id)
        require(response.id == id) { "Collection identity changed" }
        val movies =
            response.parts
                .orEmpty()
                .filter { it.id > 0 && it.mediaType == "movie" }
                .distinctBy { it.id }
                .sortedWith(
                    compareBy<MovieResult> { toLocalDate(it.releaseDate) == null }.thenBy { toLocalDate(it.releaseDate) }.thenBy { it.id },
                ).map(::movie)
        return CollectionDetailsData(
            id = id,
            name = response.name?.takeIf { it.isNotBlank() } ?: "Collection $id",
            overview = response.overview?.takeIf { it.isNotBlank() },
            posterUrl = response.posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
            backdropUrl = response.backdropPath?.let { current.imageUrlBuilder(ImageType.BACKDROP, it) },
            movies = movies,
        )
    }

    fun movie(part: MovieResult): DiscoverItem =
        DiscoverItem(
            id = part.id,
            type = SeerrItemType.MOVIE,
            title = part.title?.takeIf { it.isNotBlank() } ?: "Movie ${part.id}",
            subtitle = null,
            overview = part.overview,
            availability = SeerrAvailability.from(part.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
            releaseDate = toLocalDate(part.releaseDate),
            posterUrl = part.posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
            backDropUrl = part.backdropPath?.let { current.imageUrlBuilder(ImageType.BACKDROP, it) },
            logoUrl = null,
            // Seerr availability is not proof of a playable current-user Jellyfin destination.
            jellyfinItemId = null,
        )
}
