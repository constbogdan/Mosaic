package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.LibraryApi
import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LeavingSoonItem
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.ImageType
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

internal const val LEAVING_SOON_PAGE_SIZE = 20

internal enum class LeavingSoonCategory(
    val mediaType: LibraryApi.MediaTypeLeavingSoonGet,
) {
    ALL(LibraryApi.MediaTypeLeavingSoonGet.ALL),
    MOVIES(LibraryApi.MediaTypeLeavingSoonGet.MOVIE),
    SERIES(LibraryApi.MediaTypeLeavingSoonGet.SERIES),
}

internal data class LeavingSoonEntry(
    val canonicalMediaId: Int,
    val item: DiscoverItem,
    val hasTmdbIdentity: Boolean,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val removeAt: OffsetDateTime?,
) : CardGridItem {
    val key: String = "canonical:$canonicalMediaId"
    override val gridId: String get() = key
    override val playable: Boolean get() = false
    override val sortName: String get() = title
}

internal data class LeavingSoonPageData(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val pageSize: Int,
    val entries: List<LeavingSoonEntry>,
)

internal interface LeavingSoonBackend {
    suspend fun page(
        category: LeavingSoonCategory,
        page: Int,
    ): LeavingSoonPageData
}

internal data class LeavingSoonSession(
    val owner: String,
    val backend: LeavingSoonBackend,
    val isCurrent: () -> Boolean,
    val epoch: SeerrApiClient,
)

@Singleton
internal class LeavingSoonSessionSource
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
                    LeavingSoonSession(
                        owner = owner,
                        backend = SeerrLeavingSoonBackend(client, current),
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

internal class SeerrLeavingSoonBackend(
    private val client: SeerrApiClient,
    private val current: CurrentSeerr,
) : LeavingSoonBackend {
    override suspend fun page(
        category: LeavingSoonCategory,
        page: Int,
    ): LeavingSoonPageData {
        val response = client.libraryApi.leavingSoonGet(page = page, mediaType = category.mediaType)
        return LeavingSoonPageData(
            page = response.pageInfo.page,
            totalPages = response.pageInfo.pages,
            totalResults = response.pageInfo.results,
            pageSize = response.pageInfo.pageSize,
            entries = response.results.map { it.toLeavingSoonEntry(current) },
        )
    }
}

internal fun LeavingSoonItem.toLeavingSoonEntry(current: CurrentSeerr): LeavingSoonEntry {
    val type = if (mediaType == LeavingSoonItem.MediaType.MOVIE) SeerrItemType.MOVIE else SeerrItemType.TV
    val posterUrl = posterPath?.let { current.imageUrlBuilder(ImageType.PRIMARY, it) }
    val yearDate = year?.takeIf { it in 1..9999 }?.let { LocalDate.of(it, 1, 1) }
    val validTmdbId = tmdbId?.takeIf { it > 0 }
    val item =
        DiscoverItem(
            id = validTmdbId ?: canonicalMediaId,
            type = type,
            title = title,
            subtitle = null,
            overview = null,
            availability = SeerrAvailability.UNKNOWN,
            releaseDate = yearDate,
            posterUrl = posterUrl,
            backDropUrl = null,
            logoUrl = null,
            jellyfinItemId = null,
        )
    return LeavingSoonEntry(
        canonicalMediaId = canonicalMediaId,
        item = item,
        hasTmdbIdentity = validTmdbId != null,
        title = title,
        posterUrl = posterUrl,
        year = year,
        removeAt = schedules.minByOrNull { it.removeAt.toInstant() }?.removeAt,
    )
}
