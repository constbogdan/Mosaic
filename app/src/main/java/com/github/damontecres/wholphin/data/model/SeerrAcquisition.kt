package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.model.DownloadStatus
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import java.util.UUID

internal const val TV_PROGRESS_GRACE_POLLS = 2

data class SeerrRequestAcquisition(
    val request: SeerrRequestState,
    val acquisition: SeerrAcquisitionState,
)

data class SeerrRequestState(
    val requestId: Int,
    val mediaId: Int?,
    val tmdbId: Int?,
    val discoverItem: DiscoverItem? = null,
    val requestedById: Int?,
    val requestedByName: String?,
    val status: RequestStatus,
    val mediaType: SeerrItemType,
    val is4k: Boolean,
    val availability: SeerrAvailability,
    val seasonAvailability: Map<Int, SeerrAvailability>,
    val seasonUpdatedAt: Map<Int, String>,
    val seasonEpisodeCounts: Map<Int, Int> = emptyMap(),
    val requestedSeasonNumbers: Set<Int>,
    val createdAt: String?,
    val updatedAt: String?,
    val availableSinceEpochMillis: Long? = null,
    val seasonAvailableSinceEpochMillis: Map<Int, Long> = emptyMap(),
    val jellyfinReadiness: JellyfinAcquisitionReadiness = JellyfinAcquisitionReadiness(),
    val jellyfinReadySinceEpochMillis: Long? = null,
    val jellyfinSeasonReadySinceEpochMillis: Map<Int, Long> = emptyMap(),
)

data class JellyfinAcquisitionReadiness(
    /** IDs below prove fulfillment of this request's selected quality, not generic playback. */
    val movieItemId: UUID? = null,
    val seriesItemId: UUID? = null,
    val seasonItemIds: Map<Int, UUID> = emptyMap(),
    val episodeItemIds: Map<Int, Map<Int, UUID>> = emptyMap(),
    /** Independently playable movie, even when it does not fulfill a 4K acquisition. */
    val playableMovieItemId: UUID? = null,
    /** Quality required when the fulfillment IDs were observed; standard retains legacy counting. */
    val fulfillmentIs4k: Boolean = false,
) {
    val movieReady: Boolean get() = movieItemId != null
    val playableMovieId: UUID? get() = playableMovieItemId ?: movieItemId

    fun seasonReady(
        seasonNumber: Int,
        expectedEpisodeCount: Int?,
    ): Boolean =
        expectedEpisodeCount != null &&
            expectedEpisodeCount > 0 &&
            if (fulfillmentIs4k) {
                (1..expectedEpisodeCount).all { it in episodeItemIds[seasonNumber].orEmpty() }
            } else {
                episodeItemIds[seasonNumber].orEmpty().size >= expectedEpisodeCount
            }
}

sealed interface SeerrAcquisitionState {
    /** Submitted successfully by Wholphin but not yet reconciled from a fresh tracker response. */
    data object Queueing : SeerrAcquisitionState

    data object None : SeerrAcquisitionState

    /** Seerr is processing the request, but ARR supplied no queue progress. */
    data object Processing : SeerrAcquisitionState

    data class Movie(
        val aggregate: AcquisitionAggregate,
    ) : SeerrAcquisitionState

    data class Tv(
        val seasons: List<SeasonAcquisition>,
        val unassignedEntries: List<AcquisitionEntry>,
    ) : SeerrAcquisitionState
}

data class SeasonAcquisition(
    val seasonNumber: Int,
    val aggregate: AcquisitionAggregate,
)

data class AcquisitionAggregate(
    val status: AcquisitionStatus,
    val progress: AcquisitionProgress?,
    val entries: List<AcquisitionEntry>,
)

data class AcquisitionProgress(
    val totalSize: Double,
    val sizeLeft: Double,
) {
    val completedSize: Double = totalSize - sizeLeft
    val fraction: Double = completedSize / totalSize
}

enum class AcquisitionStatus {
    UNKNOWN,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    PROBLEM,
}

/**
 * A queue snapshot entry. Its logical media/episode identity is kept separate from [downloadId]
 * so a future tracker can reconcile retries and retain completed entries after ARR removes them.
 */
