package com.github.damontecres.wholphin.ui.collections

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.services.CollectionMovieLink
import com.github.damontecres.wholphin.services.CollectionsBackend
import com.github.damontecres.wholphin.services.CollectionsFilter
import com.github.damontecres.wholphin.services.CollectionsPageData
import com.github.damontecres.wholphin.services.CollectionsSession
import com.github.damontecres.wholphin.services.CollectionsSessionSource
import com.github.damontecres.wholphin.services.CollectionsSort
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionMovieLinkViewModelTest {
    private val link = CollectionMovieLink(7, "Saga", null)

    private fun backend(local: suspend (Int, Int?) -> CollectionMovieLink? = { _, _ -> link }) =
        object : CollectionsBackend {
            override suspend fun page(
                skip: Int,
                filter: CollectionsFilter,
                sort: CollectionsSort,
            ): CollectionsPageData = error("admin index must not be requested")

            override suspend fun detail(id: Int): CollectionDetailsData = error("unexpected detail lookup")

            override fun linkFromMovie(movie: MovieDetails) = link

            override suspend fun linkFromJellyfinMovie(
                movieId: Int,
                claimedCollectionId: Int?,
            ) = local(movieId, claimedCollectionId)
        }

    private fun session(
        owner: String,
        backend: CollectionsBackend,
        current: () -> Boolean = { true },
    ) = CollectionsSession(owner, SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()), backend, current)

    private fun discoveredOrigin(id: Int) =
        Destination.DiscoveredItem(
            DiscoverItem(id, SeerrItemType.MOVIE, "Movie", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
        )

    private fun localMovie(id: UUID = UUID.randomUUID()) =
        BaseItem(BaseItemDto(id = id, type = BaseItemKind.MOVIE, providerIds = mapOf("Tmdb" to "42")))

    @Test fun discoveredMovieUsesExistingDetailsAndNavigatesOnlyOnce() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val origin = discoveredOrigin(42)
                val stack = mutableListOf<Destination>(Destination.Home(), origin)
                val navigation = mockk<NavigationManager>(relaxed = true)
                every { navigation.backStack } returns stack
                every { navigation.navigateTo(any()) } answers {
                    stack.add(firstArg())
                    Unit
                }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend()))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = CollectionMovieLinkViewModel(source, navigation)
                vm.showDiscover(MovieDetails(id = 42), origin)
                runCurrent()
                assertEquals(link, vm.state.value.link)
                vm.open(link)
                vm.open(link)
                assertEquals(3, stack.size)
                assertEquals(Destination.SeerrCollection(7, null, null, "one"), stack.last())
                stack.removeLast()
                assertEquals(origin, stack.last())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun wrongMovieIdentityDoesNotExposeCollection() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend()))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = CollectionMovieLinkViewModel(source, mockk(relaxed = true))
                vm.showDiscover(MovieDetails(id = 99), discoveredOrigin(42))
                runCurrent()
                assertNull(vm.state.value.link)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun jellyfinMovieCollectionBackRetainsOriginAndLink() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val movie = localMovie()
                val origin = Destination.MediaItem(movie)
                val stack = mutableListOf<Destination>(Destination.Home(), origin)
                val navigation = mockk<NavigationManager>(relaxed = true)
                every { navigation.backStack } returns stack
                every { navigation.navigateTo(any()) } answers {
                    stack.add(firstArg())
                    Unit
                }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend()))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = CollectionMovieLinkViewModel(source, navigation)
                vm.showLocal(movie, origin)
                runCurrent()
                vm.open(link)
                assertEquals(Destination.SeerrCollection(7, null, null, "one"), stack.last())
                stack.removeLast()
                assertEquals(origin, stack.last())
                assertEquals(link, vm.state.value.link)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun localLateResultCannotCrossSession() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val pending = CompletableDeferred<CollectionMovieLink?>()
                var calls = 0
                val first =
                    backend { _, _ ->
                        calls++
                        pending.await()
                    }
                val second =
                    backend { _, _ ->
                        calls++
                        link
                    }
                var firstCurrent = true
                val sessions = MutableStateFlow<CollectionsSession?>(session("old", first) { firstCurrent })
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = CollectionMovieLinkViewModel(source, mockk(relaxed = true))
                val movie = localMovie()
                vm.showLocal(movie, Destination.MediaItem(movie))
                runCurrent()
                assertTrue(vm.state.value.loading)
                firstCurrent = false
                sessions.value = session("new", second)
                runCurrent()
                pending.complete(CollectionMovieLink(8, "Stale", null))
                runCurrent()
                assertEquals("new", vm.state.value.owner)
                assertEquals(link, vm.state.value.link)
                assertEquals(2, calls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun localFailureOffersRetryWithoutStaleLink() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var calls = 0
                val backend = backend { _, _ -> if (++calls == 1) error("denied") else link }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", backend))
                val source = mockk<CollectionsSessionSource>()
                every { source.sessions } returns sessions
                val vm = CollectionMovieLinkViewModel(source, mockk(relaxed = true))
                val movie = localMovie()
                vm.showLocal(movie, Destination.MediaItem(movie))
                runCurrent()
                assertTrue(vm.state.value.error)
                assertTrue(vm.state.value.retryable)
                assertNull(vm.state.value.link)
                vm.retry()
                vm.retry()
                runCurrent()
                assertEquals(link, vm.state.value.link)
                assertFalse(vm.state.value.error)
                assertEquals(2, calls)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
