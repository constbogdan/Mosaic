package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.TvRequestOutcome
import com.github.damontecres.wholphin.api.seerr.infrastructure.Serializer
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RequestUser
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.api.seerr.requestSession
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.TvRequest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrRequestActionsTest {
    @Test fun ownedPendingStandardUsesPut() = runBlocking { assertSubmission(listOf(record()), false, "PUT", 91) }

    @Test fun ownedPendingFourKUsesPut() = runBlocking { assertSubmission(listOf(record(is4k = true)), true, "PUT", 91) }

    @Test fun oppositeQualityUsesPost() = runBlocking { assertSubmission(listOf(record()), true, "POST") }

    @Test fun anotherOwnerUsesPost() = runBlocking { assertSubmission(listOf(record(owner = 8)), false, "POST") }

    @Test fun approvedAndCompletedRequestsUsePost() =
        runBlocking {
            for (status in listOf(2, 5)) assertSubmission(listOf(record(status = status)), false, "POST")
        }

    @Test fun differentQualityRequestsChooseExactMatchingRequest() =
        runBlocking {
            val records = listOf(record(91), record(92, is4k = true))
            assertSubmission(records, false, "PUT", 91)
            assertSubmission(records, true, "PUT", 92)
        }

    @Test fun disjointSeasonStartsSeparateRequestInsteadOfReplacingPendingSeason() =
        runBlocking {
            assertSubmission(listOf(record(season = 1)), false, "POST")
        }

    @Test fun unrelatedMediaAndWrongTypeCannotBecomeEditTargets() =
        runBlocking {
            assertSubmission(listOf(record().copy(media = MediaInfo(id = 999, tmdbId = 999))), false, "POST")
            assertSubmission(listOf(record().copy(type = "movie")), false, "POST")
        }

    @Test fun ambiguousOverlappingPendingEditsFailClosed() {
        val fixture = Fixture()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.submit(listOf(record(91), record(92)), false) }
        }
        assertTrue(fixture.http.calls.isEmpty())
    }

    @Test fun mismatchedMediaIdentityMakesNoRequest() {
        val fixture = Fixture()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.service.requestTv(TvDetails(id = 99), request(false)) }
        }
        assertTrue(fixture.http.calls.isEmpty())
    }

    @Test fun permissionAndEnabledCapabilityAreRecheckedByService() {
        for (session in listOf(requestSession(permissions = 0), requestSession(tv4k = false))) {
            val fixture = Fixture(session)
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.submit(emptyList(), true) } }
            assertTrue(fixture.http.calls.isEmpty())
        }
    }

    @Test fun ownedPendingCanBeCancelledEvenWithoutCreationPermission() =
        runBlocking {
            val fixture = Fixture(requestSession(permissions = 0))
            fixture.cancel(listOf(record()))
            assertEquals(
                "DELETE",
                fixture.http.calls
                    .single()
                    .method,
            )
            assertEquals(
                "/api/v1/request/91",
                fixture.http.calls
                    .single()
                    .url.encodedPath,
            )
        }

    @Test fun unrelatedOwnerFirstDoesNotChangeCancellationIdentity() =
        runBlocking {
            val fixture = Fixture()
            fixture.cancel(listOf(record(88, owner = 8), record(91)))
            assertEquals(
                "/api/v1/request/91",
                fixture.http.calls
                    .single()
                    .url.encodedPath,
            )
        }

    @Test fun managerMayCancelEachBackendAuthorizedStatusWhenUnambiguous() =
        runBlocking {
            for (status in 1..5) {
                val fixture = Fixture(requestSession(permissions = 16))
                fixture.cancel(listOf(record(status = status, owner = 8)))
                assertEquals(
                    "/api/v1/request/91",
                    fixture.http.calls
                        .single()
                        .url.encodedPath,
                )
            }
        }

    @Test fun ownerCannotCancelNonPendingOrOtherUsersRequest() {
        for (candidate in listOf(record(owner = 8), record(status = 2), record(status = 3), record(status = 4), record(status = 5))) {
            val fixture = Fixture()
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.cancel(listOf(candidate)) } }
            assertTrue(fixture.http.calls.isEmpty())
        }
    }

    @Test fun wrongQualityMissingIdAndMediaIdDoNotDeleteAnything() {
        for ((id, quality) in listOf(91 to true, 42 to false, 84 to false, 999 to false)) {
            val fixture = Fixture()
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.cancel(listOf(record()), id, quality) } }
            assertTrue(fixture.http.calls.isEmpty())
        }
    }

    @Test fun twoAuthorizedQualityVariantsAreAmbiguousEvenForManager() {
        for (permissions in listOf(32, 16)) {
            val fixture = Fixture(requestSession(permissions = permissions))
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.cancel(listOf(record(), record(92, is4k = true))) } }
            assertTrue(fixture.http.calls.isEmpty())
        }
    }

    @Test fun fourKCancellationDoesNotDeleteStandardRequest() =
        runBlocking {
            val fixture = Fixture()
            fixture.cancel(listOf(record(88, owner = 8), record(92, is4k = true)), 92, true)
            assertEquals(
                "/api/v1/request/92",
                fixture.http.calls
                    .single()
                    .url.encodedPath,
            )
        }

    @Test fun missingOrForeignContextRejectsCancellation() {
        for (records in listOf(
            emptyList(),
            listOf(record().copy(media = MediaInfo(id = 999, tmdbId = 999))),
            listOf(record().copy(type = "movie")),
        )) {
            val fixture = Fixture()
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.cancel(records) } }
            assertTrue(fixture.http.calls.isEmpty())
        }
    }

    @Test fun sessionChangeDuringDeletePropagatesCancellation() {
        val fixture = Fixture()
        fixture.http.onResponse = { fixture.current.value = requestSession(userId = 8) }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.cancel(listOf(record())) } }
        assertEquals(1, fixture.http.calls.size)
    }

    @Test fun actualDtoDecodingKeepsQualityAvailabilityIndependent() {
        val movie =
            Serializer.kotlinxSerializationJson.decodeFromString<MovieDetails>(
                """{"id":42,"mediaInfo":{"id":84,"tmdbId":42,"status":5,"status4k":1}}""",
            )
        assertFalse(requestSession().canRequestMovie(movie, false))
        assertTrue(requestSession().canRequestMovie(movie, true))
    }

    @Test fun staleCallerClientCannotDeleteThroughReplacementSession() {
        val fixture = Fixture()
        val previous = fixture.boundApi
        val replacement = TvRequestHttpFixture()
        fixture.boundApi = replacement.api
        fixture.current.value = requestSession(16, userId = 8)
        assertThrows(CancellationException::class.java) {
            runBlocking { fixture.service.cancelRequest(media(listOf(record())), SeerrItemType.TV, 42, 91, false, previous) }
        }
        assertTrue(fixture.http.calls.isEmpty())
        assertTrue(replacement.calls.isEmpty())
    }

    private suspend fun assertSubmission(
        records: List<MediaRequest>,
        is4k: Boolean,
        method: String,
        id: Int? = null,
    ) {
        val fixture = Fixture()
        assertEquals(TvRequestOutcome.NoNewRequest, fixture.submit(records, is4k))
        assertEquals(
            method,
            fixture.http.calls
                .single()
                .method,
        )
        assertEquals(
            "/api/v1/request" + (id?.let { "/$it" } ?: ""),
            fixture.http.calls
                .single()
                .url.encodedPath,
        )
    }

    private class Fixture(
        session: CurrentSeerr = requestSession(),
    ) {
        val http = TvRequestHttpFixture()
        var boundApi = http.api
        val current = MutableStateFlow<CurrentSeerr?>(session)
        private val wrapper = mockk<SeerrApi> { every { api } answers { boundApi } }
        private val repository =
            mockk<SeerrServerRepository> {
                every { this@mockk.current } returns this@Fixture.current
                every { currentUserId } returns this@Fixture.current.map { it?.config?.id }
            }
        val service = SeerrService(wrapper, repository, mockk())

        suspend fun submit(
            records: List<MediaRequest>,
            is4k: Boolean,
        ) = service.requestTv(TvDetails(id = 42, mediaInfo = media(records)), request(is4k))

        suspend fun cancel(
            records: List<MediaRequest>,
            id: Int = 91,
            is4k: Boolean = false,
        ) {
            http.code = 204
            http.body = ""
            service.cancelRequest(media(records), SeerrItemType.TV, 42, id, is4k)
        }
    }

    companion object {
        private fun media(records: List<MediaRequest>) = MediaInfo(id = 84, tmdbId = 42, status = 1, status4k = 1, requests = records)

        private fun request(is4k: Boolean) = TvRequest(SeerrRequestData(), 42, listOf(2), is4k, null, null)

        private fun record(
            id: Int = 91,
            status: Int = 1,
            owner: Int = 7,
            is4k: Boolean = false,
            season: Int = 2,
        ) = MediaRequest(
            id = id,
            status = status,
            type = "tv",
            is4k = is4k,
            requestedBy = RequestUser(id = owner),
            media = MediaInfo(id = 84, tmdbId = 42),
            seasons = listOf(Season(seasonNumber = season, status = status)),
        )
    }
}
