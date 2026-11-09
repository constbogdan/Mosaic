package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.model.DownloadStatus
import java.time.Instant

enum class AcquisitionPhase(
    val wireValue: String,
) {
    UNKNOWN(""),
    WAITING("waiting"),
    DOWNLOADING("downloading"),
    PROCESSING("processing"),
    IMPORT_PENDING("importPending"),
    IMPORTING("importing"),
    IMPORT_BLOCKED("importBlocked"),
    FINALIZING("finalizing"),
    PAUSED("paused"),
    DELAYED("delayed"),
    WARNING("warning"),
    FAILED("failed"),
}

enum class AcquisitionHealth { OK, WARNING, ERROR, UNKNOWN }

/** Backend allowlisted codes only. Raw provider errors/messages must never enter presentation. */
enum class AcquisitionSafeReason(
    val wireValue: String,
) {
    INSUFFICIENT_SPACE("insufficient_space"),
    DOWNLOAD_CLIENT_UNAVAILABLE("download_client_unavailable"),
    IMPORT_BLOCKED("import_blocked"),
    IMPORT_FAILED("import_failed"),
    DELAYED("delayed"),
    PAUSED("paused"),
    UNKNOWN_ERROR("unknown_error"),
}

data class AcquisitionLifecycle(
    val phase: AcquisitionPhase,
    val health: AcquisitionHealth,
    val reason: AcquisitionSafeReason? = null,
    val startedAt: String? = null,
) {
    val isPostTransfer: Boolean
        get() =
            health == AcquisitionHealth.OK && phase in
                setOf(
                    AcquisitionPhase.PROCESSING,
                    AcquisitionPhase.IMPORT_PENDING,
                    AcquisitionPhase.IMPORTING,
                    AcquisitionPhase.FINALIZING,
                )
}

internal fun DownloadStatus.toAcquisitionLifecycle(): AcquisitionLifecycle? {
    val trackedStatus = trackedDownloadStatus ?: this.trackedStatus
    if (acquisitionPhase == null && health == null && trackedStatus == null && trackedDownloadState == null) return null
    val suppliedPhase =
        acquisitionPhase?.let { value ->
            AcquisitionPhase.entries.firstOrNull { it.wireValue == value }
                ?: AcquisitionPhase.UNKNOWN
        }
    val suppliedHealth =
        health?.let { value ->
            AcquisitionHealth.entries.firstOrNull { it.name.lowercase() == value }
                ?: AcquisitionHealth.UNKNOWN
        }
    val phase =
        when {
            suppliedPhase == AcquisitionPhase.IMPORT_BLOCKED || trackedDownloadState == "importBlocked" -> {
                AcquisitionPhase.IMPORT_BLOCKED
            }

            suppliedPhase == AcquisitionPhase.FAILED || suppliedHealth == AcquisitionHealth.ERROR ||
                trackedDownloadState in setOf("failed", "failedPending") || trackedStatus == "error" ||
                status in setOf("failed", "error", "downloadClientUnavailable") -> {
                AcquisitionPhase.FAILED
            }

            suppliedHealth == AcquisitionHealth.UNKNOWN -> {
                AcquisitionPhase.UNKNOWN
            }

            suppliedPhase != null -> {
                suppliedPhase
            }

            trackedDownloadState == "importing" -> {
                AcquisitionPhase.IMPORTING
            }

            trackedDownloadState == "importPending" -> {
                AcquisitionPhase.IMPORT_PENDING
            }

            trackedDownloadState == "imported" -> {
                AcquisitionPhase.FINALIZING
            }

            trackedDownloadState != null && trackedDownloadState !in setOf("downloading", "ignored") -> {
                AcquisitionPhase.UNKNOWN
            }

            trackedStatus != null && trackedStatus !in setOf("ok", "warning") -> {
                AcquisitionPhase.UNKNOWN
            }

            status == "paused" -> {
                AcquisitionPhase.PAUSED
            }

            status == "delay" -> {
                AcquisitionPhase.DELAYED
            }

            status == "warning" || trackedStatus == "warning" -> {
                AcquisitionPhase.WARNING
            }

            status in setOf("downloading", "completed") || trackedDownloadState == "downloading" -> {
                if (sizeLeft?.let { it > 0 } == true) AcquisitionPhase.DOWNLOADING else AcquisitionPhase.PROCESSING
            }

            else -> {
                AcquisitionPhase.WAITING
            }
        }
    val effectiveHealth =
        when {
            phase in setOf(AcquisitionPhase.IMPORT_BLOCKED, AcquisitionPhase.FAILED) -> AcquisitionHealth.ERROR

            suppliedHealth == AcquisitionHealth.UNKNOWN -> AcquisitionHealth.UNKNOWN

            phase in setOf(AcquisitionPhase.PAUSED, AcquisitionPhase.DELAYED, AcquisitionPhase.WARNING) ||
                suppliedHealth == AcquisitionHealth.WARNING || trackedStatus == "warning" -> AcquisitionHealth.WARNING

            else -> suppliedHealth ?: AcquisitionHealth.OK
        }
    return AcquisitionLifecycle(
        phase,
        effectiveHealth,
        reason =
            AcquisitionSafeReason.entries
                .firstOrNull {
                    it.wireValue == safeReason
                }?.takeIf { effectiveHealth != AcquisitionHealth.OK },
        startedAt = acquisitionPhaseStartedAt?.let { runCatching { Instant.parse(it).toString() }.getOrNull() },
    )
}

