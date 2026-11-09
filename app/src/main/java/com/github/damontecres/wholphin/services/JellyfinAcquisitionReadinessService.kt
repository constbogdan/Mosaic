package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.JellyfinAcquisitionReadiness
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrRequestAcquisition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinAcquisitionReadinessService
    @Inject
    constructor(
        private val api: ApiClient,
        private val seriesInventoryService: JellyfinSeriesInventoryService,
    ) {
        suspend fun enrich(requests: List<SeerrRequestAcquisition>): List<SeerrRequestAcquisition> =
            coroutineScope {
                val semaphore = Semaphore(3)
                requests
                    .map { acquisition ->
                        async {
                            if (!acquisition.needsJellyfinReadinessCheck()) return@async acquisition
                            semaphore.withPermit {
                                runCatching { acquisition.withJellyfinReadiness() }
                                    .onFailure {
                                        if (it is CancellationException) throw it
                                        Timber.w(it, "Unable to resolve Jellyfin readiness for request %s", acquisition.request.requestId)
                                    }.getOrDefault(acquisition)
                            }
                        }
                    }.awaitAll()
            }

        private suspend fun SeerrRequestAcquisition.withJellyfinReadiness(): SeerrRequestAcquisition {
            val kind =
                when (request.mediaType) {
                    SeerrItemType.MOVIE -> BaseItemKind.MOVIE
                    SeerrItemType.TV -> BaseItemKind.SERIES
                    else -> return this
                }
            val items = findJellyfinItems(kind)
            val item = items.firstOrNull() ?: return this

            val readiness =
                if (request.mediaType == SeerrItemType.MOVIE) {
                    val playable = items.firstOrNull { it.mediaSources.orEmpty().isNotEmpty() }
                    val fulfilled = if (request.is4k) items.firstOrNull { it.hasVerified4kSource() } else playable
                    JellyfinAcquisitionReadiness(
                        movieItemId = fulfilled?.id,
                        playableMovieItemId = playable?.id,
                        fulfillmentIs4k = request.is4k,
                    )
                } else {
                    val inventory = seriesInventoryService.get(item.id)
                    val previous = request.jellyfinReadiness.takeIf { request.is4k && it.seriesItemId == item.id }
                    JellyfinAcquisitionReadiness(
                        seriesItemId = item.id,
                        seasonItemIds = previous?.seasonItemIds.orEmpty() + inventory.seasonItemIds,
                        fulfillmentIs4k = request.is4k,
                        episodeItemIds =
                            if (request.is4k) {
                                val observed =
                                    inventory.verified4kEpisodeItemIds.mapValues { (season, episodes) ->
                                        val expected = request.seasonEpisodeCounts[season]
                                        episodes.filterKeys { expected == null || it in 1..expected }
                                    }
                                (previous?.episodeItemIds.orEmpty().keys + observed.keys).associateWith { season ->
                                    previous?.episodeItemIds?.get(season).orEmpty() + observed[season].orEmpty()
                                }
                            } else {
                                inventory.playableEpisodeItemIds
                            },
                    )
                }
            return copy(request = request.copy(jellyfinReadiness = readiness))
        }

        private suspend fun SeerrRequestAcquisition.findJellyfinItems(kind: BaseItemKind): List<BaseItemDto> {
            val explicit =
                request.discoverItem
                    ?.jellyfinItemId
                    ?.let { jellyfinItemId ->
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    ids = listOf(jellyfinItemId),
                                    includeItemTypes = listOf(kind),
                                    fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.MEDIA_SOURCES),
                                    limit = 1,
                                    enableTotalRecordCount = false,
                                ),
                            ).content.items
                            .firstOrNull {
                                it.id == jellyfinItemId && it.type == kind &&
                                    (it.providerIds?.get("Tmdb") == null || it.providerIds?.get("Tmdb") == request.tmdbId?.toString())
                            }
                    }
            // A generic explicit ID may point only to the standard version. Search the same
            // bounded, exact-provider result set for a separate 4K item as well.
            val needsAlternates =
                explicit == null ||
                    (
                        kind == BaseItemKind.MOVIE &&
                            (explicit.mediaSources.orEmpty().isEmpty() || (request.is4k && !explicit.hasVerified4kSource()))
                    )
            val alternates =
                if (needsAlternates) {
                    runCatching { findJellyfinItemsByTmdb(kind) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrElse {
                            if (explicit == null) throw it
                            Timber.w(it, "Unable to resolve alternate Jellyfin versions for request %s", request.requestId)
                            emptyList()
                        }
                } else {
                    emptyList()
                }
            return (listOfNotNull(explicit) + alternates).distinctBy { it.id }
        }

        private suspend fun SeerrRequestAcquisition.findJellyfinItemsByTmdb(kind: BaseItemKind): List<BaseItemDto> =
            request.tmdbId?.let { tmdbId ->
                val title = request.discoverItem?.title ?: return@let emptyList()
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            searchTerm = title,
                            recursive = true,
                            includeItemTypes = listOf(kind),
                            fields = listOf(ItemFields.PROVIDER_IDS, ItemFields.MEDIA_SOURCES),
                            limit = 20,
                            enableTotalRecordCount = false,
                        ),
                    ).content.items
                    .filter { it.type == kind && it.providerIds?.get("Tmdb") == tmdbId.toString() }
            } ?: emptyList()
    }

/** Source-level video evidence, not names, HDR flags, item-level streams or Seerr status.
 * UHD/DCI-width video includes cropped cinema sources; 2K is not 4K. Missing dimensions
 * remain unknown. This proves Jellyfin's current metadata, not live file accessibility.
 */
internal fun BaseItemDto.hasVerified4kSource(): Boolean =
    mediaSources.orEmpty().any { source ->
        val video = source.mediaStreams.orEmpty().filter { it.type == MediaStreamType.VIDEO }
        video.isNotEmpty() && video.all { (it.width ?: 0) >= 3840 && (it.height ?: 0) > 0 }
    }

internal fun SeerrRequestAcquisition.needsJellyfinReadinessCheck(): Boolean =
    when (request.mediaType) {
        SeerrItemType.MOVIE -> {
            !request.jellyfinReadiness.movieReady
        }

        SeerrItemType.TV -> {
            val previousSeries = request.jellyfinReadiness.seriesItemId
            val currentSeries = request.discoverItem?.jellyfinItemId
            (previousSeries != null && currentSeries != null && previousSeries != currentSeries) ||
                request.requestedSeasonNumbers.any { season ->
                    !request.jellyfinReadiness.seasonReady(season, request.seasonEpisodeCounts[season])
                }
        }

        else -> {
            false
        }
    }
