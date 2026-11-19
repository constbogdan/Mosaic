package com.github.damontecres.wholphin.ui.detail.discover

import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestUser
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestSeasonRulesTest {
    @Test
    fun upstreamDeclinedEditabilityUsesSharedProjectionWithoutReopeningProtectedSeasons() {
        for (status in listOf(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.DECLINED, RequestStatus.COMPLETED)) {
            val tv =
                TvDetails(
                    id = 42,
                    seasons = listOf(Season(seasonNumber = 2)),
                    mediaInfo =
                        MediaInfo(
                            id = 1,
                            requests =
                                listOf(
                                    MediaRequest(
                                        id = 5,
                                        status = status.status,
                                        is4k = false,
                                        requestedBy = RequestUser(id = 7),
                                        seasons = listOf(Season(seasonNumber = 2, status = status.status)),
                                    ),
                                ),
                        ),
                )
            val owned = tv.toRequestSeasons(7, false).single()
            assertEquals(status in setOf(RequestStatus.PENDING, RequestStatus.DECLINED), owned.editable)
            assertEquals(status == RequestStatus.DECLINED, owned.isRequestable())
            assertFalse(tv.toRequestSeasons(8, false).single().editable)
            assertEquals(RequestStatus.UNKNOWN, tv.toRequestSeasons(7, true).single().status)
        }
    }

    @Test
    fun onlyMissingEditableSeasonsAreNewRequests() {
        assertTrue(season(1).isRequestable())
        assertTrue(season(1, availability = SeerrAvailability.DELETED).isRequestable())
        assertFalse(season(1, editable = false).isRequestable())
        listOf(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.COMPLETED).forEach {
            assertFalse(season(1, status = it).isRequestable())
        }
        listOf(
            SeerrAvailability.PENDING,
            SeerrAvailability.PROCESSING,
            SeerrAvailability.PARTIALLY_AVAILABLE,
            SeerrAvailability.AVAILABLE,
            SeerrAvailability.BLOCKLISTED,
        ).forEach {
            assertFalse(season(1, availability = it).isRequestable())
        }
    }

    @Test
    fun ownedPendingRequestRemainsEditableButIsNotANewRequest() {
        val pending = season(2, RequestStatus.PENDING, SeerrAvailability.PENDING)
        assertTrue(pending.editable)
        assertFalse(pending.isRequestable())
        assertEquals(setOf(2), listOf(pending).initialRequestSeasonNumbers(null))
        assertTrue(listOf(pending).requestableSeasonNumbers().isEmpty())
    }

    @Test
    fun initialSelectionPreservesPendingAndOnlyAddsRequestableTarget() {
        val seasons = listOf(season(1, RequestStatus.PENDING), season(2), season(3, RequestStatus.APPROVED))
        assertEquals(setOf(1, 2), seasons.initialRequestSeasonNumbers(2))
        assertEquals(setOf(1), seasons.initialRequestSeasonNumbers(3))
        assertEquals(setOf(1), seasons.initialRequestSeasonNumbers(null))
    }

    @Test
    fun bulkSelectionNeverRemovesExistingPendingOrInitialSeason() {
        val seasons = listOf(season(1, RequestStatus.PENDING), season(2), season(3), season(4, RequestStatus.APPROVED))
        val selected = seasons.initialRequestSeasonNumbers(2).toMutableSet()
        val remaining = seasons.filter { it.season.seasonNumber != 2 }.requestableSeasonNumbers()
        assertEquals(setOf(3), remaining)
        selected.addAll(remaining)
        assertEquals(setOf(1, 2, 3), selected)
        selected.removeAll(remaining)
        assertEquals(setOf(1, 2), selected)
        assertTrue(seasons.first().editable)
    }

    @Test
    fun nonEditablePendingAndUnknownSeasonNumbersAreNeverBulkTargets() {
        val seasons = listOf(season(1, RequestStatus.PENDING, editable = false), season(null), season(2))
        assertEquals(setOf(2), seasons.requestableSeasonNumbers())
        assertEquals(emptySet<Int>(), seasons.initialRequestSeasonNumbers(null))
        assertEquals(setOf(2), seasons.initialRequestSeasonNumbers(2))
        assertEquals(emptySet<Int>(), seasons.initialRequestSeasonNumbers(1))
    }

    @Test
    fun ownedAndOtherwiseIneligibleSeasonsAreNeverSelectedByDefault() {
        val seasons =
            listOf(
                season(1, availability = SeerrAvailability.AVAILABLE, editable = false),
                season(2, availability = SeerrAvailability.PARTIALLY_AVAILABLE, editable = false),
                season(3, RequestStatus.APPROVED, SeerrAvailability.PROCESSING, editable = false),
                season(4, RequestStatus.COMPLETED, editable = false),
            )
        assertEquals(emptySet<Int>(), seasons.initialRequestSeasonNumbers(null))
        seasons.forEach { assertEquals(emptySet<Int>(), seasons.initialRequestSeasonNumbers(it.season.seasonNumber)) }
    }

    @Test
    fun foreignPendingAndOwnPendingKeepOnlyOwnEditableRequestSelected() {
        val seasons =
            listOf(
                season(1, RequestStatus.PENDING, SeerrAvailability.PENDING, editable = false),
                season(2, RequestStatus.PENDING, SeerrAvailability.PENDING),
                season(3),
            )
        assertEquals(setOf(2), seasons.initialRequestSeasonNumbers(null))
        assertEquals(setOf(2, 3), seasons.initialRequestSeasonNumbers(3))
        assertEquals(setOf(3), seasons.requestableSeasonNumbers())
    }

    @Test
    fun unownedAndFourKRequestableSeasonsRequireExplicitSelection() {
        val standard = listOf(season(1, availability = SeerrAvailability.AVAILABLE, editable = false))
        val fourK = listOf(season(1))
        val unowned = listOf(season(1), season(2))
        assertEquals(emptySet<Int>(), standard.initialRequestSeasonNumbers(null))
        assertEquals(emptySet<Int>(), fourK.initialRequestSeasonNumbers(null))
        assertEquals(setOf(1), fourK.initialRequestSeasonNumbers(1))
        assertEquals(emptySet<Int>(), unowned.initialRequestSeasonNumbers(null))
        assertEquals(setOf(2), unowned.initialRequestSeasonNumbers(2))
    }

    private fun season(
        number: Int?,
        status: RequestStatus = RequestStatus.UNKNOWN,
        availability: SeerrAvailability = SeerrAvailability.UNKNOWN,
        editable: Boolean = true,
    ) = RequestSeason(Season(seasonNumber = number), status, availability, editable)
}
