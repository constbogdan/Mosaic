package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WatchlistContractTest {
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val local = CurrentUser(JellyfinServer(id, null, "http://jellyfin.invalid", null), JellyfinUser(1, id, "viewer", id, null))
    private val current =
        CurrentSeerr(
            SeerrServer(1, "http://seerr.invalid"),
            SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "viewer", null, null),
            User(id = 7, jellyfinUserId = id.toString()),
            PublicSettings(),
        )

    private fun backend(handler: (Request) -> Pair<Int, String>): WatchlistBackend {
        val http =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val (status, body) = handler(chain.request())
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("fixture")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return PersonalWatchlistBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
    }

    @Test fun personalIdentityRejectsSharedKeysUnlinkedAndWrongJellyfinUsers() {
        assertNotNull(personalWatchlistIdentity(current, local))
        assertNull(personalWatchlistIdentity(current.copy(user = current.user.copy(authMethod = SeerrAuthMethod.API_KEY)), local))
        assertNull(personalWatchlistIdentity(current.copy(config = User(id = 7)), local))
        assertNull(personalWatchlistIdentity(current.copy(config = User(id = 7, jellyfinUserId = UUID.randomUUID().toString())), local))
        assertNull(personalWatchlistIdentity(current, local.copy(user = local.user.copy(rowId = 2))))
        assertNull(personalWatchlistIdentity(null, local))
        assertNotEquals(
            personalWatchlistIdentity(current, local),
            personalWatchlistIdentity(current.copy(server = current.server.copy(url = "http://other.invalid")), local),
        )
        assertNotEquals(
            personalWatchlistIdentity(current, local),
            personalWatchlistIdentity(current.copy(config = current.config.copy(id = 8)), local),
        )
        assertEquals(
            personalWatchlistIdentity(current, local),
            personalWatchlistIdentity(current, local.copy(server = local.server.copy(lastUsed = java.time.ZonedDateTime.now()))),
        )
    }

    @Test fun everyCategoryAndPageExplicitlyIncludesWatchedAndOwnedItems() =
        runTest {
            val requests = mutableListOf<Request>()
            val backend =
                backend { request ->
                    requests += request
                    200 to
                        """{"page":2,"totalPages":3,"totalResults":45,"source":"local","results":[
                            {"tmdbId":7,"mediaType":"movie","title":"Saved watched title","createdAt":"2026-11-10T00:00:00Z"}
                        ]}"""
                }
            WatchlistCategory.entries.forEach { category ->
                val page = backend.page(category, 2)
                assertEquals(45, page.totalResults)
                assertEquals("Saved watched title", page.entries.single().title)
                assertNull(page.entries.single().posterPath)
                assertNull(
                    page.entries
                        .single()
                        .item.posterUrl,
                )
                assertNull(page.entries.single().genreIds)
            }
            assertEquals(listOf("all", "movies", "series", "animation"), requests.map { it.url.queryParameter("category") })
            requests.forEach { request ->
                assertEquals("/api/v1/discover/watchlist", request.url.encodedPath)
                mapOf(
                    "owner" to "me",
                    "sort" to "added_desc",
                    "watched" to "all",
                    "libraryStatus" to "any",
                    "includeArtwork" to "true",
                    "page" to "2",
                ).forEach { (key, value) ->
                    assertEquals(value, request.url.queryParameter(key))
                }
            }
        }

    @Test fun membershipRequiresExactTypedResponseAndUnsupportedIsNotEmpty() =
        runTest {
            val keys = linkedSetOf(MediaKey.Catalog(CatalogMediaType.MOVIE, 7), MediaKey.Catalog(CatalogMediaType.SERIES, 7))
            val backend =
                backend { request ->
                    assertEquals("movie:7,tv:7", request.url.queryParameter("items"))
                    assertNull(request.url.queryParameter("owner"))
                    200 to
                        """{"results":[{"tmdbId":7,"mediaType":"movie","onWatchlist":true},
                            {"tmdbId":7,"mediaType":"tv","onWatchlist":false}]}"""
                }
            assertEquals(listOf(true, false), backend.membership(keys).values.toList())
            assertTrue(runCatching { backend { 200 to """{"results":[]}""" }.membership(keys) }.isFailure)
            assertTrue(runCatching { backend { 404 to "{}" }.membership(keys) }.exceptionOrNull() is WatchlistUnavailable)
        }

    @Test fun ownMutationsUseTypedBodyAndDeleteQueryWithoutOwnerOverrides() =
        runTest {
            val requests = mutableListOf<Request>()
            val backend =
                backend { request ->
                    requests += request
                    if (request.method == "POST") {
                        val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                        assertTrue(body.contains("\"mediaType\":\"tv\""))
                        assertTrue(body.contains("\"tmdbId\":7"))
                        assertFalse(body.contains("userId"))
                        assertFalse(body.contains("requestedBy"))
                        201 to """{"tmdbId":7,"mediaType":"tv","title":"Saved show",
                            "createdAt":"2026-11-10T00:00:00Z"}"""
                    } else {
                        204 to ""
                    }
                }
            val key = MediaKey.Catalog(CatalogMediaType.SERIES, 7)
            backend.add(key, "Saved show")
            backend.remove(key)
            assertEquals(listOf("POST", "DELETE"), requests.map { it.method })
            assertEquals("/api/v1/watchlist/7", requests.last().url.encodedPath)
            assertEquals("tv", requests.last().url.queryParameter("mediaType"))
            assertNull(requests.last().url.queryParameter("owner"))
        }
}