data class AcquisitionEntry(
    val externalId: Int?,
    val downloadId: String?,
    val mediaType: String?,
    val title: String?,
    val status: AcquisitionStatus,
    val rawStatus: String?,
    val totalSize: Double?,
    val sizeLeft: Double?,
    val estimatedCompletionTime: String?,
    val timeLeft: String?,
    val episode: AcquisitionEpisode?,
    val presentInQueue: Boolean = true,
    val hasObservedProgress: Boolean = false,
    /** A current queue snapshot explicitly proved that this transfer completed successfully. */
    val observedSuccessfulTransferCompletion: Boolean = false,
    /** Number of missed tracker snapshots for which this entry's TV progress remains provisional. */
    val absentPollCount: Int = 0,
    val lifecycle: AcquisitionLifecycle? = null,
)

data class AcquisitionEpisode(
    val seriesId: Int?,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val title: String?,
    val hasFile: Boolean?,
)

fun MediaRequest.toSeerrRequestAcquisition(): SeerrRequestAcquisition {
    val mediaType = SeerrItemType.fromString(type)
    val is4k = is4k == true
    val requestedSeasons = seasons.orEmpty().mapNotNull { it.seasonNumber }.toSet()
    val availability = SeerrAvailability.from(if (is4k) media?.status4k else media?.status) ?: SeerrAvailability.UNKNOWN
    // Request seasons carry MediaRequestStatus, not MediaStatus. Only the media's
    // physical seasons may supply availability/timestamps; never fall back across qualities.
    val physicalSeasons = media?.seasons.orEmpty().associateBy { it.seasonNumber }
    val seasonAvailability =
        requestedSeasons.associateWith { seasonNumber ->
            val season = physicalSeasons[seasonNumber]
            val status = if (is4k) season?.status4k else season?.status
            SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN
        }
    val seasonUpdatedAt =
        requestedSeasons
            .mapNotNull { seasonNumber ->
                physicalSeasons[seasonNumber]?.updatedAt?.let { seasonNumber to it }
            }.toMap()
    val queue =
        if (is4k) {
            media?.downloadStatus4k.orEmpty()
        } else {
            media?.downloadStatus.orEmpty()
        }
    val entries = queue.mapNotNull { it.toAcquisitionEntry() }
    val acquisition =
        when {
            entries.isEmpty() && availability == SeerrAvailability.PROCESSING -> {
                SeerrAcquisitionState.Processing
            }

            entries.isEmpty() -> {
                SeerrAcquisitionState.None
            }

            mediaType == SeerrItemType.TV -> {
                entries.toTvAcquisition(requestedSeasons)
            }

            else -> {
                SeerrAcquisitionState.Movie(aggregateAcquisitionEntries(entries))
            }
        }

    return SeerrRequestAcquisition(
        request =
            SeerrRequestState(
                requestId = id,
                mediaId = media?.id,
                tmdbId = media?.tmdbId,
                requestedById = requestedBy?.id,
                requestedByName = requestedBy?.username ?: requestedBy?.plexUsername ?: requestedBy?.email,
                status = RequestStatus.from(status),
                mediaType = mediaType,
                is4k = is4k,
                availability = availability,
                seasonAvailability = seasonAvailability,
                seasonUpdatedAt = seasonUpdatedAt,
                requestedSeasonNumbers = requestedSeasons,
                createdAt = createdAt,
                updatedAt = updatedAt,
            ),
        acquisition = acquisition,
    )
}

private fun DownloadStatus.toAcquisitionEntry(): AcquisitionEntry? {
    val lifecycle = toAcquisitionLifecycle()
    val validSize = propertySize.validSize()
    val validSizeLeft = sizeLeft.validSizeLeft(validSize)
    val episode =
        episode?.seasonNumber?.let { seasonNumber ->
            AcquisitionEpisode(
                seriesId = episode.seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                title = episode.title,
                hasFile = episode.hasFile,
            )
        }
    return AcquisitionEntry(
        externalId = externalId,
        downloadId = downloadId,
        mediaType = mediaType,
        title = title,
        status = status.toAcquisitionStatus(),
        rawStatus = status,
        totalSize = validSize,
        sizeLeft = validSizeLeft,
        estimatedCompletionTime = estimatedCompletionTime,
        timeLeft = timeLeft,
        episode = episode,
        hasObservedProgress =
            validSize != null && validSizeLeft != null && validSizeLeft < validSize,
        observedSuccessfulTransferCompletion =
            status.toAcquisitionStatus() != AcquisitionStatus.PROBLEM &&
                (lifecycle == null || lifecycle.isPostTransfer) &&
                (
                    (validSize != null && validSize > 0.0 && validSizeLeft == 0.0) ||
                        status.toAcquisitionStatus() == AcquisitionStatus.COMPLETED
                ),
        lifecycle = lifecycle,
    ).takeIf { it.hasMeaningfulQueueData() }
}

