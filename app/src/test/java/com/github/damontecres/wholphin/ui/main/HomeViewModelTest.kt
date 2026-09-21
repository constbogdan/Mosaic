package com.github.damontecres.wholphin.ui.main

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.UserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

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
                MutableStateFlow<UserDto?>(
                    UserDto(
                        id = userId,
                        name = "test-user",
                        serverName = "test-server",
                        hasPassword = true,
                        hasConfiguredPassword = true,
                        hasConfiguredEasyPassword = false,
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
}
