package com.github.damontecres.wholphin.api.seerr

import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.api.seerr.infrastructure.ServerException
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestRequestIdPutRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestUser
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.TvRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

class TvRequestSubmissionTest {
    @Test
    fun post201DecodesRequestAndPreservesGeneratedBody() =
        runBlocking {
            val fixture = TvRequestHttpFixture(201, """{"id":91,"status":1,"type":"tv","is4k":true}""")
            val result = fixture.submit() as TvRequestOutcome.Submitted
            assertEquals(91, result.request.id)
            assertEquals(1, result.request.status)
            assertEquals(true, result.request.is4k)
            val sent = fixture.calls.single()
            assertEquals("POST", sent.method)
            assertEquals("/api/v1/request", sent.url.encodedPath)
            val buffer = okio.Buffer()
            sent.body!!.writeTo(buffer)
            assertTrue(buffer.readUtf8().contains("\"mediaType\":\"tv\""))
        }

    @Test
    fun put200PreservesIdentityAndStatus() =
        runBlocking {
            val fixture = TvRequestHttpFixture(200, """{"id":91,"status":2,"type":"tv"}""")
            val result = fixture.submit(edit = true) as TvRequestOutcome.Submitted
            assertEquals(91, result.request.id)
            assertEquals(2, result.request.status)
            assertEquals("PUT", fixture.calls.single().method)
            assertEquals(
                "/api/v1/request/91",
                fixture.calls
                    .single()
                    .url.encodedPath,
            )
        }

    @Test
    fun post202HasNoIdentityAndDoesNotResubmit() =
        runBlocking {
            val fixture = TvRequestHttpFixture()
            assertEquals(TvRequestOutcome.NoNewRequest, fixture.submit())
            assertEquals(1, fixture.calls.size)
        }

    @Test
    fun put202HasNoIdentityAndDoesNotResubmit() =
        runBlocking {
            val fixture = TvRequestHttpFixture()
            assertEquals(TvRequestOutcome.NoNewRequest, fixture.submit(edit = true))
            assertEquals(1, fixture.calls.size)
        }

    @Test
    fun malformedSuccessBodiesFailClosedForCreateAndEdit() {
        for (edit in listOf(false, true)) {
            for (body in listOf(
                "",
                "[]",
                "not-json",
                "{}",
                "{\"message\":\"unexpected\"}",
                "{\"id\":0,\"status\":1}",
                "{\"id\":91,\"status\":99}",
                "{\"id\":91,\"status\":1,\"type\":\"movie\"}",
            )) {
                assertThrows(TvRequestResponseException::class.java) {
                    runBlocking { TvRequestHttpFixture(if (edit) 200 else 201, body).submit(edit) }
                }
            }
        }
    }

    @Test
    fun unknownOrAmbiguous202BodiesFailClosed() {
        for (body in listOf(
            "{}",
            "{\"message\":\"Accepted\"}",
            "{\"message\":\"No seasons available to request\",\"id\":91}",
            "{\"message\":\"No seasons available to request\",\"status\":2}",
        )) {
            assertThrows(TvRequestResponseException::class.java) { runBlocking { TvRequestHttpFixture(202, body).submit() } }
        }
    }

    @Test
    fun unexpectedSuccessfulStatusDoesNotBecomeAnOutcome() {
        for (status in listOf(200, 204, 205)) {
            assertThrows(TvRequestResponseException::class.java) {
                runBlocking { TvRequestHttpFixture(status, "{\"id\":91,\"status\":1}").submit() }
            }
        }
    }

    @Test
    fun httpErrorsKeepGeneratedErrorTypesWithoutRetries() {
        for (edit in listOf(false, true)) {
            for (code in listOf(400, 401, 403, 409, 500, 503)) {
                val fixture = TvRequestHttpFixture(code, "{\"message\":\"fixture failure\"}")
                val expected = if (code < 500) ClientException::class.java else ServerException::class.java
                assertThrows(expected) { runBlocking { fixture.submit(edit) } }
                assertEquals(1, fixture.calls.size)
            }
        }
    }