/** Explicit observations use the existing bounded reconciliation grace, not indefinite phase retention. */
internal val AcquisitionEntry.hasFreshLifecycleEvidence: Boolean
    get() = lifecycle != null && (presentInQueue || absentPollCount <= TV_PROGRESS_GRACE_POLLS)

internal val AcquisitionAggregate.lifecycle: AcquisitionLifecycle?
    get() {
        val current = entries.filter { it.presentInQueue || it.absentPollCount <= TV_PROGRESS_GRACE_POLLS }
        if (current.none { it.lifecycle != null }) return null
        val signals =
            current.map { entry ->
                entry.lifecycle ?: AcquisitionLifecycle(
                    when (entry.status) {
                        AcquisitionStatus.DOWNLOADING -> AcquisitionPhase.DOWNLOADING
                        AcquisitionStatus.QUEUED -> AcquisitionPhase.WAITING
                        AcquisitionStatus.PAUSED -> AcquisitionPhase.PAUSED
                        AcquisitionStatus.PROBLEM -> AcquisitionPhase.FAILED
                        AcquisitionStatus.COMPLETED -> AcquisitionPhase.PROCESSING
                        AcquisitionStatus.UNKNOWN -> AcquisitionPhase.UNKNOWN
                    },
                    if (entry.status == AcquisitionStatus.PROBLEM) AcquisitionHealth.ERROR else AcquisitionHealth.OK,
                )
            }
        return summarizeAcquisitionLifecycle(signals, current.size)
    }

internal fun summarizeAcquisitionLifecycle(
    signals: List<AcquisitionLifecycle>,
    scopeSize: Int,
): AcquisitionLifecycle? {
    if (signals.isEmpty()) return null
    val phase =
        when {
            signals.size == scopeSize && signals.all { it.phase == AcquisitionPhase.FAILED } -> {
                AcquisitionPhase.FAILED
            }

            signals.any { it.phase == AcquisitionPhase.IMPORT_BLOCKED } -> {
                AcquisitionPhase.IMPORT_BLOCKED
            }

            signals.any { it.phase == AcquisitionPhase.FAILED || it.phase == AcquisitionPhase.WARNING } -> {
                AcquisitionPhase.WARNING
            }

            else -> {
                listOf(
                    AcquisitionPhase.UNKNOWN,
                    AcquisitionPhase.DOWNLOADING,
                    AcquisitionPhase.PAUSED,
                    AcquisitionPhase.DELAYED,
                    AcquisitionPhase.WAITING,
                    AcquisitionPhase.PROCESSING,
                    AcquisitionPhase.IMPORT_PENDING,
                    AcquisitionPhase.IMPORTING,
                    AcquisitionPhase.FINALIZING,
                ).first { candidate -> signals.any { it.phase == candidate } }
            }
        }
    val health =
        when {
            phase in setOf(AcquisitionPhase.FAILED, AcquisitionPhase.IMPORT_BLOCKED) -> AcquisitionHealth.ERROR
            signals.any { it.health in setOf(AcquisitionHealth.WARNING, AcquisitionHealth.ERROR) } -> AcquisitionHealth.WARNING
            signals.any { it.health == AcquisitionHealth.UNKNOWN } -> AcquisitionHealth.UNKNOWN
            else -> AcquisitionHealth.OK
        }
    val reason = signals.sortedByDescending { it.health == AcquisitionHealth.ERROR }.firstNotNullOfOrNull { it.reason }
    return AcquisitionLifecycle(phase, health, reason)
}
