package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SearchApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.FreshResult
import com.github.damontecres.wholphin.api.seerr.model.FreshStatus
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

internal const val FRESH_PAGE_SIZE = 20

internal enum class FreshCategory(
    val mediaType: SearchApi.MediaTypeFreshGet,
    val classification: SearchApi.CategoryFreshGet? = null,
) {
    ALL(SearchApi.MediaTypeFreshGet.ALL),
    MOVIES(SearchApi.MediaTypeFreshGet.MOVIE),
    SHOWS(SearchApi.MediaTypeFreshGet.TV),
    ANIMATION(SearchApi.MediaTypeFreshGet.ALL, SearchApi.CategoryFreshGet.ANIMATION),
    DOCUMENTARY(SearchApi.MediaTypeFreshGet.ALL, SearchApi.CategoryFreshGet.DOCUMENTARY),
}

internal enum class FreshSort(
    val query: SearchApi.SortFreshGet,
) {
    FRESHEST(SearchApi.SortFreshGet.FRESHEST),
    OLDEST(SearchApi.SortFreshGet.OLDEST),
    RATING(SearchApi.SortFreshGet.VOTE_AVERAGE),
}

internal data class FreshEntry(
    val item: DiscoverItem,
) : CardGridItem {
    val key: String = "${item.type}:${item.id}"
    override val gridId: String get() = key
    override val playable: Boolean get() = false
    override val sortName: String get() = item.sortName
}

internal data class FreshPageData(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val status: FreshStatus.Status,
    val entries: List<FreshEntry>,
)

internal interface FreshBackend {
    suspend fun page(
        category: FreshCategory,
        sort: FreshSort,
        page: Int,
    ): FreshPageData
}

internal data class FreshSession(
    val owner: String,
    val backend: FreshBackend,
    val isCurrent: () -> Boolean,
    val epoch: SeerrApiClient,
)

/** Fresh is a shared discovery feed, not the personal Watchlist. Still bind every response to both active sessions. */
@Singleton
internal class FreshSessionSource
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
                    FreshSession(
                        owner = owner,
                        backend = SeerrFreshBackend(client, current),
                        epoch = client,
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

internal class SeerrFreshBackend(
    private val client: SeerrApiClient,
    private val current: CurrentSeerr,
) : FreshBackend {
    override suspend fun page(
        category: FreshCategory,
        sort: FreshSort,
        page: Int,
    ): FreshPageData {
        val result =
            client.searchApi.freshGet(
                page = page,
                mediaType = category.mediaType,
                category = category.classification,
                sort = sort.query,
            )
        return FreshPageData(
            page = result.page,
            totalPages = result.totalPages,
            totalResults = result.totalResults,
            status = result.status.status,
            entries = result.results.map { FreshEntry(it.toDiscoverItem(current)) },
        )
    }
}

internal fun FreshResult.toDiscoverItem(current: CurrentSeerr): DiscoverItem {
    val type = if (mediaType == FreshResult.MediaType.MOVIE) SeerrItemType.MOVIE else SeerrItemType.TV
    return DiscoverItem(
        id = id,
        type = type,
        title = if (type == SeerrItemType.MOVIE) title else name,
        subtitle = null,
        overview = overview,
        availability = SeerrAvailability.from(mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        releaseDate = toLocalDate(if (type == SeerrItemType.MOVIE) releaseDate else firstAirDate),
        posterUrl = posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) },
        backDropUrl = backdropPath?.let { current.imageUrlBuilder(ImageType.BACKDROP, it) },
        logoUrl = null,
        jellyfinItemId = mediaInfo?.jellyfinId,
    )
}
