package com.github.damontecres.wholphin.services

import android.app.Application
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class SeerrOwnedNavigationTest {
    private val item =
        DiscoverItem(42, SeerrItemType.MOVIE, null, null, null, SeerrAvailability.AVAILABLE, null, null, null, null, UUID.randomUUID())

    @Test fun `repeated selection dispatches once and does not duplicate destination`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            try {
                val owner = mockk<CurrentUser>()
                val sessions = MutableStateFlow<CurrentUser?>(owner)
                val servers = mockk<ServerRepository>()
                every { servers.current } returns sessions
                val resolver = mockk<SeerrOwnedNavigationResolver>()
                val gate = CompletableDeferred<Destination?>()
                coEvery { resolver.resolve(item, owner) } coAnswers { gate.await() }
                val navigation = NavigationManager(resolver, servers)
                navigation.navigateToSeerrItem(item)
                navigation.navigateToSeerrItem(item)
                runCurrent()
                coVerify(exactly = 1) { resolver.resolve(item, owner) }
                gate.complete(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))
                runCurrent()
                assertEquals(2, navigation.backStack.size)
                navigation.navigateToSeerrItem(item)
                runCurrent()
                assertEquals(2, navigation.backStack.size)
                coVerify(exactly = 1) { resolver.resolve(item, owner) }
            } finally {
                WholphinDispatchers.IO = previousIo
                Dispatchers.resetMain()
            }
        }

    @Test fun `session change during resolution cannot navigate`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            try {
                val first = mockk<CurrentUser>()
                val sessions = MutableStateFlow<CurrentUser?>(first)
                val servers = mockk<ServerRepository>()
                every { servers.current } returns sessions
                val resolver = mockk<SeerrOwnedNavigationResolver>()
                val gate = CompletableDeferred<Destination?>()
                coEvery { resolver.resolve(item, first) } coAnswers { gate.await() }
                val navigation = NavigationManager(resolver, servers)
                navigation.navigateToSeerrItem(item)
                runCurrent()
                sessions.value = mockk()
                gate.complete(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))
                runCurrent()
                assertEquals(1, navigation.backStack.size)
            } finally {
                WholphinDispatchers.IO = previousIo
                Dispatchers.resetMain()
            }
        }

    @Test fun `random selection uses the same owned and Discover destinations for movies and series`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            try {
                val owner = mockk<CurrentUser>()
                val servers = mockk<ServerRepository>()
                every { servers.current } returns MutableStateFlow<CurrentUser?>(owner)
                val resolver = mockk<SeerrOwnedNavigationResolver>()
                val navigation = NavigationManager(resolver, servers)
                navigation.navigateTo(Destination.Watchlist)
                val movie = item.copy(id = 101, availability = SeerrAvailability.UNKNOWN)
                val series = item.copy(id = 102, type = SeerrItemType.TV, availability = SeerrAvailability.UNKNOWN)
                val unownedMovie = movie.copy(id = 103)
                val unownedSeries = series.copy(id = 104)
                val destinations =
                    listOf(
                        movie to Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE),
                        series to Destination.MediaItem(UUID.randomUUID(), BaseItemKind.SERIES),
                        unownedMovie to Destination.DiscoveredItem(unownedMovie),
                        unownedSeries to Destination.DiscoveredItem(unownedSeries),
                    )
                for ((selected, expected) in destinations) {
                    coEvery { resolver.resolve(selected, owner) } returns expected
                    var finished = false
                    navigation.navigateToSeerrItem(selected, onFinished = { finished = true })
                    runCurrent()
                    assertEquals(expected, navigation.backStack.last())
                    assertEquals(3, navigation.backStack.size)
                    assertEquals(true, finished)
                    navigation.goBack()
                }
            } finally {
                WholphinDispatchers.IO = previousIo
                Dispatchers.resetMain()
            }
        }

    @Test fun `changed Watchlist context rejects a pending owned resolution`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            try {
                val owner = mockk<CurrentUser>()
                val servers = mockk<ServerRepository>()
                every { servers.current } returns MutableStateFlow<CurrentUser?>(owner)
                val resolver = mockk<SeerrOwnedNavigationResolver>()
                val gate = CompletableDeferred<Destination?>()
                coEvery { resolver.resolve(item, owner) } coAnswers { gate.await() }
                val navigation = NavigationManager(resolver, servers)
                navigation.navigateTo(Destination.Watchlist)
                var current = true
                var navigated = false
                var finished = false
                navigation.navigateToSeerrItem(
                    item,
                    canNavigate = { current },
                    onNavigated = { navigated = true },
                    onFinished = { finished = true },
                )
                runCurrent()
                current = false
                gate.complete(Destination.MediaItem(UUID.randomUUID(), BaseItemKind.MOVIE))
                runCurrent()
                assertEquals(Destination.Watchlist, navigation.backStack.last())
                assertFalse(navigated)
                assertEquals(true, finished)
            } finally {
                WholphinDispatchers.IO = previousIo
                Dispatchers.resetMain()
            }
        }

    @Test fun `Go To unresolved callback does not push another Discover screen`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val previousIo = WholphinDispatchers.IO
            Dispatchers.setMain(dispatcher)
            WholphinDispatchers.IO = dispatcher
            try {
                val owner = mockk<CurrentUser>()
                val servers = mockk<ServerRepository>()
                every { servers.current } returns MutableStateFlow<CurrentUser?>(owner)
                val resolver = mockk<SeerrOwnedNavigationResolver>()
                coEvery { resolver.resolve(item, owner) } returns Destination.DiscoveredItem(item)
                val navigation = NavigationManager(resolver, servers)
                var unavailable = 0
                navigation.navigateToSeerrItem(item, onUnresolved = { unavailable++ })
                runCurrent()
                assertEquals(1, unavailable)
                assertEquals(1, navigation.backStack.size)
            } finally {
                WholphinDispatchers.IO = previousIo
                Dispatchers.resetMain()
            }
        }
}
