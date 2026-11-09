package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.model.DownloadStatus
import com.github.damontecres.wholphin.api.seerr.model.DownloadStatusEpisode
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.Season
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SeerrAcquisitionTest {
    @Test
    fun calculatesMovieProgressFromAllValidQueueEntries() {
        val result =
            movieRequest(
                normal =
                    listOf(
                        download(size = 1_000.5, sizeLeft = 250.25, status = "downloading"),
                        download(size = 499.5, sizeLeft = 0.0, status = "queued"),
                    ),
            ).toSeerrRequestAcquisition()

        val movie = result.acquisition as SeerrAcquisitionState.Movie
        assertEquals(AcquisitionStatus.DOWNLOADING, movie.aggregate.status)
        assertEquals(1_500.0, movie.aggregate.progress!!.totalSize, 0.0)
        assertEquals(250.25, movie.aggregate.progress.sizeLeft, 0.0)
        assertEquals(1_249.75 / 1_500.0, movie.aggregate.progress.fraction, 0.000_001)
    }

    @Test
    fun processingWithoutQueueDataIsIndeterminate() {
        val result = movieRequest(mediaStatus = SeerrAvailability.PROCESSING.status).toSeerrRequestAcquisition()

        assertEquals(RequestStatus.APPROVED, result.request.status)
        assertEquals(SeerrAvailability.PROCESSING, result.request.availability)
        assertEquals(SeerrAcquisitionState.Processing, result.acquisition)
    }

    @Test
    fun emptyQueuePlaceholderIsIgnored() {
        val result =
            movieRequest(
                mediaStatus = SeerrAvailability.PROCESSING.status,
                normal = listOf(DownloadStatus()),
            ).toSeerrRequestAcquisition()

        assertEquals(SeerrAcquisitionState.Processing, result.acquisition)
    }

    @Test
    fun physicalTvSeasonSuppliesAvailabilityAndTimestamp() {
        val result =
            MediaRequest(
                id = 2,
                status = RequestStatus.COMPLETED.status,
                type = "tv",
                seasons =
                    listOf(
                        Season(
                            seasonNumber = 1,
                            status = RequestStatus.COMPLETED.status,
                            updatedAt = "2026-11-09T07:00:00Z",
                        ),
                    ),
                media =
                    MediaInfo(
                        id = 20,
                        status = SeerrAvailability.PARTIALLY_AVAILABLE.status,
                        seasons = listOf(Season(seasonNumber = 1, status = 5, updatedAt = "2026-09-01T07:38:08Z")),
                    ),
            ).toSeerrRequestAcquisition()

        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, result.request.availability)
        assertEquals(SeerrAvailability.AVAILABLE, result.request.seasonAvailability[1])
        assertEquals("2026-09-01T07:38:08Z", result.request.seasonUpdatedAt[1])
    }

    @Test
    fun requestLifecycleNeverSuppliesPhysicalSeasonAvailability() {
        for (status in 1..5) {
            val result = backendRequest(requestStatus = status).toSeerrRequestAcquisition()
            assertEquals(RequestStatus.from(status), result.request.status)
            assertEquals(SeerrAvailability.UNKNOWN, result.request.availability)
            assertEquals(SeerrAvailability.UNKNOWN, result.request.seasonAvailability[1])
            assertEquals(TvSeasonLifecycle.QUEUED, result.toTvSeasonTargets().single().lifecycle)
        }
    }

    @Test
    fun failedRequestFourIsNotPhysicalPartialAvailabilityFour() {
        val failed = backendRequest(requestStatus = 4).toSeerrRequestAcquisition()
        val partial = backendRequest(physical = """{"status":4,"seasons":[{"seasonNumber":1,"status":4}]}""").toSeerrRequestAcquisition()
        assertEquals(RequestStatus.FAILURE, failed.request.status)
        assertEquals(SeerrAvailability.UNKNOWN, failed.request.seasonAvailability[1])
        assertEquals(RequestStatus.APPROVED, partial.request.status)
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, partial.request.seasonAvailability[1])
    }

    @Test
    fun completedRequestWithPartialSeasonDoesNotCompleteSeason() {
        val result =
            backendRequest(requestStatus = 5, physical = """{"status":4,"seasons":[{"seasonNumber":1,"status":4}]}""")
                .toSeerrRequestAcquisition()
        val target = result.toTvSeasonTargets().single()
        assertEquals(RequestStatus.COMPLETED, result.request.status)
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, target.seerrAvailability)
        assertFalse(target.jellyfinReady)
        assertEquals(TvSeasonLifecycle.QUEUED, target.lifecycle)
        assertNull(target.completedAtEpochMillis)
    }

    @Test
    fun physicalAvailabilityDoesNotRequireCompletedRequestOrProveJellyfinReadiness() {
        val result =
            backendRequest(physical = """{"status":5,"seasons":[{"seasonNumber":1,"status":5}]}""")
                .toSeerrRequestAcquisition()
        val target = result.toTvSeasonTargets().single()
        assertEquals(RequestStatus.APPROVED, result.request.status)
        assertEquals(SeerrAvailability.AVAILABLE, target.seerrAvailability)
        assertFalse(target.jellyfinReady)
        assertEquals(TvSeasonLifecycle.FINISHING, target.lifecycle)
    }

    @Test
    fun absentPhysicalSeasonRemainsUnknownWithoutRequestTimestampFallback() {
        for (physical in listOf("{}", """{"status":5,"seasons":null}""", """{"status":5,"seasons":[]}""")) {
            val result = backendRequest(requestStatus = 5, physical = physical).toSeerrRequestAcquisition()
            assertEquals(setOf(1), result.request.requestedSeasonNumbers)
            assertEquals(SeerrAvailability.UNKNOWN, result.request.seasonAvailability[1])
            assertTrue(result.request.seasonUpdatedAt.isEmpty())
            assertEquals(TvSeasonLifecycle.QUEUED, result.toTvSeasonTargets().single().lifecycle)
        }
    }

    @Test
    fun unrelatedPhysicalSeasonsDoNotBecomeRequestedTargets() {
        val result = backendRequest(physical = """{"seasons":[{"seasonNumber":2,"status":5}]}""").toSeerrRequestAcquisition()
        assertEquals(setOf(1), result.request.requestedSeasonNumbers)
        assertEquals(mapOf(1 to SeerrAvailability.UNKNOWN), result.request.seasonAvailability)
        assertTrue(result.request.seasonUpdatedAt.isEmpty())
    }

    @Test
    fun futureRequestAndPhysicalStatusesRemainUnknown() {
        val result =
            backendRequest(requestStatus = 999, physical = """{"status":999,"seasons":[{"seasonNumber":1,"status":999}]}""")
                .toSeerrRequestAcquisition()
        assertEquals(RequestStatus.UNKNOWN, result.request.status)
        assertEquals(SeerrAvailability.UNKNOWN, result.request.availability)
        assertEquals(SeerrAvailability.UNKNOWN, result.request.seasonAvailability[1])
    }

    @Test
    fun standardAndFourKPhysicalAvailabilityAreIndependentForMoviesAndTv() {
        for (type in listOf("movie", "tv")) {
            for ((standard, fourK) in listOf(5 to 1, 1 to 5)) {
                val physical = """{"status":$standard,"status4k":$fourK,
                    "seasons":[{"seasonNumber":1,"status":$standard,"status4k":$fourK}]}"""
                for (is4k in listOf(false, true)) {
                    val result = backendRequest(is4k = is4k, type = type, physical = physical).toSeerrRequestAcquisition()
                    val expected = SeerrAvailability.from(if (is4k) fourK else standard)
                    assertEquals(expected, result.request.availability)
                    assertEquals(expected, result.request.seasonAvailability[1])
                }
            }
        }
    }

    @Test
    fun missingFourKFieldsNeverFallBackToAvailableStandardFields() {
        val result =
            backendRequest(is4k = true, physical = """{"status":5,"seasons":[{"seasonNumber":1,"status":5}]}""")
                .toSeerrRequestAcquisition()
        assertEquals(SeerrAvailability.UNKNOWN, result.request.availability)
        assertEquals(SeerrAvailability.UNKNOWN, result.request.seasonAvailability[1])
    }

    @Test
    fun separateQualityRequestsKeepIdentityLifecycleAndPhysicalState() {
        val physical = """{"status":5,"status4k":3,"seasons":[{"seasonNumber":1,"status":5,"status4k":3}]}"""
        val standard = backendRequest(requestStatus = 5, physical = physical).toSeerrRequestAcquisition()
        val fourK = backendRequest(is4k = true, physical = physical).copy(id = 92).toSeerrRequestAcquisition()
        assertEquals(91, standard.request.requestId)
        assertEquals(92, fourK.request.requestId)
        assertFalse(standard.request.is4k)
        assertTrue(fourK.request.is4k)
        assertEquals(RequestStatus.COMPLETED, standard.request.status)
        assertEquals(RequestStatus.APPROVED, fourK.request.status)
        assertEquals(SeerrAvailability.AVAILABLE, standard.request.seasonAvailability[1])
        assertEquals(SeerrAvailability.PROCESSING, fourK.request.seasonAvailability[1])
        assertEquals(SeerrAcquisitionState.Processing, fourK.acquisition)
    }

    @Test
    fun movieRequestLifecycleAloneCannotCreatePhysicalAvailabilityOrProgress() {
        for (status in listOf(2, 4, 5)) {
            val result = backendRequest(type = "movie", requestStatus = status).toSeerrRequestAcquisition()
            assertEquals(SeerrAvailability.UNKNOWN, result.request.availability)
            assertEquals(SeerrAcquisitionState.None, result.acquisition)
            assertFalse(result.request.jellyfinReadiness.movieReady)
        }
    }

    @Test
    fun mixedEpisodesRemainPartialDespiteCompletedRequest() {
        val request =
            backendRequest(requestStatus = 5, physical = """{"status":4,"seasons":[{"seasonNumber":1,"status":4}]}""")
                .copy(
                    media =
                        MediaInfo(
                            status = 4,
                            seasons = listOf(Season(seasonNumber = 1, status = 4)),
                            downloadStatus =
                                listOf(
                                    episodeDownload(
                                        1,
                                        1,
                                        100.0,
                                        0.0,
                                        "completed",
                                        "first",
                                    ).let { it.copy(episode = it.episode!!.copy(hasFile = true)) },
                                    episodeDownload(
                                        1,
                                        2,
                                        100.0,
                                        50.0,
                                        "downloading",
                                        "second",
                                    ).let { it.copy(episode = it.episode!!.copy(hasFile = false)) },
                                ),
                        ),
                ).toSeerrRequestAcquisition()
        val enriched =
            request.copy(
                request =
                    request.request.copy(
                        seasonEpisodeCounts = mapOf(1 to 2),
                        jellyfinReadiness = JellyfinAcquisitionReadiness(episodeItemIds = mapOf(1 to mapOf(1 to UUID.randomUUID()))),
                    ),
            )
        val target = enriched.toTvSeasonTargets().single()
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, target.seerrAvailability)
        assertFalse(target.jellyfinReady)
        assertEquals(TvSeasonLifecycle.IN_PROGRESS, target.lifecycle)
        assertEquals(0.75, target.acquisitionProjection.displayedFraction, 0.000_001)
        assertEquals(listOf(true, false), target.aggregate!!.entries.map { it.episode!!.hasFile })
    }

    private fun backendRequest(
        requestStatus: Int = 2,
        is4k: Boolean = false,
        type: String = "tv",
        physical: String = """{"id":84,"tmdbId":42,"status":1,"status4k":1,"seasons":[{"seasonNumber":1,"status":1,"status4k":1}]}""",
    ): MediaRequest =
        Serializer.kotlinxSerializationJson.decodeFromString(
            """{"id":91,"status":$requestStatus,"type":"$type","is4k":$is4k,"updatedAt":"2026-11-09T07:00:00Z",
            "seasons":[{"seasonNumber":1,"status":$requestStatus,"updatedAt":"2026-11-09T07:00:00Z"}],"media":$physical} """,
        )

    @Test
    fun emptyRequestActorsDeserializeWithoutLosingValidIdentity() {
        val empty =
            Serializer.kotlinxSerializationJson.decodeFromString<MediaRequest>(
                """{"id":1,"status":2,"requestedBy":{},"modifiedBy":{}}""",
            )
        val valid =
            Serializer.kotlinxSerializationJson.decodeFromString<MediaRequest>(
                """{"id":2,"status":2,"requestedBy":{"id":42,"username":"alex"}}""",
            )

        assertNull(empty.requestedBy?.id)
        assertNull(empty.modifiedBy?.id)
        assertEquals(42, valid.requestedBy?.id)
        assertEquals("alex", valid.requestedBy?.username)
    }

    @Test
    fun fourKRequestUsesOnlyFourKQueue() {
        val request =
            movieRequest(
                is4k = true,
                normal = listOf(download(size = 100.0, sizeLeft = 90.0, status = "downloading")),
                fourK = listOf(download(size = 200.0, sizeLeft = 50.0, status = "queued")),
            )

        val movie = request.toSeerrRequestAcquisition().acquisition as SeerrAcquisitionState.Movie
        assertEquals(AcquisitionStatus.QUEUED, movie.aggregate.status)
        assertEquals(0.75, movie.aggregate.progress!!.fraction, 0.0)
        assertEquals(200.0, movie.aggregate.progress.totalSize, 0.0)
    }

    @Test
    fun groupsTvProgressBySeasonAndRetainsEpisodeIdentity() {
        val request =
            tvRequest(
                requestedSeasons = setOf(1, 2),
                queue =
                    listOf(
                        episodeDownload(1, 1, 100.0, 25.0, "downloading", "s1e1"),
                        episodeDownload(1, 2, 300.0, 150.0, "queued", "s1e2"),
                        episodeDownload(2, 1, 500.0, 400.0, "paused", "s2e1"),
                    ),
            )

        val tv = request.toSeerrRequestAcquisition().acquisition as SeerrAcquisitionState.Tv
        val seasonOneProgress = tv.seasons[0].aggregate.progress!!
        assertEquals(listOf(1, 2), tv.seasons.map { it.seasonNumber })
        assertEquals(400.0, seasonOneProgress.totalSize, 0.0)
        assertEquals(175.0, seasonOneProgress.sizeLeft, 0.0)
        assertEquals(AcquisitionStatus.DOWNLOADING, tv.seasons[0].aggregate.status)
        assertEquals(
            2,
            tv.seasons[0]
                .aggregate.entries[1]
                .episode!!
                .episodeNumber,
        )
        assertEquals(
            "Episode 2",
            tv.seasons[0]
                .aggregate.entries[1]
                .episode!!
                .title,
        )
        assertEquals(
            "s1e2",
            tv.seasons[0]
                .aggregate.entries[1]
                .downloadId,
        )
        assertEquals(
            0.2,
            tv.seasons[1]
                .aggregate.progress!!
                .fraction,
            0.000_001,
        )
    }

    @Test
    fun invalidOrMissingSizesDoNotCreateOrCorruptProgress() {
        val result =
            movieRequest(
                normal =
                    listOf(
                        download(size = null, sizeLeft = null, status = "downloading"),
                        download(size = -1.0, sizeLeft = 0.0, status = "queued"),
                        download(size = 100.0, sizeLeft = 110.0, status = "queued"),
                    ),
            ).toSeerrRequestAcquisition()

        val movie = result.acquisition as SeerrAcquisitionState.Movie
        assertEquals(AcquisitionStatus.DOWNLOADING, movie.aggregate.status)
        assertNull(movie.aggregate.progress)
        assertEquals(3, movie.aggregate.entries.size)
    }

    @Test
    fun processingDoesNotMeanDownloadingWithoutDownloadingQueueStatus() {
        val result =
            movieRequest(
                mediaStatus = SeerrAvailability.PROCESSING.status,
                normal = listOf(download(size = 100.0, sizeLeft = 50.0, status = "queued")),
            ).toSeerrRequestAcquisition()

        val movie = result.acquisition as SeerrAcquisitionState.Movie
        assertEquals(AcquisitionStatus.QUEUED, movie.aggregate.status)
        assertTrue(movie.aggregate.entries.none { it.status == AcquisitionStatus.DOWNLOADING })
    }

    private fun movieRequest(
        is4k: Boolean = false,
        mediaStatus: Int = SeerrAvailability.PROCESSING.status,
        normal: List<DownloadStatus>? = null,
        fourK: List<DownloadStatus>? = null,
    ) = MediaRequest(
        id = 1,
        status = RequestStatus.APPROVED.status,
        type = "movie",
        is4k = is4k,
        media =
            MediaInfo(
                id = 10,
                status = mediaStatus,
                downloadStatus = normal,
                downloadStatus4k = fourK,
            ),
    )

    private fun tvRequest(
        requestedSeasons: Set<Int>,
        queue: List<DownloadStatus>,
    ) = MediaRequest(
        id = 2,
        status = RequestStatus.APPROVED.status,
        type = "tv",
        seasons = requestedSeasons.map { Season(seasonNumber = it) },
        media =
            MediaInfo(
                id = 20,
                status = SeerrAvailability.PROCESSING.status,
                downloadStatus = queue,
            ),
    )

    private fun download(
        size: Double?,
        sizeLeft: Double?,
        status: String,
    ) = DownloadStatus(
        externalId = 10,
        propertySize = size,
        sizeLeft = sizeLeft,
        status = status,
    )

    private fun episodeDownload(
        seasonNumber: Int,
        episodeNumber: Int,
        size: Double,
        sizeLeft: Double,
        status: String,
        downloadId: String,
    ) = DownloadStatus(
        externalId = 20,
        propertySize = size,
        sizeLeft = sizeLeft,
        status = status,
        downloadId = downloadId,
        episode =
            DownloadStatusEpisode(
                seriesId = 20,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = "Episode $episodeNumber",
                hasFile = false,
            ),
    )
}
