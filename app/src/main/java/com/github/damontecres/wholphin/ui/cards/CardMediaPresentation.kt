package com.github.damontecres.wholphin.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.AcquisitionHealth
import com.github.damontecres.wholphin.data.model.AcquisitionLifecycle
import com.github.damontecres.wholphin.data.model.AcquisitionPhase
import com.github.damontecres.wholphin.data.model.AcquisitionSafeReason
import com.github.damontecres.wholphin.data.model.AcquisitionStatus
import com.github.damontecres.wholphin.data.model.SeerrAcquisitionState
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrRequestAcquisition
import com.github.damontecres.wholphin.data.model.TvSeasonLifecycle
import com.github.damontecres.wholphin.data.model.TvSeasonTarget
import com.github.damontecres.wholphin.data.model.aggregateAcquisitionEntries
import com.github.damontecres.wholphin.data.model.analyzeTvSeasonAcquisition
import com.github.damontecres.wholphin.data.model.hasActiveProgress
import com.github.damontecres.wholphin.data.model.hasCurrentMovieAcquisitionWork
import com.github.damontecres.wholphin.data.model.isFinishing
import com.github.damontecres.wholphin.data.model.lifecycle
import com.github.damontecres.wholphin.data.model.summarizeAcquisitionLifecycle
import com.github.damontecres.wholphin.services.IndexedAcquisition
import com.github.damontecres.wholphin.services.MediaProductState

data class CardMediaPresentation(
    val acquisitionProgress: Float? = null,
    val acquisitionState: CardAcquisitionState? = null,
    val lifecycle: AcquisitionLifecycle? = null,
)

internal fun List<CardMediaPresentation>.combinedCardPresentation(): CardMediaPresentation? =
    when (size) {
        0 -> {
            null
        }

        1 -> {
            single()
        }

        else -> {
            CardMediaPresentation(
                acquisitionState = CardAcquisitionState.ACQUIRING,
                lifecycle = summarizeAcquisitionLifecycle(mapNotNull { it.lifecycle }, size),
            )
        }
    }

internal val AcquisitionPhase.stringResource: Int get() =
    when (this) {
        AcquisitionPhase.UNKNOWN -> R.string.acquiring
        AcquisitionPhase.WAITING -> R.string.download_status_queued
        AcquisitionPhase.DOWNLOADING -> R.string.acquisition_downloading
        AcquisitionPhase.PROCESSING -> R.string.acquisition_processing
        AcquisitionPhase.IMPORT_PENDING -> R.string.acquisition_import_pending
        AcquisitionPhase.IMPORTING -> R.string.acquisition_importing
        AcquisitionPhase.IMPORT_BLOCKED -> R.string.acquisition_import_blocked
        AcquisitionPhase.FINALIZING -> R.string.acquisition_finalizing
        AcquisitionPhase.PAUSED -> R.string.acquisition_paused
        AcquisitionPhase.DELAYED -> R.string.acquisition_delayed
        AcquisitionPhase.WARNING -> R.string.acquisition_warning
        AcquisitionPhase.FAILED -> R.string.acquisition_failed
    }

internal val AcquisitionSafeReason.stringResource: Int get() =
    when (this) {
        AcquisitionSafeReason.INSUFFICIENT_SPACE -> R.string.acquisition_reason_space
        AcquisitionSafeReason.DOWNLOAD_CLIENT_UNAVAILABLE -> R.string.acquisition_reason_client
        AcquisitionSafeReason.IMPORT_BLOCKED -> R.string.acquisition_import_blocked
        AcquisitionSafeReason.IMPORT_FAILED -> R.string.acquisition_reason_import
        AcquisitionSafeReason.DELAYED -> R.string.acquisition_delayed
        AcquisitionSafeReason.PAUSED -> R.string.acquisition_paused
        AcquisitionSafeReason.UNKNOWN_ERROR -> R.string.download_status_problem
    }

@Composable
internal fun AcquisitionLifecycle.displayText(): String {
    val phaseText = stringResource(phase.stringResource)
    return if (health == AcquisitionHealth.WARNING && phase !in
        setOf(
            AcquisitionPhase.WARNING,
            AcquisitionPhase.PAUSED,
            AcquisitionPhase.DELAYED,
        )
    ) {
        stringResource(R.string.acquisition_phase_warning, phaseText)
    } else {
        phaseText
    }
}

enum class CardAcquisitionState {
    ACQUIRING,
    QUEUEING,
    QUEUED,
    FINISHING,
}

internal enum class ArtworkProgressSource {
    PLAYBACK,
    ACQUISITION,
}

internal data class ArtworkProgress(
    val fraction: Float,
    val source: ArtworkProgressSource,
)