    @Test
    fun realServiceKeepsExistingPostAndOwnedPendingPutSelection() =
        runBlocking {
            for (edit in listOf(false, true)) {
                val fixture = TvRequestHttpFixture()
                val wrapper = mockk<SeerrApi> { every { api } returns fixture.api }
                val repository =
                    mockk<SeerrServerRepository> {
                        every { currentUserId } returns flowOf(7)
                        every { this@mockk.current } returns flowOf(requestSession())
                    }
                val service = SeerrService(wrapper, repository, mockk())
                val existing =
                    if (edit) {
                        listOf(
                            MediaRequest(
                                id = 91,
                                status = 1,
                                type = "tv",
                                seasons =
                                    listOf(
                                        com.github.damontecres.wholphin.api.seerr.model
                                            .Season(seasonNumber = 2, status = 1),
                                    ),
                                requestedBy = RequestUser(id = 7),
                            ),
                        )
                    } else {
                        emptyList()
                    }
                assertEquals(
                    TvRequestOutcome.NoNewRequest,
                    service.requestTv(TvDetails(id = 42, mediaInfo = MediaInfo(requests = existing)), tvRequest()),
                )
                assertEquals(if (edit) "PUT" else "POST", fixture.calls.single().method)
            }
        }

    @Test
    fun serviceDiscardsResponseAfterSessionClientChanges() {
        val fixture = TvRequestHttpFixture(201, "{\"id\":91,\"status\":1}")
        var current = fixture.api
        val wrapper = mockk<SeerrApi> { every { api } answers { current } }
        fixture.onResponse = { current = SeerrApiClient("http://replacement.invalid/api/v1", null, OkHttpClient()) }
        val repository =
            mockk<SeerrServerRepository> {
                every { currentUserId } returns flowOf(7)
                every { this@mockk.current } returns flowOf(requestSession())
            }
        val service = SeerrService(wrapper, repository, mockk())
        assertThrows(CancellationException::class.java) { runBlocking { service.requestTv(TvDetails(id = 42), tvRequest()) } }
        assertEquals(1, fixture.calls.size)
    }

    private fun tvRequest() = TvRequest(SeerrRequestData(), 42, listOf(2), false, null, null)
}

internal fun requestSession(
    permissions: Int = 32 or 1024,
    movie4k: Boolean = true,
    tv4k: Boolean = true,
    userId: Int = 7,
) = com.github.damontecres.wholphin.services.CurrentSeerr(
    com.github.damontecres.wholphin.data.model
        .SeerrServer(id = 1, url = "http://fixture.invalid"),
    com.github.damontecres.wholphin.data.model.SeerrUser(
        1,
        1,
        com.github.damontecres.wholphin.data.model.SeerrAuthMethod.API_KEY,
        null,
        null,
        null,
    ),
    com.github.damontecres.wholphin.services
        .SeerrUserConfig(id = userId, permissions = permissions),
    com.github.damontecres.wholphin.api.seerr.model
        .PublicSettings(movie4kEnabled = movie4k, series4kEnabled = tv4k),
)

/** Real OkHttp/generated transport and JSON decoding; the interceptor forbids external traffic. */
internal class TvRequestHttpFixture(
    var code: Int = 202,
    var body: String = "{\"message\":\"No seasons available to request\"}",
) {
    val calls = mutableListOf<Request>()
    var onResponse: () -> Unit = {}
    val api =
        SeerrApiClient(
            "http://fixture.invalid/api/v1",
            "fixture-only",
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    calls.add(chain.request())
                    onResponse()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("fixture")
                        .header("Content-Type", "application/json")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build(),
        )

    suspend fun submit(edit: Boolean = false): TvRequestOutcome =
        if (edit) {
            api.tvRequestSubmission.update(
                "91",
                RequestRequestIdPutRequest(mediaType = RequestRequestIdPutRequest.MediaType.TV, seasons = listOf(2)),
            )
        } else {
            api.tvRequestSubmission.create(
                RequestPostRequest(mediaType = RequestPostRequest.MediaType.TV, mediaId = 42, seasons = listOf(2)),
            )
        }
}
