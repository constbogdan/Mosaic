package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.services.SeerrAcquisitionLedger
import com.github.damontecres.wholphin.services.SeerrAcquisitionTrackerState
import com.github.damontecres.wholphin.services.toAcquisitionIndex
import com.github.damontecres.wholphin.ui.cards.movieCardPresentation
import com.github.damontecres.wholphin.ui.cards.tvSeasonCardPresentation
import com.github.damontecres.wholphin.ui.downloads.DownloadStatusLabel
import com.github.damontecres.wholphin.ui.downloads.toDownloadSections
import com.github.damontecres.wholphin.ui.main.toHomeAcquiringState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class AcquisitionLifecycleTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()

    @Test fun homeQualitySummaryRetainsWarningWithoutFailingHealthyQuality() {
        for (type in listOf("movie", "tv")) {
            val normal = listOf(entry("failed", "error"))
            val fourK = listOf(entry("downloading"))
            val standard = read(normal, type = type, fourK = fourK)
            val upgrade = read(normal, type = type, is4k = true, fourK = fourK).let { it.copy(request = it.request.copy(requestId = 92)) }
            val home = SeerrAcquisitionTrackerState(requests = listOf(standard, upgrade)).toAcquisitionIndex().toHomeAcquiringState()
            assertEquals(
                AcquisitionPhase.WARNING,
                home.items
                    .single()
                    .acquisitionPresentation!!
                    .lifecycle!!
                    .phase,
            )
            val rows = listOf(standard, upgrade).toDownloadSections(now).allRows()
            assertEquals(AcquisitionPhase.FAILED, rows.single { !it.request.request.is4k }.lifecycle!!.phase)
            assertEquals(AcquisitionPhase.DOWNLOADING, rows.single { it.request.request.is4k }.lifecycle!!.phase)
        }
    }

    @Test fun blockedOrWarningEvidenceDoesNotAdvertiseTransferEta() {
        for (phase in listOf("importBlocked", "downloading")) {
            val timed =
                JsonObject(
                    entry(phase, "warning") +
                        buildJsonObject {
                            put("timeLeft", "00:05:00")
                            put("estimatedCompletionTime", "2026-11-09T13:00:00Z")
                        },
                )
            for (type in listOf("movie", "tv")) {
                val row = listOf(read(listOf(timed), type = type)).toDownloadSections(now).allRows().single()
                assertNull(row.timing)
                assertFalse(row.completed)
            }
        }
    }

    @Test fun legacyEpisodeCannotBeHiddenByAnotherEpisodesExplicitFinalizing() {
        val result = read(listOf(entry("finalizing", left = 0.0), entry(null, null, episode = 2)), type = "tv")
        assertEquals(
            AcquisitionPhase.DOWNLOADING,
            result
                .toTvSeasonTargets()
                .single()
                .aggregate!!
                .lifecycle!!
                .phase,
        )
    }

    @Test fun allBackendPhasesSurviveRealDtoConversion() {
        for (phase in AcquisitionPhase.entries.filter { it != AcquisitionPhase.UNKNOWN }) {
            val result = read(listOf(entry(phase.wireValue)))
            assertEquals(phase, result.aggregate().lifecycle!!.phase)
            assertEquals(phase, result.movieCardPresentation()!!.lifecycle!!.phase)
            assertEquals(
                phase,
                listOf(result)
                    .toDownloadSections(now)
                    .allRows()
                    .single()
                    .lifecycle!!
                    .phase,
            )
        }
    }

    @Test fun healthIsIndependentAndErrorNeverMasqueradesAsFinishing() {
        for ((value, expected) in listOf(
            "ok" to AcquisitionHealth.OK,
            "warning" to AcquisitionHealth.WARNING,
            "error" to AcquisitionHealth.ERROR,
            "future" to AcquisitionHealth.UNKNOWN,
        )) {
            val result = read(listOf(entry("downloading", value)))
            assertEquals(expected, result.aggregate().lifecycle!!.health)
            assertEquals(
                if (expected == AcquisitionHealth.ERROR) {
                    AcquisitionPhase.FAILED
                } else {
                    if (expected == AcquisitionHealth.UNKNOWN) {
                        AcquisitionPhase.UNKNOWN
                    } else {
                        AcquisitionPhase.DOWNLOADING
                    }
                },
                result.aggregate().lifecycle!!.phase,
            )
        }
    }

    @Test fun missingOptionalSignalsKeepLegacyFallback() {
        val result = read(listOf(entry(null, null, left = 0.0)))
        assertNull(result.aggregate().lifecycle)
        assertTrue(result.aggregate().isFinishing)
    }

    @Test fun futureValuesAreConservativeAndNeverShownAsRawText() {
        val result = read(listOf(entry("futurePhase", "futureHealth", 0.0, reason = "provider-secret")))
        assertEquals(AcquisitionPhase.UNKNOWN, result.aggregate().lifecycle!!.phase)
        assertEquals(AcquisitionHealth.UNKNOWN, result.aggregate().lifecycle!!.health)
        assertNull(result.aggregate().lifecycle!!.reason)
        assertFalse(result.aggregate().isFinishing)
        assertFalse(result.movieCardPresentation().toString().contains("provider-secret"))
    }

    @Test fun trackedBlockedAndErrorOverrideInconsistentNormalPhase() {
        for ((state, status, expected) in listOf(
            Triple("importBlocked", "ok", AcquisitionPhase.IMPORT_BLOCKED),
            Triple("failedPending", "ok", AcquisitionPhase.FAILED),
            Triple("downloading", "error", AcquisitionPhase.FAILED),
        )) {
            val result = read(listOf(entry("finalizing", trackedState = state, trackedStatus = status)))
            assertEquals(expected, result.aggregate().lifecycle!!.phase)
            assertFalse(result.aggregate().isFinishing)
        }
    }

    @Test fun trackedFieldsAndCompatibilityAliasWorkWithoutPhase() {
        for ((state, expected) in listOf(
            "importing" to AcquisitionPhase.IMPORTING,
            "importPending" to AcquisitionPhase.IMPORT_PENDING,
            "imported" to AcquisitionPhase.FINALIZING,
        )) {
            assertEquals(expected, read(listOf(entry(null, trackedState = state))).aggregate().lifecycle!!.phase)
        }
        val alias =
            buildJsonObject {
                put("trackedStatus", "error")
                put("status", "downloading")
            }
        assertEquals(AcquisitionPhase.FAILED, read(listOf(alias)).aggregate().lifecycle!!.phase)
    }

    @Test fun importingWithoutTransferFieldsRemainsRepresented() {
        val phaseOnly =
            buildJsonObject {
                put("acquisitionPhase", "importing")
                put("health", "ok")
            }
        val result = read(listOf(phaseOnly))
        assertEquals(AcquisitionPhase.IMPORTING, result.movieCardPresentation()!!.lifecycle!!.phase)
        assertNull(result.movieCardPresentation()!!.acquisitionProgress)
    }

    @Test fun blockedAtOneHundredKeepsProgressSeparateFromCompletion() {
        val result = read(listOf(entry("importBlocked", "error", 0.0, reason = "insufficient_space")))
        val row = listOf(result).toDownloadSections(now).allRows().single()
        assertEquals(DownloadStatusLabel.PROBLEM, row.status)
        assertEquals(1f, row.progress)
        assertFalse(row.completed)
        assertEquals(AcquisitionSafeReason.INSUFFICIENT_SPACE, row.lifecycle!!.reason)
    }

    @Test fun warningDuringDownloadRetainsProgressWithoutBecomingFailure() {
        val result = read(listOf(entry("downloading", "warning", 50.0)))
        val row = listOf(result).toDownloadSections(now).allRows().single()
        assertEquals(DownloadStatusLabel.IN_PROGRESS, row.status)
        assertEquals(.5f, row.progress)
        assertEquals(AcquisitionHealth.WARNING, row.lifecycle!!.health)
        assertEquals(AcquisitionPhase.DOWNLOADING, row.lifecycle!!.phase)
    }

    @Test fun sanitizedReasonsAreAllowlistedAndRawProviderErrorsAreIgnored() {
        for (reason in AcquisitionSafeReason.entries) {
            assertEquals(reason, read(listOf(entry("failed", "error", reason = reason.wireValue))).aggregate().lifecycle!!.reason)
        }
        val malicious =
            buildJsonObject {
                put("acquisitionPhase", "failed")
                put("health", "error")
                put("safeReason", "C:\\private\\file?token=secret")
                put("errorMessage", "Authorization: secret")
                put("statusMessages", JsonArray(listOf(buildJsonObject { put("title", "secret-path") })))
            }
        val converted = read(listOf(malicious))
        assertNull(converted.aggregate().lifecycle!!.reason)
        assertFalse(converted.toString().contains("secret"))
        assertFalse(converted.toString().contains("private"))
    }

    @Test fun standardAndFourKQueueEvidenceNeverMixes() {
        val normal = listOf(entry("importBlocked", "error", 0.0))
        val fourK = listOf(entry("downloading", left = 75.0))
        val standard = read(normal, fourK = fourK)
        val upgrade = read(normal, is4k = true, fourK = fourK).let { it.copy(request = it.request.copy(requestId = 92)) }
        assertEquals(AcquisitionPhase.IMPORT_BLOCKED, standard.aggregate().lifecycle!!.phase)
        assertEquals(AcquisitionPhase.DOWNLOADING, upgrade.aggregate().lifecycle!!.phase)
        assertEquals(.25f, upgrade.movieCardPresentation()!!.acquisitionProgress)
    }

    @Test fun mixedEpisodeWarningAndFailureDoNotFailEntireSeason() {
        for (phase in listOf("warning", "failed")) {
            val result = read(listOf(entry(phase, episode = 1), entry("downloading", episode = 2)), type = "tv")
            val target = result.toTvSeasonTargets().single()
            assertEquals(AcquisitionPhase.WARNING, target.aggregate!!.lifecycle!!.phase)
            assertEquals(AcquisitionHealth.WARNING, target.aggregate.lifecycle!!.health)
            assertFalse(target.jellyfinReady)
            assertEquals(AcquisitionPhase.WARNING, target.tvSeasonCardPresentation()!!.lifecycle!!.phase)
        }
        val failed = read(listOf(entry("failed", episode = 1), entry("failed", episode = 2)), type = "tv")
        assertEquals(
            AcquisitionPhase.FAILED,
            failed
                .toTvSeasonTargets()
                .single()
                .aggregate!!
                .lifecycle!!
                .phase,
        )
    }

    @Test fun mixedImportAndDownloadingEpisodesKeepLeastAdvancedActivePhase() {
        val result = read(listOf(entry("importing", left = 0.0, episode = 1), entry("downloading", episode = 2)), type = "tv")
        assertEquals(
            AcquisitionPhase.DOWNLOADING,
            result
                .toTvSeasonTargets()
                .single()
                .aggregate!!
                .lifecycle!!
                .phase,
        )
        assertFalse(result.toTvSeasonTargets().single().jellyfinReady)
    }

    @Test fun homeAndDownloadsShareTheSameLifecycleProjection() {
        for (type in listOf("movie", "tv")) {
            val request = read(listOf(entry("importBlocked", "error", 0.0)), type = type)
            val home = SeerrAcquisitionTrackerState(requests = listOf(request)).toAcquisitionIndex().toHomeAcquiringState()
            val row = listOf(request).toDownloadSections(now).allRows().single()
            assertEquals(
                row.lifecycle,
                home.items
                    .single()
                    .acquisitionPresentation!!
                    .lifecycle,
            )
            assertEquals(DownloadStatusLabel.PROBLEM, row.status)
        }
    }

    @Test fun disappearedImportingUsesBoundedGraceWithoutInventingSuccess() {
        for (type in listOf("movie", "tv")) {
            val ledger = SeerrAcquisitionLedger()
            val active = read(listOf(entry("importing", left = 0.0)), type = type)
            ledger.reconcile(listOf(active))
            val missing = read(emptyList(), type = type)
            val retained = ledger.reconcile(listOf(missing)).single()
            val aggregate = if (type == "movie") retained.aggregate() else retained.toTvSeasonTargets().single().aggregate!!
            assertEquals(AcquisitionPhase.IMPORTING, aggregate.lifecycle!!.phase)
            assertTrue(listOf(retained).toDownloadSections(now).completed.isEmpty())
            repeat(TV_PROGRESS_GRACE_POLLS + 1) { ledger.reconcile(listOf(missing)) }
            val expired = ledger.reconcile(listOf(missing)).single()
            assertTrue(
                SeerrAcquisitionTrackerState(requests = listOf(expired))
                    .toAcquisitionIndex()
                    .toHomeAcquiringState()
                    .items
                    .isEmpty(),
            )
            assertTrue(listOf(expired).toDownloadSections(now).completed.isEmpty())
        }
    }

    @Test fun newTransferDoesNotInheritOldPhaseOrOneHundredPercent() {
        val ledger = SeerrAcquisitionLedger()
        ledger.reconcile(listOf(read(listOf(entry("finalizing", left = 0.0, download = "old")))))
        val fresh = ledger.reconcile(listOf(read(listOf(entry("downloading", left = 80.0, download = "new"))))).single()
        assertEquals(.2, fresh.aggregate().progress!!.fraction, .000_001)
        assertEquals(AcquisitionPhase.DOWNLOADING, fresh.aggregate().lifecycle!!.phase)
        assertFalse(
            fresh
                .aggregate()
                .entries
                .single()
                .observedSuccessfulTransferCompletion,
        )
    }

    @Test fun finalizingOnlyBecomesCompletedAfterJellyfinReadiness() {
        val request = read(listOf(entry("finalizing", left = 0.0)))
        assertTrue(listOf(request).toDownloadSections(now).completed.isEmpty())
        val ready =
            read(emptyList()).let {
                it.copy(
                    request =
                        it.request.copy(
                            jellyfinReadiness = JellyfinAcquisitionReadiness(movieItemId = UUID.randomUUID()),
                            jellyfinReadySinceEpochMillis = now,
                        ),
                )
            }
        assertEquals(
            DownloadStatusLabel.AVAILABLE,
            listOf(ready)
                .toDownloadSections(now)
                .completed
                .single()
                .status,
        )
    }

    private fun SeerrRequestAcquisition.aggregate() = (acquisition as SeerrAcquisitionState.Movie).aggregate

    private fun com.github.damontecres.wholphin.ui.downloads.DownloadSections.allRows() = active + processing + completed

    private fun entry(
        phase: String?,
        health: String? = "ok",
        left: Double = 50.0,
        reason: String? = null,
        trackedState: String? = null,
        trackedStatus: String? = null,
        episode: Int = 1,
        download: String = "fixture",
    ) = buildJsonObject {
        put("status", "downloading")
        put("size", 100.0)
        put("sizeLeft", left)
        put("downloadId", download)
        put("externalId", 84)
        put("title", "Fixture S01E0$episode")
        put(
            "episode",
            buildJsonObject {
                put("seasonNumber", 1)
                put("episodeNumber", episode)
            },
        )
        phase?.let { put("acquisitionPhase", it) }
        health?.let { put("health", it) }
        reason?.let { put("safeReason", it) }
        trackedState?.let { put("trackedDownloadState", it) }
        trackedStatus?.let { put("trackedDownloadStatus", it) }
    }

    private fun read(
        normal: List<JsonObject>,
        type: String = "movie",
        is4k: Boolean = false,
        fourK: List<JsonObject> = emptyList(),
    ): SeerrRequestAcquisition {
        val json =
            buildJsonObject {
                put("id", 91)
                put("status", 2)
                put("type", type)
                put("is4k", is4k)
                put("updatedAt", Instant.ofEpochMilli(now).toString())
                put(
                    "seasons",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("seasonNumber", 1)
                                put("status", 2)
                            },
                        ),
                    ),
                )
                put(
                    "media",
                    buildJsonObject {
                        put("id", 84)
                        put("tmdbId", 42)
                        put("status", 3)
                        put("status4k", 3)
                        put("downloadStatus", JsonArray(normal))
                        put("downloadStatus4k", JsonArray(fourK))
                    },
                )
            }
        return Serializer.kotlinxSerializationJson.decodeFromString<MediaRequest>(json.toString()).toSeerrRequestAcquisition().let {
            it.copy(
                request =
                    it.request.copy(
                        discoverItem =
                            DiscoverItem(
                                id = 42,
                                type = SeerrItemType.fromString(type),
                                title = "Fixture",
                                subtitle = null,
                                overview = null,
                                availability = SeerrAvailability.PROCESSING,
                                releaseDate = null,
                                posterUrl = null,
                                backDropUrl = null,
                                logoUrl = null,
                                jellyfinItemId = null,
                            ),
                        seasonEpisodeCounts = mapOf(1 to 2),
                    ),
            )
        }
    }
}
