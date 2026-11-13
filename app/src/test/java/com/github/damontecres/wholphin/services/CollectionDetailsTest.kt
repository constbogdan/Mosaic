package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionsResponse
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailsTest {
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
                      "parts":[
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
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true))
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
                val vm = SeerrCollectionDetailsViewModel(source, mockk(relaxed = true))
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
                Dispatchers.resetMain()
            }
        }

    @Test fun movieNavigationRetainsTypedFocusAndDoesNotInferJellyfinPlayback() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val movies =
                    (1..2).map { id ->
                        DiscoverItem(
                            id,
                            SeerrItemType.MOVIE,
                            "Movie $id",
                            null,
                            null,
                            SeerrAvailability.AVAILABLE,
                            null,
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
                val vm = SeerrCollectionDetailsViewModel(source, navigation)
                vm.show(7, "one")
                runCurrent()
                vm.position(1)
                vm.open(movies[1])
                assertEquals("tmdb:movie:2", vm.state.value.focusedKey)
                assertEquals(1, vm.state.value.focusedIndex)
                assertEquals(null, movies[1].jellyfinItemId)
                verify(exactly = 1) { navigation.navigateTo(movies[1].destination) }
            } finally {
                Dispatchers.resetMain()
            }
        }
}