private fun AcquisitionEntry.hasMeaningfulQueueData(): Boolean =
    lifecycle != null || externalId != null ||
        !downloadId.isNullOrBlank() ||
        !rawStatus.isNullOrBlank() ||
        totalSize != null ||
        sizeLeft != null ||
        episode != null ||
        !title.isNullOrBlank() ||
        !estimatedCompletionTime.isNullOrBlank() ||
        !timeLeft.isNullOrBlank()

private fun List<AcquisitionEntry>.toTvAcquisition(requestedSeasons: Set<Int>): SeerrAcquisitionState.Tv {
    val relevant =
        if (requestedSeasons.isEmpty()) {
            this
        } else {
            filter { entry ->
                entry.episode?.seasonNumber == null ||
                    entry.episode.seasonNumber in requestedSeasons
            }
        }
    val assigned =
        relevant.filter { entry ->
            val seasonNumber = entry.episode?.seasonNumber
            seasonNumber != null
        }
    val seasons =
        assigned
            .groupBy { it.episode!!.seasonNumber }
            .map { (seasonNumber, entries) ->
                SeasonAcquisition(seasonNumber, aggregateAcquisitionEntries(entries))
            }.sortedBy { it.seasonNumber }
    return SeerrAcquisitionState.Tv(
        seasons = seasons,
        unassignedEntries = relevant - assigned.toSet(),
    )
}

internal fun aggregateAcquisitionEntries(entries: List<AcquisitionEntry>): AcquisitionAggregate {
    val statuses = entries.map { it.status }
    val sizedEntries = entries.filter { it.totalSize != null && it.sizeLeft != null }
    val totalSize = sizedEntries.sumOf { it.totalSize!! }
    val sizeLeft = sizedEntries.sumOf { it.sizeLeft!! }
    return AcquisitionAggregate(
        status = statuses.aggregateStatus(),
        progress =
            if (sizedEntries.isNotEmpty() && totalSize > 0.0) {
                AcquisitionProgress(totalSize = totalSize, sizeLeft = sizeLeft)
            } else {
                null
            },
        entries = entries,
    )
}

private fun List<AcquisitionStatus>.aggregateStatus(): AcquisitionStatus =
    when {
        any { it == AcquisitionStatus.DOWNLOADING } -> AcquisitionStatus.DOWNLOADING
        any { it == AcquisitionStatus.PROBLEM } -> AcquisitionStatus.PROBLEM
        any { it == AcquisitionStatus.PAUSED } -> AcquisitionStatus.PAUSED
        any { it == AcquisitionStatus.QUEUED } -> AcquisitionStatus.QUEUED
        isNotEmpty() && all { it == AcquisitionStatus.COMPLETED } -> AcquisitionStatus.COMPLETED
        else -> AcquisitionStatus.UNKNOWN
    }

private fun String?.toAcquisitionStatus(): AcquisitionStatus =
    when (this?.lowercase()) {
        "downloading" -> AcquisitionStatus.DOWNLOADING
        "queued" -> AcquisitionStatus.QUEUED
        "paused" -> AcquisitionStatus.PAUSED
        "completed" -> AcquisitionStatus.COMPLETED
        "failed", "error", "warning" -> AcquisitionStatus.PROBLEM
        else -> AcquisitionStatus.UNKNOWN
    }

private fun Double?.validSize(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

private fun Double?.validSizeLeft(totalSize: Double?): Double? =
    takeIf { totalSize != null && it != null && it.isFinite() && it >= 0.0 && it <= totalSize }