internal fun resolveArtworkProgress(
    showPlaybackOverlay: Boolean,
    watchedPercent: Double?,
    acquisitionProgress: Float?,
): ArtworkProgress? {
    val playback =
        watchedPercent
            ?.takeIf { showPlaybackOverlay && it.isFinite() && it > 0.0 && it < 100.0 }
            ?.let { ArtworkProgress((it / 100.0).toFloat(), ArtworkProgressSource.PLAYBACK) }
    return playback
        ?: acquisitionProgress
            ?.takeIf { it.isFinite() && it > 0f && it < 1f }
            ?.let { ArtworkProgress(it, ArtworkProgressSource.ACQUISITION) }
}

internal fun MediaProductState.movieRequestCardPresentation(
    requestId: Int,
    is4k: Boolean,
): CardMediaPresentation? =
    acquisitions
        .filter { indexed ->
            indexed.request.request.requestId == requestId &&
                indexed.request.request.is4k == is4k &&
                indexed.request.request.mediaType == SeerrItemType.MOVIE
        }.movieCatalogCardPresentation()

internal fun List<IndexedAcquisition>.movieCatalogCardPresentation(): CardMediaPresentation? {
    val presentations = mapNotNull(IndexedAcquisition::movieCardPresentation).distinct()
    return presentations.combinedCardPresentation()
}

internal fun IndexedAcquisition.movieCardPresentation(): CardMediaPresentation? = request.movieCardPresentation()

internal fun SeerrRequestAcquisition.movieCardPresentation(): CardMediaPresentation? {
    if (!hasCurrentMovieAcquisitionWork) return null
    return when (val acquisition = acquisition) {
        SeerrAcquisitionState.Queueing -> {
            CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUEING)
        }

        is SeerrAcquisitionState.Movie -> {
            val aggregate = acquisition.aggregate
            aggregate.lifecycle?.let { return aggregate.explicitPresentation(it) }
            when {
                aggregate.isFinishing -> {
                    CardMediaPresentation(acquisitionState = CardAcquisitionState.FINISHING)
                }

                aggregate.status == AcquisitionStatus.DOWNLOADING &&
                    aggregate.hasActiveProgress -> {
                    val progress =
                        aggregate.progress
                            ?.fraction
                            ?.takeIf { it.isFinite() && it > 0.0 && it < 1.0 }
                            ?.toFloat()
                    progress?.let { CardMediaPresentation(acquisitionProgress = it) }
                        ?: CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUED)
                }

                else -> {
                    CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUED)
                }
            }
        }

        else -> {
            null
        }
    }
}

internal fun IndexedAcquisition.tvSeasonCardPresentation(): CardMediaPresentation? {
    if (request.acquisition == SeerrAcquisitionState.Queueing) {
        return CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUEING)
    }
    val target = tvSeasonTarget ?: return null
    return target.tvSeasonCardPresentation()
}

internal fun TvSeasonTarget.tvSeasonCardPresentation(): CardMediaPresentation? {
    if (!hasCurrentAcquisitionWork) return null
    val operationalAggregate =
        aggregate
            ?.let { canonical ->
                if (jellyfinReady) {
                    aggregateAcquisitionEntries(canonical.entries)
                        .analyzeTvSeasonAcquisition(seasonNumber, expectedEpisodeCount, emptySet())
                        .aggregate
                } else {
                    canonical
                }
            }
            ?: return CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUED)
    operationalAggregate.lifecycle?.let { return operationalAggregate.explicitPresentation(it) }
    when (lifecycle) {
        TvSeasonLifecycle.QUEUED -> {
            return CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUED)
        }

        TvSeasonLifecycle.FINISHING -> {
            return CardMediaPresentation(acquisitionState = CardAcquisitionState.FINISHING)
        }

        TvSeasonLifecycle.AVAILABLE -> {
            if (operationalAggregate.isFinishing) {
                return CardMediaPresentation(acquisitionState = CardAcquisitionState.FINISHING)
            }
        }

        TvSeasonLifecycle.IN_PROGRESS -> {
            Unit
        }
    }
    val progress =
        operationalAggregate.progress
            ?.fraction
            ?.takeIf { it.isFinite() && it > 0.0 && it < 1.0 }
            ?.toFloat()
    return if (
        operationalAggregate.status == AcquisitionStatus.DOWNLOADING &&
        operationalAggregate.hasActiveProgress &&
        progress != null
    ) {
        CardMediaPresentation(acquisitionProgress = progress)
    } else {
        CardMediaPresentation(acquisitionState = CardAcquisitionState.QUEUED)
    }
}

private fun com.github.damontecres.wholphin.data.model.AcquisitionAggregate.explicitPresentation(
    lifecycle: AcquisitionLifecycle,
): CardMediaPresentation =
    CardMediaPresentation(
        acquisitionProgress =
            progress
                ?.fraction
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.coerceAtMost(1.0)
                ?.toFloat(),
        acquisitionState = CardAcquisitionState.ACQUIRING,
        lifecycle = lifecycle,
    )
