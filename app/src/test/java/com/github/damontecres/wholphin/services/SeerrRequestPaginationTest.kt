package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.model.toSeerrRequestAcquisition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrRequestPaginationTest {
    @Test
    fun acquisitionDiscoveryFetchesOnlyLatestTwentyFiveRequests() =
        runTest {
            val http = TvRequestHttpFixture(200, """{"pageInfo":{"page":1,"pages":20,"results":477},"results":[]}""")
            val wrapper = mockk<SeerrApi> { every { api } returns http.api }
            val service = SeerrService(wrapper, mockk(), mockk())

            assertTrue(service.getRequestAcquisitions().isEmpty())
            assertEquals(1, http.calls.size)
            val url = http.calls.single().url
            assertEquals("/api/v1/request", url.encodedPath)
            assertEquals("25", url.queryParameter("take"))
            assertEquals("0", url.queryParameter("skip"))
            assertEquals("added", url.queryParameter("sort"))
            assertEquals("desc", url.queryParameter("sortDirection"))
        }

    @Test
    fun targetedRequestTreatsOnlyGoneOrInaccessibleAsMissing() =
        runTest {
            val http = TvRequestHttpFixture(404, """{"message":"Not found"}""")
            val wrapper = mockk<SeerrApi> { every { api } returns http.api }
            val service = SeerrService(wrapper, mockk(), mockk())

            assertEquals(null, service.getTrackedRequestAcquisition(91))
            http.code = 403
            assertEquals(null, service.getTrackedRequestAcquisition(91))
            http.code = 500
            assertTrue(runCatching { service.getTrackedRequestAcquisition(91) }.isFailure)
            assertEquals(3, http.calls.size)
            assertTrue(http.calls.all { it.url.encodedPath == "/api/v1/request/91" })
        }

    @Test
    fun requestsEveryGlobalPageInOrder() =
        runTest {
            val calls = mutableListOf<Pair<Int, Int>>()
            val all = (1..5).map { request(it) }

            val result =
                paginateSeerrRequestAcquisitions(pageSize = 2) { take, skip ->
                    calls += take to skip
                    SeerrRequestAcquisitionPage(
                        requests = all.drop(skip).take(take),
                        page = skip / take + 1,
                        pages = 3,
                        totalResults = all.size,
                    )
                }

            assertEquals(listOf(1, 2, 3, 4, 5), result.map { it.request.requestId })
            assertEquals(
                listOf(2 to 0, 2 to 2, 2 to 4),
                calls,
            )
        }

    private fun request(id: Int) =
        MediaRequest(
            id = id,
            status = 5,
            type = "movie",
            media = MediaInfo(id = id, status = 5),
        ).toSeerrRequestAcquisition()
}
