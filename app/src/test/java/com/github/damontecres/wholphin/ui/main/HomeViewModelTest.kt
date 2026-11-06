package com.github.damontecres.wholphin.ui.main

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        WholphinDispatchers.configure(testDispatcher)
    }

    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    @Test
    fun `reactive user settings reset preserves acquisition items`() =
        runTest(testDispatcher) {
            val userId = UUID.randomUUID()
            val currentUser =
                MutableStateFlow<ServerUserConfig?>(
                    ServerUserConfig(
                        id = userId,
                        name = "test-user",
                    ),
                )
            val currentSettings = MutableStateFlow(HomePageResolvedSettings.EMPTY)
            val acquiringItem = mockk<HomeAcquiringItem>()
            val acquisitionState = MutableStateFlow(HomeAcquiringState(listOf(acquiringItem)))

            val serverRepository =
                mockk<ServerRepository>(relaxed = true) {
                    every { currentUserDtoFlow } returns currentUser
                }
            val homeSettingsService =
                mockk<HomeSettingsService>(relaxed = true) {
                    every { this@mockk.currentSettings } returns currentSettings
                }
            val navDrawerService =
                mockk<NavDrawerService>(relaxed = true) {
                    coEvery { getAllUserLibraries(any(), any()) } returns emptyList()
                }
            val appPreferences = mockk<AppPreferences>(relaxed = true)
            val userPreferencesService =
                mockk<UserPreferencesService>(relaxed = true) {
                    coEvery { getCurrent() } returns UserPreferences(appPreferences, null)
                }
            val acquiringSource =
                mockk<HomeAcquiringStateProvider> {
                    every { state } returns acquisitionState
                }
            val viewModel =
                HomeViewModel(
                    context = mockk<Context>(relaxed = true),
                    navigationManager = mockk<NavigationManager>(relaxed = true),
                    serverRepository = serverRepository,
                    serverReportService = mockk<ServerReportService>(relaxed = true),
                    navDrawerService = navDrawerService,
                    homeSettingsService = homeSettingsService,
                    favoriteWatchManager = mockk<FavoriteWatchManager>(relaxed = true),
                    datePlayedService = mockk<DatePlayedService>(relaxed = true),
                    backdropService = mockk<BackdropService>(relaxed = true),
                    userPreferencesService = userPreferencesService,
                    mediaManagementService = mockk<MediaManagementService>(relaxed = true),
                    latestNextUpService = mockk<LatestNextUpService>(relaxed = true),
                    homeAcquiringSource = acquiringSource,
                )

            advanceUntilIdle()
            assertEquals(listOf(acquiringItem), viewModel.state.value.acquiringItems)

            val reloadedSettings = HomePageResolvedSettings(userId, emptyList())
            currentSettings.value = reloadedSettings
            advanceUntilIdle()

            assertEquals(reloadedSettings, viewModel.state.value.settings)
            assertEquals(LoadingState.Success, viewModel.state.value.loadingState)
            assertEquals(listOf(acquiringItem), viewModel.state.value.acquiringItems)
        }

    @Test
    fun `timestamp only user updates do not reload home`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            val dto =
                UserDto(
                    id = fixture.user.id,
                    serverId = fixture.user.serverId.toString(),
                    hasPassword = true,
                    hasConfiguredPassword = true,
                    hasConfiguredEasyPassword = false,
                )
            fixture.currentUser.value = ServerUserConfig(dto)
            advanceUntilIdle()
            val before = fixture.viewModel.state.value

            fixture.currentUser.value = ServerUserConfig(dto.copy(lastActivityDate = LocalDateTime.of(2026, 11, 6, 1, 0)))
            advanceUntilIdle()

            assertSame(before, fixture.viewModel.state.value)
            coVerify(exactly = 1) { fixture.navDrawer.getAllUserLibraries(any(), any()) }
        }

    @Test
    fun `same session config refresh retains loaded rows and acquiring until replacement`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            advanceUntilIdle()
            val rows = fixture.viewModel.state.value.homeRows
            val replacement = CompletableDeferred<HomeRowLoadingState>()
            coEvery { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                replacement.await()
            }

            fixture.currentUser.value =
                fixture.user.copy(
                    policy = mockk<UserPolicy> { every { enableLiveTvAccess } returns true },
                )
            advanceUntilIdle()
            assertSame(rows, fixture.viewModel.state.value.homeRows)
            assertEquals(LoadingState.Success, fixture.viewModel.state.value.loadingState)
            assertEquals(LoadingState.Loading, fixture.viewModel.state.value.refreshState)
            assertEquals(listOf(fixture.acquiringItem), fixture.viewModel.state.value.acquiringItems)
            coVerify { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), true, any()) }

            val newRow = HomeRowLoadingState.Success(EmptyStringProvider, listOf(mockk()))
            replacement.complete(newRow)
            advanceUntilIdle()
            assertEquals(listOf(newRow), fixture.viewModel.state.value.homeRows)
            assertEquals(LoadingState.Success, fixture.viewModel.state.value.refreshState)
        }

    @Test
    fun `changed row settings rebuild rows without dropping acquiring`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            advanceUntilIdle()
            val replacement = CompletableDeferred<HomeRowLoadingState>()
            coEvery { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                replacement.await()
            }
            fixture.settings.value = fixture.settings.value.copy(rows = listOf(fixture.row.copy(id = 2)))
            advanceUntilIdle()
            assertTrue(
                fixture.viewModel.state.value.homeRows
                    .single() is HomeRowLoadingState.Pending,
            )
            assertEquals(listOf(fixture.acquiringItem), fixture.viewModel.state.value.acquiringItems)
            replacement.complete(fixture.loadedRow)
            advanceUntilIdle()
        }

    @Test
    fun `invalid user or settings clears home rows while acquisition provider remains authoritative`() =
        runTest(testDispatcher) {
            for (invalid in listOf("logout", "empty settings", "wrong user")) {
                val fixture = Fixture()
                advanceUntilIdle()
                when (invalid) {
                    "logout" -> fixture.currentUser.value = null
                    "empty settings" -> fixture.settings.value = HomePageResolvedSettings.EMPTY
                    else -> fixture.currentUser.value = fixture.user.copy(id = UUID.randomUUID())
                }
                advanceUntilIdle()
                assertEquals(HomePageResolvedSettings.EMPTY, fixture.viewModel.state.value.settings)
                assertEquals(LoadingState.Pending, fixture.viewModel.state.value.loadingState)
                assertTrue(
                    fixture.viewModel.state.value.homeRows
                        .isEmpty(),
                )
                assertEquals(listOf(fixture.acquiringItem), fixture.viewModel.state.value.acquiringItems)
                fixture.acquisition.value = HomeAcquiringState()
                advanceUntilIdle()
                assertTrue(
                    fixture.viewModel.state.value.acquiringItems
                        .isEmpty(),
                )
                coVerify(exactly = 1) { fixture.navDrawer.getAllUserLibraries(any(), any()) }
            }
        }

    @Test
    fun `same user id on a different server does not reuse previous server rows`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            advanceUntilIdle()
            val replacement = CompletableDeferred<HomeRowLoadingState>()
            coEvery { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                replacement.await()
            }
            fixture.currentUser.value = fixture.user.copy(serverId = UUID.randomUUID())
            advanceUntilIdle()
            assertEquals(LoadingState.Loading, fixture.viewModel.state.value.loadingState)
            assertTrue(
                fixture.viewModel.state.value.homeRows
                    .single() is HomeRowLoadingState.Pending,
            )
            coVerify(exactly = 0) { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), true, any()) }
            replacement.complete(fixture.loadedRow)
            advanceUntilIdle()
        }

    @Test
    fun `cancelled previous user refresh cannot overwrite a new users home`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            advanceUntilIdle()
            val stale = CompletableDeferred<HomeRowLoadingState>()
            coEvery { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                stale.await()
            }
            fixture.currentUser.value = fixture.user.copy(name = "updated name")
            advanceUntilIdle()
            val newUser = fixture.user.copy(id = UUID.randomUUID())
            fixture.currentUser.value = newUser
            advanceUntilIdle()
            assertTrue(
                fixture.viewModel.state.value.homeRows
                    .isEmpty(),
            )

            val newRow = HomeRowLoadingState.Success(EmptyStringProvider, listOf(mockk()))
            coEvery { fixture.homeSettings.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } returns newRow
            fixture.settings.value = fixture.settings.value.copy(userId = newUser.id)
            advanceUntilIdle()
            stale.complete(fixture.loadedRow)
            advanceUntilIdle()
            assertEquals(newUser.id, fixture.viewModel.state.value.settings.userId)
            assertEquals(listOf(newRow), fixture.viewModel.state.value.homeRows)
        }

    @Test
    fun `manual refresh init retains loaded rows and acquisition`() =
        runTest(testDispatcher) {
            val fixture = Fixture()
            advanceUntilIdle()
            fixture.viewModel.init()
            advanceUntilIdle()
            assertEquals(listOf(fixture.loadedRow), fixture.viewModel.state.value.homeRows)
            assertEquals(listOf(fixture.acquiringItem), fixture.viewModel.state.value.acquiringItems)
            coVerify(exactly = 2) { fixture.navDrawer.getAllUserLibraries(any(), any()) }
        }

    private class Fixture {
        val user = ServerUserConfig(UUID.randomUUID(), "test-user", UUID.randomUUID())
        val currentUser = MutableStateFlow<ServerUserConfig?>(user)
        val row = HomeRowConfigDisplay(1, EmptyStringProvider, HomeRowConfig.ContinueWatching())
        val settings = MutableStateFlow(HomePageResolvedSettings(user.id, listOf(row)))
        val acquiringItem = mockk<HomeAcquiringItem>()
        val acquisition = MutableStateFlow(HomeAcquiringState(listOf(acquiringItem)))
        val loadedRow = HomeRowLoadingState.Success(EmptyStringProvider, listOf(mockk()))
        val navDrawer =
            mockk<NavDrawerService>(relaxed = true) {
                coEvery { getAllUserLibraries(any(), any()) } returns emptyList()
            }
        val homeSettings =
            mockk<HomeSettingsService>(relaxed = true) {
                every { currentSettings } returns settings
                coEvery { fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any()) } returns loadedRow
            }
        val viewModel =
            HomeViewModel(
                context = mockk<Context>(relaxed = true),
                navigationManager = mockk<NavigationManager>(relaxed = true),
                serverRepository =
                    mockk<ServerRepository>(relaxed = true) {
                        every { currentUserDtoFlow } returns this@Fixture.currentUser
                        every { currentUserDto } answers { this@Fixture.currentUser.value }
                    },
                serverReportService = mockk<ServerReportService>(relaxed = true),
                navDrawerService = navDrawer,
                homeSettingsService = homeSettings,
                favoriteWatchManager = mockk<FavoriteWatchManager>(relaxed = true),
                datePlayedService = mockk<DatePlayedService>(relaxed = true),
                backdropService = mockk<BackdropService>(relaxed = true),
                userPreferencesService =
                    mockk<UserPreferencesService>(relaxed = true) {
                        coEvery { getCurrent() } returns UserPreferences(mockk<AppPreferences>(relaxed = true), null)
                    },
                mediaManagementService = mockk<MediaManagementService>(relaxed = true),
                latestNextUpService = mockk<LatestNextUpService>(relaxed = true),
                homeAcquiringSource =
                    mockk<HomeAcquiringStateProvider> {
                        every { state } returns acquisition
                    },
            )
    }
}
