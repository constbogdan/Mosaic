package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.FreshStatus
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.fresh.validateFreshPage
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshContractTest {
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
    ): FreshBackend {
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
        return SeerrFreshBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
    }

    @Test fun queryUsesServerPaginationCategoryAndFullSetSort() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed =
                backend(
                    """{"page":2,"totalPages":4,"totalResults":62,"status":{"status":"ready","refreshing":false,"backfillComplete":true,"itemCount":62,"pendingWork":0},"results":[]}""",
                    requests,
                )
            FreshCategory.entries.forEach { category ->
                FreshSort.entries.forEach { sort ->
                    val page = feed.page(category, sort, 2)
                    assertEquals(62, page.totalResults)
                    assertEquals(4, page.totalPages)
                }
            }
            assertEquals(15, requests.size)
            requests.forEachIndexed { index, request ->
                assertEquals("/api/v1/fresh", request.url.encodedPath)
                assertEquals("2", request.url.queryParameter("page"))
                val category = FreshCategory.entries[index / 3]
                assertEquals(category.mediaType.value, request.url.queryParameter("mediaType"))
                assertEquals(category.classification?.value, request.url.queryParameter("category"))
                assertEquals(FreshSort.entries[index % 3].query.value, request.url.queryParameter("sort"))
            }
        }

    @Test fun mapsMovieAndTvStateAndKeepsTypedCardIdentity() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed =
                backend(
                    """{"page":1,"totalPages":1,"totalResults":2,"status":{"status":"ready","refreshing":false,"backfillComplete":true,"itemCount":2,"pendingWork":0},"results":[{"id":42,"mediaType":"movie","freshFirstSeenAt":"2026-11-12T00:00:00Z","title":"Movie","posterPath":"/movie.jpg","releaseDate":"2024-03-01","mediaInfo":{"status":2}},{"id":42,"mediaType":"tv","freshFirstSeenAt":"2026-11-11T00:00:00Z","name":"Show","firstAirDate":"2023-06-01","mediaInfo":{"status":5}}]}""",
                    requests,
                )
            val page = feed.page(FreshCategory.ALL, FreshSort.FRESHEST, 1)
            assertEquals(listOf(SeerrItemType.MOVIE, SeerrItemType.TV), page.entries.map { it.item.type })
            assertEquals(
                2,
                page.entries
                    .map { it.key }
                    .toSet()
                    .size,
            )
            assertEquals(SeerrAvailability.PENDING, page.entries[0].item.availability)
            assertEquals(SeerrAvailability.AVAILABLE, page.entries[1].item.availability)
            assertEquals("Movie", page.entries[0].item.title)
            assertEquals("Show", page.entries[1].item.title)
            assertTrue(
                page.entries[0]
                    .item.posterUrl!!
                    .contains("/movie.jpg"),
            )
            validateFreshPage(page, 1)
        }

    @Test fun crossMediaCategoriesRetainMovieAndTvCards() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed =
                backend(
                    """{"page":1,"totalPages":1,"totalResults":2,"status":{"status":"ready","refreshing":false,"backfillComplete":true,"itemCount":2,"pendingWork":0},"results":[{"id":42,"mediaType":"movie","freshFirstSeenAt":"2026-11-12T00:00:00Z","title":"Movie"},{"id":42,"mediaType":"tv","freshFirstSeenAt":"2026-11-11T00:00:00Z","name":"Show"}]}""",
                    requests,
                )
            listOf(FreshCategory.ANIMATION, FreshCategory.DOCUMENTARY).forEach { category ->
                val page = feed.page(category, FreshSort.FRESHEST, 1)
                assertEquals(listOf(SeerrItemType.MOVIE, SeerrItemType.TV), page.entries.map { it.item.type })
                assertEquals(
                    2,
                    page.entries
                        .map { it.key }
                        .toSet()
                        .size,
                )
                validateFreshPage(page, 1)
            }
            assertEquals(listOf("animation", "documentary"), requests.map { it.url.queryParameter("category") })
            assertTrue(requests.all { it.url.queryParameter("mediaType") == "all" })
        }

    @Test fun disabledAndMalformedPagesAreDistinguished() =
        runTest {
            val requests = mutableListOf<Request>()
            val disabled =
                backend(
                    """{"page":1,"totalPages":0,"totalResults":0,"status":{"status":"disabled","refreshing":false,"backfillComplete":false,"itemCount":0,"pendingWork":0},"results":[]}""",
                    requests,
                ).page(FreshCategory.ALL, FreshSort.FRESHEST, 1)
            assertEquals(FreshStatus.Status.DISABLED, disabled.status)
            validateFreshPage(disabled, 1)
            assertThrows(IllegalArgumentException::class.java) {
                validateFreshPage(disabled.copy(totalResults = 1, status = FreshStatus.Status.READY), 1)
            }
        }
}
