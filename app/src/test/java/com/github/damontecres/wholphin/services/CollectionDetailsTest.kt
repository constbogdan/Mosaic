package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.MovieDetailsCollection
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.CollectionBadgeCounts
import com.github.damontecres.wholphin.ui.collections.CollectionFocusSection
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import com.github.damontecres.wholphin.ui.collections.collectionBadgeCounts
import com.github.damontecres.wholphin.ui.collections.collectionDetailCounts
import com.github.damontecres.wholphin.ui.collections.collectionHasAvailableReleasedMember
import com.github.damontecres.wholphin.ui.collections.collectionMemberActionable
import com.github.damontecres.wholphin.ui.collections.collectionMembersOrdered
import com.github.damontecres.wholphin.ui.collections.collectionMovieArtworkAlpha
import com.github.damontecres.wholphin.ui.collections.collectionMovieDownTargetsSimilar
import com.github.damontecres.wholphin.ui.collections.collectionMovieUpTargetsPoster
import com.github.damontecres.wholphin.ui.collections.collectionUnreleasedPosterMatrix
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailsTest {
    @Test fun collectionMapperRetainsOptionalCatalogMetadata() {
        val backend = SeerrCollectionsBackend(mockk(relaxed = true), mockk(relaxed = true))
        val movie =
            backend.movie(
                MovieResult(
                    id = 11,
                    mediaType = "movie",
                    genreIds = listOf(12, 28),
                    voteAverage = BigDecimal("7.4"),
                    voteCount = 145165,
                    popularity = BigDecimal("8.82694"),
                ),
            )
        assertEquals(listOf(12, 28), movie.genreIds)
        assertEquals(7.4, movie.voteAverage!!, 0.0)
        assertEquals(145165, movie.voteCount)
        assertEquals(8.82694, movie.popularity!!, 0.00001)
    }

    @Test fun collectionSectionTraversalDoesNotSkipMoviesFromSimilar() {
        // CardGrid can retain movie index zero while its Similar footer owns focus.
        assertFalse(collectionMovieUpTargetsPoster(CollectionFocusSection.SIMILAR))
        assertTrue(collectionMovieUpTargetsPoster(CollectionFocusSection.MOVIES))
        assertFalse(collectionMovieUpTargetsPoster(CollectionFocusSection.POSTER))
        assertFalse(collectionMovieDownTargetsSimilar(CollectionFocusSection.SIMILAR, 0, 2, 6, true))
    }

    @Test fun collectionDownEntersSimilarOnlyFromLastPopulatedMovieRow() {
        assertFalse(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 0, 8, 6, true))
        assertTrue(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 6, 8, 6, true))
        assertTrue(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 7, 8, 6, true))
        assertTrue(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 0, 2, 6, true))
        assertFalse(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 0, 2, 6, false))
        assertFalse(collectionMovieDownTargetsSimilar(CollectionFocusSection.MOVIES, 2, 2, 6, true))
    }

    @Test fun collectionArtworkUsesReleasedPerMovieAvailabilityRatherThanAggregateCounts() {
        val today = LocalDate.of(2026, 11, 20)

        fun movie(
            id: Int,
            availability: SeerrAvailability,
            date: LocalDate? = today,
        ) = DiscoverItem(id, SeerrItemType.MOVIE, "Movie $id", null, null, availability, date, null, null, null, null)

        val available = movie(1, SeerrAvailability.AVAILABLE)
        val notMarkedAvailable =
            listOf(
                SeerrAvailability.UNKNOWN,
                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                SeerrAvailability.PARTIALLY_AVAILABLE,
                SeerrAvailability.BLOCKLISTED,
                SeerrAvailability.DELETED,
            ).mapIndexed { index, availability -> movie(index + 2, availability) }
        val mixed = listOf(available) + notMarkedAvailable
        assertTrue(collectionHasAvailableReleasedMember(mixed, today))
        assertEquals(1f, collectionMovieArtworkAlpha(available.availability, true, true))
        notMarkedAvailable.forEach { assertEquals(.45f, collectionMovieArtworkAlpha(it.availability, true, true)) }
        assertEquals(.45f, collectionMovieArtworkAlpha(null, true, true))

        val noneAvailable = CollectionDetailsData(7, "Saga", null, null, null, notMarkedAvailable, availableMovies = 10, totalMovies = 26)
        assertFalse(collectionHasAvailableReleasedMember(noneAvailable.movies, today))
        notMarkedAvailable.forEach { assertEquals(1f, collectionMovieArtworkAlpha(it.availability, true, false)) }
        assertTrue(collectionHasAvailableReleasedMember(listOf(available, movie(8, SeerrAvailability.AVAILABLE)), today))
        assertEquals(1f, collectionMovieArtworkAlpha(SeerrAvailability.AVAILABLE, true, true))

        val future = movie(9, SeerrAvailability.AVAILABLE, today.plusDays(1))
        assertFalse(collectionHasAvailableReleasedMember(listOf(future) + notMarkedAvailable, today))
        assertEquals(1f, collectionMovieArtworkAlpha(future.availability, false, true))
        assertEquals(1f, collectionMovieArtworkAlpha(SeerrAvailability.UNKNOWN, false, true))
        assertEquals(1f, collectionMovieArtworkAlpha(SeerrAvailability.UNKNOWN, false, false))
    }

    @Test fun unreleasedPosterMatrixFullyDesaturatesArtworkWithoutReducingAlpha() {
        val values = collectionUnreleasedPosterMatrix().values
        for (channel in 0..2) {
            assertEquals(values[channel], values[5 + channel], .0001f)
            assertEquals(values[channel], values[10 + channel], .0001f)
        }
        assertEquals(1f, values[0] + values[1] + values[2], .0001f)
        assertEquals(1f, values[18], .0001f)
        assertEquals(1f, collectionMovieArtworkAlpha(SeerrAvailability.UNKNOWN, false, true))
    }

    @Test fun countPrecedenceAndHiddenUnknownOrZero() {
        assertEquals(CollectionBadgeCounts(10, 26), collectionDetailCounts(10, 26, null, null))
        assertEquals(CollectionBadgeCounts(10, 26), collectionDetailCounts(null, null, 10, 26))
        assertEquals(CollectionBadgeCounts(10, 26), collectionDetailCounts(10, 26, 1, 2))
        assertEquals(null, collectionBadgeCounts(0, 26))
        assertEquals(null, collectionBadgeCounts(null, 26))
        assertEquals(null, collectionBadgeCounts(2, 1))
        assertEquals(null, collectionBadgeCounts(1, 0))
        assertEquals(0, collectionDetailCounts(0, 26, 10, 26)?.available)
        assertEquals(CollectionBadgeCounts(0, 0), collectionDetailCounts(0, 0, 10, 26))
        assertEquals(10, collectionDetailCounts(12, 5, 10, 26)?.available)
    }

    @Test fun releasePresentationUsesLocalCalendarDateAndKeepsUnknownMembers() {
        val today = LocalDate.of(2027, 1, 1)

        fun movie(
            id: Int,
            date: LocalDate?,
        ) = DiscoverItem(id, SeerrItemType.MOVIE, "Movie $id", null, null, SeerrAvailability.UNKNOWN, date, null, null, null, null)
        val released = movie(1, today.minusDays(1))
        val todayMovie = movie(2, today)
        val future = movie(3, today.plusDays(1))
        val missing = movie(4, null)
        assertTrue(collectionMemberActionable(released, today))
        assertTrue(collectionMemberActionable(todayMovie, today))
        assertFalse(collectionMemberActionable(future, today))
        assertFalse(collectionMemberActionable(missing, today))
        assertEquals(listOf(1, 2, 3, 4), collectionMembersOrdered(listOf(missing, future, todayMovie, released), today).map { it.id })
        val boundary = Instant.parse("2026-11-19T22:30:00Z")
        val bucharestToday = LocalDate.ofInstant(boundary, ZoneId.of("Europe/Bucharest"))
        val losAngelesToday = LocalDate.ofInstant(boundary, ZoneId.of("America/Los_Angeles"))
        assertTrue(collectionMemberActionable(movie(5, LocalDate.of(2026, 11, 20)), bucharestToday))
        assertFalse(collectionMemberActionable(movie(5, LocalDate.of(2026, 11, 20)), losAngelesToday))
    }

    @Test fun blankAndMalformedReleaseDatesRemainVisibleAndNonActionable() =
        runTest {
            val detail =
                backend(
                    """{"id":7,"name":"Saga","parts":[{"id":1,"mediaType":"movie","releaseDate":"2020-01-01"},{"id":2,"mediaType":"movie","releaseDate":"  "},{"id":3,"mediaType":"movie","releaseDate":"not-a-date"},{"id":4,"mediaType":"movie","releaseDate":null}]}""",
                ).detail(7)
            assertEquals(listOf(1, 2, 3, 4), detail.movies.map { it.id })
            assertTrue(collectionMemberActionable(detail.movies[0], LocalDate.of(2026, 11, 20)))
            detail.movies.drop(1).forEach { assertFalse(collectionMemberActionable(it, LocalDate.of(2026, 11, 20))) }
        }

    @Test fun collectionProviderIdsRequirePositiveTypedTmdbMovieIdentity() {
        assertEquals(42 to 7, collectionProviderIds(mapOf("Tmdb" to "42", "TmdbCollection" to "7")))
        assertEquals(42 to null, collectionProviderIds(mapOf("Tmdb" to "42", "TmdbCollection" to "bad")))
        assertEquals(null, collectionProviderIds(mapOf("TmdbCollection" to "7")))
        assertEquals(null, collectionProviderIds(mapOf("Tmdb" to "0", "TmdbCollection" to "7")))
    }

    @Test fun existingMovieDetailsCollectionMapsWithoutIndexRequest() {
        val link = backend("{}").linkFromMovie(MovieDetails(id = 42, collection = MovieDetailsCollection(7, "Saga", null, null)))
        assertEquals(7, link?.id)
        assertEquals("Saga", link?.name)
        assertEquals(null, backend("{}").linkFromMovie(MovieDetails(id = 42, collection = MovieDetailsCollection(0, "Bad"))))
        assertEquals(null, backend("{}").linkFromMovie(MovieDetails(id = 42, collection = MovieDetailsCollection(7, " "))))
    }

    @Test fun jellyfinClaimRequiresMatchingTypedMovieMember() =
        runTest {
            val valid =
                backend("""{"id":7,"name":"Saga","parts":[{"id":42,"mediaType":"movie","title":"Film"}]}""")
                    .linkFromJellyfinMovie(42, 7)
            assertEquals(7, valid?.id)
            val wrong =
                backend("""{"id":7,"name":"Saga","parts":[{"id":99,"mediaType":"movie","title":"Other"}]}""")
                    .linkFromJellyfinMovie(42, 7)
            assertEquals(null, wrong)
        }

    @Test fun jellyfinFallbackUsesMovieTmdbIdentityWithoutConsultingAdminIndex() =
        runTest {
            val paths = mutableListOf<String>()
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        paths += chain.request().url.encodedPath
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("fixture")
                            .body("""{"id":42,"collection":{"id":7,"name":"Saga"}}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val backend = SeerrCollectionsBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
            assertEquals(7, backend.linkFromJellyfinMovie(42, null)?.id)
            assertEquals(listOf("/api/v1/movie/42"), paths)
        }

    private val current =
        CurrentSeerr(
            SeerrServer(1, "http://seerr.invalid"),
            SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "admin", null, null),
            User(id = 7),
            PublicSettings(),
        )

    private fun backend(body: String): SeerrCollectionsBackend {
        val http =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("fixture")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }.build()
        return SeerrCollectionsBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
    }

    @Test fun detailKeepsFullMembershipButDoesNotInferCompletionFromIt() =
        runTest {
            val detail =
                backend(
                    """{
                      "id":7,"name":"Saga","overview":"Overview","posterPath":null,"backdropPath":null,
                      "availableMovies":1,"totalMovies":2,"parts":[
                        {"id":3,"mediaType":"movie","title":"Upcoming","releaseDate":"2030-01-01"},
                        {"id":2,"mediaType":"movie","title":"Released","releaseDate":"2020-01-01","mediaInfo":{"status":5}},
                        {"id":3,"mediaType":"movie","title":"Duplicate"},
                        {"id":4,"mediaType":"movie","title":null,"releaseDate":null}
                      ]
                    }""",
                ).detail(7)
            assertEquals(7, detail.id)
            assertEquals(listOf(2, 3, 4), detail.movies.map { it.id })
            assertEquals(SeerrAvailability.AVAILABLE, detail.movies[0].availability)
            assertEquals(SeerrAvailability.UNKNOWN, detail.movies[1].availability)
            assertEquals("Movie 4", detail.movies[2].title)
            assertEquals(null, detail.posterUrl)
            assertEquals(null, detail.movies[2].releaseDate)
            assertEquals(1, detail.availableMovies)
            assertEquals(2, detail.totalMovies)
        }

    @Test fun bondAndHousemaidDetailsUseAuthoritativeCountsWithoutAdminIndex() =
        runTest {
            val paths = mutableListOf<String>()
            val bondParts =
                (1..26).joinToString(",") { id ->
                    """{"id":$id,"mediaType":"movie","title":"Bond $id","releaseDate":"2000-01-01"}"""
                }
            val bodies =
                mapOf(
                    "/api/v1/collection/645" to
                        """{"id":645,"name":"James Bond","availableMovies":10,"totalMovies":26,"parts":[$bondParts,{"id":27,"mediaType":"movie","title":"Planned","releaseDate":""}]}""",
                    "/api/v1/collection/1611146" to
                        """{"id":1611146,"name":"The Housemaid","availableMovies":1,"totalMovies":1,"parts":[{"id":1,"mediaType":"movie","releaseDate":"2020-01-01"},{"id":2,"mediaType":"movie","releaseDate":"2027-01-01"}]}""",
                )
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val path = chain.request().url.encodedPath
                        paths += path
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("fixture")
                            .body(requireNotNull(bodies[path]).toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val backend = SeerrCollectionsBackend(SeerrApiClient("http://seerr.invalid/api/v1", null, http), current)
            val bond = backend.detail(645)
            val housemaid = backend.detail(1611146)
            assertEquals(10, bond.availableMovies)
            assertEquals(26, bond.totalMovies)
            assertEquals(27, bond.movies.size)
            assertEquals("Planned", bond.movies.last().title)
            assertFalse(collectionMemberActionable(bond.movies.last(), LocalDate.of(2026, 11, 20)))
            assertEquals(1, housemaid.availableMovies)
            assertEquals(1, housemaid.totalMovies)
            assertEquals(2, housemaid.movies.size)
            assertFalse(collectionMemberActionable(housemaid.movies.last(), LocalDate.of(2026, 11, 20)))
            assertEquals(listOf("/api/v1/collection/645", "/api/v1/collection/1611146"), paths)
        }

    private fun emptyPage() = CollectionsPageData(0, 20, 0, LibraryCollectionsResponse.Status.COMPLETE, false, 0, emptyList())

    private fun fake(details: suspend (Int) -> CollectionDetailsData) =
        object : CollectionsBackend {
            override suspend fun page(
                skip: Int,
                filter: CollectionsFilter,
                sort: CollectionsSort,
            ) = emptyPage()

            override suspend fun detail(id: Int) = details(id)
        }

    private fun session(
        owner: String,
        backend: CollectionsBackend,
    ) = CollectionsSession(
        owner,
        SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()),
        backend,
        { true },
    )

    @Test fun sessionSwitchDiscardsLateDetailsAndDoesNotReuseOldDestination() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val oldResult = CompletableDeferred<CollectionDetailsData>()
                val oldBackend = fake { oldResult.await() }
                val newBackend = fake { CollectionDetailsData(it, "New owner", null, null, null, emptyList()) }
                val sessions = MutableStateFlow<CollectionsSession?>(session("old", oldBackend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true), mockk(relaxed = true))
                vm.show(7, "old")
                runCurrent()
                sessions.value = session("new", newBackend)
                runCurrent()
                oldResult.complete(CollectionDetailsData(7, "Old owner", null, null, null, emptyList()))
                runCurrent()
                assertEquals("new", vm.state.value.owner)
                assertEquals(null, vm.state.value.details)
                assertFalse(vm.state.value.loading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun failedLoadExposesRetryAndSuccessClearsError() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var hideScreen: () -> Unit = {}
            try {
                var fail = true
                val backend =
                    fake {
                        if (fail) throw IOException("offline")
                        CollectionDetailsData(it, "Recovered", null, null, null, emptyList())
                    }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true), mockk(relaxed = true))
                hideScreen = { vm.onScreenHidden() }
                vm.show(7, "one")
                runCurrent()
                assertTrue(vm.state.value.error)
                fail = false
                vm.retry()
                runCurrent()
                assertEquals(
                    "Recovered",
                    vm.state.value.details
                        ?.name,
                )
                assertFalse(vm.state.value.error)
            } finally {
                hideScreen()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test fun movieNavigationRetainsTypedFocusAndDoesNotInferJellyfinPlayback() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var hideScreen: () -> Unit = {}
            try {
                val movies =
                    (1..3).map { id ->
                        DiscoverItem(
                            id,
                            SeerrItemType.MOVIE,
                            "Movie $id",
                            null,
                            null,
                            SeerrAvailability.AVAILABLE,
                            if (id == 3) LocalDate.now().plusYears(1) else LocalDate.of(2020, 1, 1),
                            null,
                            null,
                            null,
                            null,
                        )
                    }
                val backend = fake { CollectionDetailsData(it, "Saga", null, null, null, movies) }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val navigation = mockk<NavigationManager>(relaxed = true)
                val vm = SeerrCollectionDetailsViewModel(source, navigation, mockk(relaxed = true))
                hideScreen = { vm.onScreenHidden() }
                vm.show(7, "one")
                runCurrent()
                vm.position(1)
                vm.open(movies[1])
                vm.open(movies[2])
                assertEquals("tmdb:movie:2", vm.state.value.focusedKey)
                assertEquals(1, vm.state.value.focusedIndex)
                assertEquals(null, movies[1].jellyfinItemId)
                verify(exactly = 1) { navigation.navigateToSeerrItem(movies[1], null) }
                verify(exactly = 0) { navigation.navigateToSeerrItem(movies[2], null) }
                verify(exactly = 0) { navigation.navigateTo(any()) }
            } finally {
                hideScreen()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }

    @Test fun unavailableSimilarEndpointSettlesWithoutInterruptingDetails() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var hideScreen: () -> Unit = {}
            try {
                val backend = fake { CollectionDetailsData(it, "Saga", null, null, null, emptyList()) }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true), mockk(relaxed = true))
                hideScreen = { vm.onScreenHidden() }
                vm.show(7, "one")
                runCurrent()
                assertEquals(
                    "Saga",
                    vm.state.value.details
                        ?.name,
                )
                assertTrue(vm.state.value.similarLoading)

                advanceUntilIdle()
                assertFalse(vm.state.value.similarLoading)
                assertTrue(vm.state.value.similarReadError)
                assertTrue(vm.state.value.similarTriggerError)
                assertEquals(
                    "Saga",
                    vm.state.value.details
                        ?.name,
                )
            } finally {
                hideScreen()
                advanceUntilIdle()
                Dispatchers.resetMain()
            }
        }
}
