package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.leavingsoon.formatLeavingSoonDate
import com.github.damontecres.wholphin.ui.leavingsoon.validateLeavingSoonPage
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.util.Locale

class LeavingSoonContractTest {
    private val current =
        CurrentSeerr(
            SeerrServer(1, "http://seerr.invalid"),
            SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "viewer", null, null),
            User(id = 7),
            PublicSettings(),
        )

    private fun backend(
        body: String,
        requests: MutableList<Request>,
    ): LeavingSoonBackend {
        val http =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requests += chain.request()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("fixture")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return SeerrLeavingSoonBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
    }

    @Test fun categoryRequestsUseOnlyTheAuthenticatedReadEndpoint() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed = backend("""{"pageInfo":{"pages":0,"page":1,"results":0,"pageSize":20},"results":[]}""", requests)
            LeavingSoonCategory.entries.forEach { category ->
                val result = feed.page(category, 1)
                validateLeavingSoonPage(result, 1)
                assertEquals(0, result.totalResults)
            }
            assertEquals(listOf("all", "movie", "series"), requests.map { it.url.queryParameter("mediaType") })
            assertTrue(requests.all { it.method == "GET" && it.url.encodedPath == "/api/v1/leaving-soon" })
            assertTrue(requests.all { it.url.queryParameter("page") == "1" })
        }

    @Test fun preservesServerOrderAndMapsEarliestReturnedSchedule() =
        runTest {
            val requests = mutableListOf<Request>()
            val schedule =
                """
                {"id":1,"canonicalMediaId":9,"targetType":"movie","seasonNumber":null,
                "origin":"manual","status":"deferred","scheduledAt":"2026-11-01T00:00:00Z",
                "removeAt":"2026-12-12T00:00:00Z","revision":1}
                """.trimIndent()
            val later = schedule.replace("\"id\":1", "\"id\":2").replace("2026-12-12", "2027-01-01")
            val body =
                """
                {"pageInfo":{"pages":1,"page":1,"results":2,"pageSize":20},"results":[
                {"canonicalMediaId":9,"tmdbId":42,"mediaType":"movie","title":"First",
                "year":2020,"posterPath":"/first.jpg","schedules":[$later,$schedule]},
                {"canonicalMediaId":10,"tmdbId":null,"mediaType":"series","title":"Second",
                "year":null,"posterPath":null,"schedules":[]}]}
                """.trimIndent()
            val result = backend(body, requests).page(LeavingSoonCategory.ALL, 1)
            validateLeavingSoonPage(result, 1)
            assertEquals(listOf(9, 10), result.entries.map { it.canonicalMediaId })
            assertEquals(SeerrItemType.MOVIE, result.entries[0].item.type)
            assertEquals(SeerrItemType.TV, result.entries[1].item.type)
            assertTrue(result.entries[0].hasTmdbIdentity)
            assertFalse(result.entries[1].hasTmdbIdentity)
            assertEquals("12 Dec 2026", formatLeavingSoonDate(result.entries[0].removeAt!!, Locale.ENGLISH))
            assertEquals(null, result.entries[1].removeAt)
        }

    @Test fun rejectsTruncatedOrDuplicatePagesWithoutLocalFiltering() {
        val entry =
            LeavingSoonEntry(
                1,
                com.github.damontecres.wholphin.data.model.DiscoverItem(
                    1,
                    SeerrItemType.MOVIE,
                    "One",
                    null,
                    null,
                    com.github.damontecres.wholphin.data.model.SeerrAvailability.UNKNOWN,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
                true,
                "One",
                null,
                null,
                null,
            )
        assertThrows(IllegalArgumentException::class.java) {
            validateLeavingSoonPage(LeavingSoonPageData(1, 1, 2, 20, listOf(entry)), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateLeavingSoonPage(LeavingSoonPageData(1, 1, 2, 20, listOf(entry, entry)), 1)
        }
    }

    @Test fun badgeUsesDateAsReturnedAndDoesNotInventMissingDate() {
        assertEquals("12 Dec 2026", formatLeavingSoonDate(OffsetDateTime.parse("2026-12-12T00:00:00Z"), Locale.ENGLISH))
    }
}
