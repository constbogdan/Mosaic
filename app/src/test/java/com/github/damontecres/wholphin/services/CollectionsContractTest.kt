package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionItem
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.validateCollectionsPage
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

class CollectionsContractTest {
    private val current =
        CurrentSeerr(
            SeerrServer(1, "http://seerr.invalid"),
            SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "admin", null, null),
            User(id = 7),
            PublicSettings(),
        )

    private fun backend(
        body: String,
        requests: MutableList<Request>,
        code: Int = 200,
    ): CollectionsBackend {
        val http =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requests += chain.request()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("fixture")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return SeerrCollectionsBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
    }

    @Test fun onlySupportedServerSideFilterSortAndPaginationAreUsed() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed =
                backend(
                    """{"pageInfo":{"pages":3,"page":2,"results":45,"pageSize":20},"results":[],"status":"pending","stale":false,"discoveredCollections":45,"failedCollections":0,"refreshedAt":null}""",
                    requests,
                )
            CollectionsFilter.entries.forEach { filter ->
                CollectionsSort.entries.forEach { sort -> feed.page(20, filter, sort) }
            }
            assertEquals(18, requests.size)
            requests.forEachIndexed { index, request ->
                assertEquals("/api/v1/library/collections", request.url.encodedPath)
                assertEquals("20", request.url.queryParameter("take"))
                assertEquals("20", request.url.queryParameter("skip"))
                assertEquals(CollectionsFilter.entries[index / 6].query.value, request.url.queryParameter("completeness"))
                assertEquals(CollectionsSort.entries[index % 6].query.value, request.url.queryParameter("sort"))
                assertEquals(CollectionsSort.entries[index % 6].direction.value, request.url.queryParameter("sortDirection"))
            }
        }

    @Test fun eligibleCountAndUnknownCountArePreservedWithoutInferringAllParts() =
        runTest {
            val requests = mutableListOf<Request>()
            val feed =
                backend(
                    """{"pageInfo":{"pages":1,"page":1,"results":2,"pageSize":20},"results":[{"id":7,"name":"Saga","overview":null,"posterPath":"/poster.jpg","backdropPath":null,"availableMovies":1,"totalMovies":4,"completeness":"incomplete"},{"id":8,"name":"Unknown","overview":null,"posterPath":null,"backdropPath":null,"availableMovies":null,"totalMovies":null,"completeness":"unknown"}],"status":"partial","stale":true,"discoveredCollections":3,"failedCollections":1,"refreshedAt":null}""",
                    requests,
                )
            val page = feed.page(0, CollectionsFilter.ALL, CollectionsSort.NAME_ASC)
            validateCollectionsPage(page, 0)
            assertEquals("tmdb:collection:7", page.entries[0].gridId)
            assertEquals(1, page.entries[0].summary.availableMovies)
            assertEquals(4, page.entries[0].summary.totalMovies)
            assertEquals(null, page.entries[1].posterUrl)
            assertEquals(null, page.entries[1].summary.availableMovies)
            assertTrue(page.stale)
        }

    @Test fun malformedCompletePageIsRejected() {
        val entry =
            CollectionsEntry(
                LibraryCollectionItem(7, "Saga", null, null, null, 1, 4, LibraryCollectionItem.Completeness.INCOMPLETE),
                null,
                null,
            )
        val page = CollectionsPageData(0, 20, 2, LibraryCollectionsResponse.Status.COMPLETE, false, 0, listOf(entry))
        assertThrows(IllegalArgumentException::class.java) { validateCollectionsPage(page, 0) }
        assertThrows(IllegalArgumentException::class.java) { validateCollectionsPage(page.copy(entries = listOf(entry, entry)), 0) }
    }
}
