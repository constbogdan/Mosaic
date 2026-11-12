package com.github.damontecres.wholphin.ui

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistFilter
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.ScreensaverState
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.services.WatchlistRepository
import com.github.damontecres.wholphin.services.WatchlistSnapshot
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.cards.CardAcquisitionState
import com.github.damontecres.wholphin.ui.cards.CardMediaPresentation
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.MinButtonSize
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.discover.ExpandableDiscoverButtons
import com.github.damontecres.wholphin.ui.detail.discover.RequestMovieDialog
import com.github.damontecres.wholphin.ui.detail.discover.SeerrProfile
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.discover.QuickMovieConfirmGate
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchServerViewModel
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.watchlist.WatchlistActionButton
import com.github.damontecres.wholphin.ui.watchlist.WatchlistActionViewModel
import com.github.damontecres.wholphin.ui.watchlist.WatchlistPage
import com.github.damontecres.wholphin.ui.watchlist.WatchlistScreenState
import com.github.damontecres.wholphin.ui.watchlist.WatchlistViewModel
import com.github.damontecres.wholphin.ui.watchlist.watchlistBookmarkIcon
import com.github.damontecres.wholphin.ui.watchlist.watchlistFilterIconColor
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.operations.QuickConnectApi
import org.jellyfin.sdk.discovery.DiscoveryService
import org.jellyfin.sdk.discovery.RecommendedServerInfo
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class BasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    // From https://github.com/robolectric/robolectric/pull/4736#issuecomment-1831034882
    @get:Rule(order = 1)
    val addActivityToRobolectricRule =
        object : TestWatcher() {
            override fun starting(description: Description?) {
                super.starting(description)
                val appContext: Application = ApplicationProvider.getApplicationContext()
                val activityInfo =
                    ActivityInfo().apply {
                        name = TestActivity::class.java.name
                        packageName = appContext.packageName
                    }
                shadowOf(appContext.packageManager).addOrUpdateActivity(activityInfo)
            }
        }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Inject
    lateinit var jellyfin: Jellyfin

    @Inject
    lateinit var api: ApiClient

    @Inject
    lateinit var setupNavigationManager: SetupNavigationManager

    lateinit var screensaverService: ScreensaverService

    lateinit var switchServerViewModel: SwitchServerViewModel

    val discovery: DiscoveryService = mockk()

    @Test
    fun seerr_movie_card_short_ok_and_long_ok_keep_separate_actions() {
        val showing = mutableStateOf(false)
        var details = 0
        val item = DiscoverItem(42, SeerrItemType.MOVIE, "Fixture", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        composeTestRule.setContent {
            WholphinTheme {
                DiscoverItemCard(item, onClick = { details++ }, onLongClick = { showing.value = true }, useFallbackText = true)
                if (showing.value) BasicDialog(onDismissRequest = { showing.value = false }) { Text("Quick request") }
            }
        }
        val card = composeTestRule.onNode(hasClickAction())
        card.requestFocus().performClickEnter()
        Assert.assertEquals(1, details)
        Assert.assertFalse(showing.value)
        card.performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(800)
            keyUp(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithText("Quick request").assertIsDisplayed()
        Assert.assertEquals(1, details)
        composeTestRule.runOnIdle { showing.value = false }
        card.assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun quick_movie_request_requires_explicit_enabled_confirmation() {
        val ready = mutableStateOf(false)
        var submissions = 0
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(serverId = 11, profiles = listOf(SeerrProfile(7, "1080p HDR", true))),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = { submissions++ },
                    onDismissRequest = {},
                    confirmationReady = ready.value,
                )
            }
        }
        val confirm = composeTestRule.onNodeWithContentDescription("1080p HDR")
        confirm.assertIsNotEnabled()
        Assert.assertEquals(0, submissions)
        composeTestRule.runOnIdle { ready.value = true }
        confirm.assertIsEnabled().requestFocus().performClickEnter()
        composeTestRule.waitForIdle()
        Assert.assertEquals(1, submissions)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun quick_movie_request_held_ok_cannot_submit_but_a_new_ok_can() {
        val gate = QuickMovieConfirmGate()
        val showing = mutableStateOf(false)
        var submissions = 0 // A mock callback: no Seerr client or live POST is involved.
        val item = DiscoverItem(42, SeerrItemType.MOVIE, "Fixture", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val guard =
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                gate.onKey(native.action, native.keyCode, native.repeatCount)
            }
        composeTestRule.setContent {
            WholphinTheme {
                Box(guard) {
                    DiscoverItemCard(item, onClick = {}, onLongClick = {
                        gate.reset()
                        showing.value = true
                    }, useFallbackText = true)
                }
                if (showing.value) {
                    RequestMovieDialog(
                        loading = LoadingState.Success,
                        data = SeerrRequestData(serverId = 11, profiles = listOf(SeerrProfile(7, "1080p HDR", true))),
                        requestEnabled = true,
                        movie =
                            com.github.damontecres.wholphin.api.seerr.model
                                .MovieDetails(id = 42, title = "Fixture"),
                        onSubmit = { submissions++ },
                        confirmAction = gate::consumeConfirmation,
                        onDismissRequest = {
                            showing.value = false
                            gate.clear()
                        },
                        confirmationReady = gate.released,
                        dialogModifier = guard,
                    )
                }
            }
        }
        val card = composeTestRule.onNode(hasClickAction())
        card.requestFocus().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(800)
            advanceEventTime(800) // Continue holding beyond the long-click threshold.
            keyUp(Key.DirectionCenter)
        }
        Assert.assertTrue("Long press did not open fixture dialog", showing.value)
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsDisplayed()
        Assert.assertEquals(0, submissions)
        val confirm = composeTestRule.onNodeWithContentDescription("1080p HDR")
        confirm.requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        Assert.assertEquals(1, submissions)
        Assert.assertTrue(showing.value)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_details_never_submits_until_a_quality_button_is_pressed() {
        var submissions = 0
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(serverId = 11, profiles = listOf(SeerrProfile(7, "1080p HDR", true))),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = { submissions++ },
                    onDismissRequest = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        Assert.assertEquals(0, submissions)
        composeTestRule.onNodeWithContentDescription("1080p HDR").requestFocus().performClickEnter()
        composeTestRule.waitForIdle()
        Assert.assertEquals(1, submissions)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_loading_keeps_dialog_size_and_focuses_default_quality_when_ready() {
        val loading = mutableStateOf<LoadingState>(LoadingState.Loading)
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = loading.value,
                    data =
                        SeerrRequestData(
                            serverId = 0,
                            profiles = listOf(SeerrProfile(12, "1080p HDR", true), SeerrProfile(8, "2160p HDR", false)),
                        ),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = {},
                    onDismissRequest = {},
                )
            }
        }
        val shell = composeTestRule.onNodeWithTag("movieRequestDialogShell")
        val before = shell.fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsNotEnabled()
        composeTestRule.runOnIdle { loading.value = LoadingState.Success }
        composeTestRule.waitForIdle()
        val after = shell.fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(before.width, after.width, 0.1f)
        Assert.assertEquals(before.height, after.height, 0.1f)
        composeTestRule
            .onNodeWithContentDescription("1080p HDR")
            .assertIsEnabled()
            .assertIsDisplayed()
            .assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_focuses_only_configured_fallback_quality() {
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(serverId = 0, profiles = listOf(SeerrProfile(8, "2160p HDR", true))),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = {},
                    onDismissRequest = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsNotEnabled()
        composeTestRule
            .onNodeWithContentDescription("2160p HDR")
            .assertIsEnabled()
            .assertIsDisplayed()
            .assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_back_dismisses_without_submission() {
        val showing = mutableStateOf(true)
        var submissions = 0
        composeTestRule.setContent {
            WholphinTheme {
                if (showing.value) {
                    RequestMovieDialog(
                        loading = LoadingState.Success,
                        data = SeerrRequestData(serverId = 0, profiles = listOf(SeerrProfile(12, "1080p HDR", true))),
                        requestEnabled = true,
                        movie =
                            com.github.damontecres.wholphin.api.seerr.model
                                .MovieDetails(id = 42, title = "Fixture"),
                        onSubmit = { submissions++ },
                        onDismissRequest = { showing.value = false },
                    )
                }
            }
        }
        composeTestRule.runOnUiThread {
            org.robolectric.shadows.ShadowDialog
                .getLatestDialog()
                .onBackPressed()
        }
        composeTestRule.waitForIdle()
        Assert.assertFalse(showing.value)
        Assert.assertEquals(0, submissions)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_synopsis_has_bounded_scroll_region_above_quality_actions() {
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(serverId = 0, profiles = listOf(SeerrProfile(12, "1080p HDR", true))),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model.MovieDetails(
                            id = 42,
                            title = "Fixture",
                            overview = List(60) { "An extended synopsis with readable detail." }.joinToString(" "),
                        ),
                    onSubmit = {},
                    onDismissRequest = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        val synopsis = composeTestRule.onNodeWithTag("movieRequestSynopsis").fetchSemanticsNode()
        synopsis.config[SemanticsProperties.VerticalScrollAxisRange]
        val quality = composeTestRule.onNodeWithContentDescription("1080p HDR").fetchSemanticsNode()
        Assert.assertTrue(
            "The synopsis must not crowd the bottom quality action",
            synopsis.boundsInRoot.bottom <= quality.boundsInRoot.top,
        )
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_synopsis_focus_keeps_scroll_position_and_movie_change_resets_it() {
        val overview = List(40) { "Synopsis line $it" }.joinToString("\n")
        val movie =
            mutableStateOf(
                com.github.damontecres.wholphin.api.seerr.model
                    .MovieDetails(id = 42, title = "First", overview = overview),
            )
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data =
                        SeerrRequestData(
                            serverId = 0,
                            profiles = listOf(SeerrProfile(12, "1080p HDR", true), SeerrProfile(8, "2160p HDR", false)),
                        ),
                    requestEnabled = true,
                    movie = movie.value,
                    onSubmit = {},
                    onDismissRequest = {},
                )
            }
        }
        val synopsis = composeTestRule.onNodeWithTag("movieRequestSynopsis")
        val range = synopsis.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        Assert.assertTrue("The multiline fixture must overflow", range.maxValue() > 0f)
        synopsis.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
        composeTestRule.waitForIdle()
        val scrolled = range.value()
        Assert.assertTrue("The synopsis did not scroll", scrolled > 0f)
        composeTestRule.onNodeWithContentDescription("2160p HDR").requestFocus()
        composeTestRule.waitForIdle()
        Assert.assertEquals("Quality focus must not restart synopsis animation", scrolled, range.value(), 0.1f)
        composeTestRule.runOnIdle {
            movie.value =
                com.github.damontecres.wholphin.api.seerr.model
                    .MovieDetails(id = 43, title = "Second", overview = overview)
        }
        composeTestRule.waitForIdle()
        Assert.assertEquals("A different movie must start at the top", 0f, range.value(), 0.1f)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        WholphinDispatchers.configure(TestModule.testDispatcher)

        hiltRule.inject()
        screensaverService = mockk(relaxed = true)
        every { screensaverService.state } returns
            MutableStateFlow(
                ScreensaverState(
                    false,
                    false,
                    false,
                    false,
                ),
            )

        every { jellyfin.createApi(any(), any(), any(), any(), any()) } returns api
        every { jellyfin.discovery } returns discovery
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        WholphinDispatchers.reset()
    }

    /**
     * Tests successfully entering and submitting a server URL
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun test_enter_server_url_success() {
        coEvery { discovery.getRecommendedServers("localhost") } returns
            listOf(
                RecommendedServerInfo(
                    address = "localhost",
                    responseTime = 50,
                    score = RecommendedServerInfoScore.GREAT,
                    issues = emptyList(),
                    systemInfo =
                        Result.success(
                            PublicSystemInfo(
                                id = UUID.randomUUID().toString(),
                                startupWizardCompleted = true,
                            ),
                        ),
                ),
            )
        val quickConnectApi = mockk<QuickConnectApi>()
        every { api.quickConnectApi } returns quickConnectApi
        coEvery { quickConnectApi.getQuickConnectEnabled() } returns successResponse(true)

        composeTestRule.setContent {
            WholphinTheme {
                switchServerViewModel = hiltViewModel()
                SwitchServerContent(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = switchServerViewModel,
                )
            }
        }

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Add Server").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO fix focus
        }
        composeTestRule
            .onNodeWithTag("add_server")
            .assertIsFocused()
            .performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter server address").performClickEnter()
        composeTestRule.onNodeWithText("Enter Server IP or URL").assertIsDisplayed()

        composeTestRule.onNodeWithTag("server_url_text").performTextInput("localhost")
        composeTestRule
            .onNodeWithText("Submit")
            .assertIsEnabled()
            .requestFocus()
            .performClickEnter()

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        switchServerViewModel.state.value.addServerState.let {
            if (it is LoadingState.Error) throw it.exception ?: Exception(it.message)
        }

//        coVerify(exactly = 1) { discovery.getRecommendedServers("localhost") }
        Assert.assertEquals(1, setupNavigationManager.backStack.size)
        Assert.assertTrue(setupNavigationManager.backStack.last() is SetupDestination.UserList)
    }

    /**
     * Tests entering and submitting a server URL that returns an error
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun test_enter_server_url_error() {
        coEvery { discovery.getRecommendedServers("localhost") } returns
            listOf(
                RecommendedServerInfo(
                    address = "localhost",
                    responseTime = 50,
                    score = RecommendedServerInfoScore.GREAT,
                    issues = emptyList(),
                    systemInfo =
                        Result.success(
                            PublicSystemInfo(
                                id = null, // Invalid
                                startupWizardCompleted = false,
                            ),
                        ),
                ),
            )
        val quickConnectApi = mockk<QuickConnectApi>()
        every { api.quickConnectApi } returns quickConnectApi
        coEvery { quickConnectApi.getQuickConnectEnabled() } returns successResponse(true)

        composeTestRule.setContent {
            WholphinTheme {
                switchServerViewModel = hiltViewModel()
                SwitchServerContent(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = switchServerViewModel,
                )
            }
        }

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        composeTestRule.onNodeWithText("Add Server").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO fix focus
        }
        composeTestRule
            .onNodeWithTag("add_server")
            .assertIsFocused()
            .performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter server address").performClickEnter()
        composeTestRule.onNodeWithText("Enter Server IP or URL").assertIsDisplayed()

        composeTestRule.onNodeWithTag("server_url_text").performTextInput("localhost")
        composeTestRule
            .onNodeWithText("Submit")
            .assertIsEnabled()
            .requestFocus()
            .performClickEnter()

        TestModule.testDispatcher.scheduler.advanceUntilIdle()

        Assert.assertTrue(switchServerViewModel.state.value.addServerState is LoadingState.Error)

        composeTestRule.onNodeWithText("Server returned invalid response").assertIsDisplayed()
    }

    @Test
    fun watchlist_cards_reuse_shared_movie_and_series_year_beneath_title() {
        composeTestRule.setContent {
            WholphinTheme {
                Row {
                    listOf(
                        SeerrItemType.MOVIE to LocalDate.of(1999, 3, 31),
                        SeerrItemType.TV to LocalDate.of(2024, 2, 29),
                        SeerrItemType.TV to null,
                    ).forEachIndexed { index, (type, date) ->
                        DiscoverItemCard(
                            item =
                                DiscoverItem(
                                    index + 1,
                                    type,
                                    "Saved $index",
                                    null,
                                    null,
                                    SeerrAvailability.UNKNOWN,
                                    date,
                                    null,
                                    null,
                                    null,
                                    null,
                                ),
                            onClick = {},
                            onLongClick = {},
                            width = 120.dp,
                            useFallbackText = false,
                            savedToWatchlist = true,
                        )
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("1999").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024").assertIsDisplayed()
        Assert.assertTrue(
            composeTestRule
                .onNodeWithText("1999")
                .fetchSemanticsNode()
                .boundsInRoot.top >=
                composeTestRule
                    .onNodeWithText("Saved 0")
                    .fetchSemanticsNode()
                    .boundsInRoot.bottom,
        )
        composeTestRule.onNodeWithText("Saved 2").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("2026").assertCountEquals(0)
    }

    @Test
    fun saved_badge_stays_inside_poster_and_scales_with_focus_without_an_extra_control() {
        composeTestRule.setContent {
            WholphinTheme {
                Row(Modifier.padding(16.dp)) {
                    DiscoverItemCard(
                        item =
                            DiscoverItem(
                                1,
                                SeerrItemType.MOVIE,
                                "Movie poster",
                                null,
                                null,
                                SeerrAvailability.AVAILABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                            ),
                        onClick = {},
                        onLongClick = {},
                        width = 80.dp,
                        useFallbackText = true,
                        savedToWatchlist = true,
                    )
                    DiscoverItemCard(
                        item =
                            DiscoverItem(
                                2,
                                SeerrItemType.TV,
                                "TV poster",
                                null,
                                null,
                                SeerrAvailability.PROCESSING,
                                null,
                                null,
                                null,
                                null,
                                null,
                            ),
                        onClick = {},
                        onLongClick = {},
                        width = 120.dp,
                        useFallbackText = true,
                        savedToWatchlist = true,
                        mediaPresentation =
                            CardMediaPresentation(
                                acquisitionProgress = .5f,
                                acquisitionState = CardAcquisitionState.ACQUIRING,
                            ),
                        preferAcquisitionStateOverlay = true,
                    )
                }
            }
        }
        val cards = composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
        val badges = composeTestRule.onAllNodesWithContentDescription("On my Watchlist", useUnmergedTree = true)
        cards.assertCountEquals(2)
        badges.assertCountEquals(2)
        cards[1].requestFocus().assertIsFocused()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        val unfocusedBadge = badges[0].fetchSemanticsNode().boundsInRoot
        cards[0].requestFocus().assertIsFocused()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
        val focusedBadge = badges[0].fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(focusedBadge.width > unfocusedBadge.width)
        Assert.assertEquals(
            focusedBadge.height / unfocusedBadge.height,
            focusedBadge.width / unfocusedBadge.width,
            .02f,
        )
        for (index in 0..1) {
            val cardNode = cards[index].fetchSemanticsNode()
            val layout = cardNode.boundsInRoot
            // Card's outer semantics are unscaled. Compare against its visual poster bounds.
            val scale =
                if (cardNode.config[SemanticsProperties.Focused] ==
                    true
                ) {
                    focusedBadge.width / unfocusedBadge.width
                } else {
                    1f
                }
            val card =
                Rect(
                    layout.center.x - layout.width * scale / 2,
                    layout.center.y - layout.height * scale / 2,
                    layout.center.x + layout.width * scale / 2,
                    layout.center.y + layout.height * scale / 2,
                )
            val badge = badges[index].fetchSemanticsNode()
            val bounds = badge.boundsInRoot
            Assert.assertTrue("Poster: $card; badge: $bounds", bounds.left >= card.left && bounds.right <= card.right)
            Assert.assertTrue("Poster: $card; badge: $bounds", bounds.top > card.center.y && card.bottom - bounds.bottom >= 6f * scale)
            Assert.assertTrue(SemanticsProperties.Focused !in badge.config)
            Assert.assertTrue(SemanticsActions.OnClick !in badge.config)
            // The badge must inherit the poster Card's transform, never sit beside the Card.
            var ancestor = badge.parent
            while (ancestor != null && SemanticsActions.OnClick !in ancestor.config) ancestor = ancestor.parent
            Assert.assertNotNull("Badge belongs inside its clickable poster Card", ancestor)
        }
        val acquisition = composeTestRule.onNodeWithText("Acquiring", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(acquisition.bottom < badges[1].fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun discover_cards_do_not_enable_watchlist_badges_by_default() {
        composeTestRule.setContent {
            WholphinTheme {
                DiscoverItemCard(
                    item =
                        DiscoverItem(
                            1,
                            SeerrItemType.MOVIE,
                            "Poster",
                            null,
                            null,
                            SeerrAvailability.UNKNOWN,
                            null,
                            null,
                            null,
                            null,
                            null,
                        ),
                    onClick = {},
                    onLongClick = {},
                )
            }
        }
        composeTestRule.onAllNodesWithContentDescription("On my Watchlist", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun paginated_grid_keeps_focus_and_server_total_when_rows_append() {
        val rows =
            mutableStateOf(
                (1..20).map { id ->
                    WatchlistEntry(
                        MediaKey.Catalog(CatalogMediaType.MOVIE, id),
                        "Saved $id",
                        Instant.EPOCH,
                        null,
                        null,
                        null,
                        DiscoverItem(
                            id,
                            SeerrItemType.MOVIE,
                            "Saved $id",
                            null,
                            null,
                            SeerrAvailability.UNKNOWN,
                            null,
                            null,
                            null,
                            null,
                            null,
                        ),
                    )
                },
            )
        var position = -1
        composeTestRule.setContent {
            val snapshot = rows.value
            WholphinTheme {
                CardGrid(
                    pager = snapshot,
                    totalItems = 989,
                    itemKey = { snapshot[it].gridId },
                    onClickItem = { _, _ -> },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = FocusRequester(),
                    showJumpButtons = false,
                    showLetterButtons = false,
                    positionCallback = { _, index -> position = index },
                    cardContent = { details ->
                        Button(onClick = details.onClick, modifier = details.mod) { Text("Row ${details.index + 1}") }
                    },
                )
            }
        }
        composeTestRule.onNodeWithText("Row 1").requestFocus().assertIsFocused()
        composeTestRule.onNodeWithText("Row 1").performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.onNodeWithText("Row 7").assertIsFocused()
        composeTestRule.onNodeWithText("7 / 989").assertIsDisplayed()
        composeTestRule.runOnIdle {
            Assert.assertEquals(6, position)
            rows.value = rows.value + rows.value.map { it.copy(key = it.key.copy(tmdbId = it.key.tmdbId + 20)) }
        }
        composeTestRule.onNodeWithText("Row 7").assertIsFocused()
        composeTestRule.onNodeWithText("7 / 989").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("7 / 40").assertCountEquals(0)
    }

    private fun assert_paged_grid_keeps_column_and_focus_after_prepend(columns: Int) {
        fun row(id: Int) =
            WatchlistEntry(
                MediaKey.Catalog(CatalogMediaType.MOVIE, id),
                "Row $id",
                Instant.EPOCH,
                null,
                null,
                null,
                DiscoverItem(id, SeerrItemType.MOVIE, "Row $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
            )
        val rows = mutableStateOf((21..40).map(::row))
        val offset = mutableStateOf(20)
        composeTestRule.setContent {
            val snapshot = rows.value
            WholphinTheme {
                CardGrid(
                    pager = snapshot,
                    columns = columns,
                    positionOffset = offset.value,
                    alignWindowToAbsoluteRows = true,
                    totalItems = 60,
                    itemKey = { snapshot[it].gridId },
                    focusAnchorKey = row(27).gridId,
                    focusRequestEpoch = 1,
                    onClickItem = { _, _ -> },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = FocusRequester(),
                    showJumpButtons = false,
                    showLetterButtons = false,
                    initialPosition = 6,
                    modifier = Modifier.height(280.dp),
                    cardContent = { details ->
                        Button(onClick = details.onClick, modifier = details.mod.height(120.dp)) {
                            Text("Row ${details.item?.key?.tmdbId}")
                        }
                    },
                )
            }
        }
        composeTestRule.onNodeWithText("Row 27").assertIsFocused()
        composeTestRule.onNodeWithText("27 / 60").assertIsDisplayed()
        val before = composeTestRule.onNodeWithText("Row 27").getUnclippedBoundsInRoot()
        composeTestRule.runOnIdle {
            offset.value = 0
            rows.value = (1..20).map(::row) + rows.value
        }
        composeTestRule.onNodeWithText("Row 27").assertIsFocused()
        composeTestRule.onNodeWithText("27 / 60").assertIsDisplayed()
        val after = composeTestRule.onNodeWithText("Row 27").getUnclippedBoundsInRoot()
        Assert.assertEquals(before.left.value, after.left.value, 1f)
        Assert.assertEquals(before.top.value, after.top.value, 1f)
    }

    @Test
    fun four_column_grid_preserves_focus_and_visual_position_after_twenty_item_prepend() =
        assert_paged_grid_keeps_column_and_focus_after_prepend(4)

    @Test
    fun seven_column_grid_preserves_focus_and_visual_position_after_twenty_item_prepend() =
        assert_paged_grid_keeps_column_and_focus_after_prepend(7)

    @Test
    fun watchlist_bookmark_expands_on_focus_and_tracks_confirmed_movie_and_series_membership() {
        Assert.assertEquals(R.drawable.ic_bookmark_outline, watchlistBookmarkIcon(WatchlistMembership.NOT_MEMBER))
        Assert.assertEquals(R.drawable.ic_bookmark_outline, watchlistBookmarkIcon(WatchlistMembership.UNKNOWN))
        Assert.assertEquals(R.drawable.ic_bookmark, watchlistBookmarkIcon(WatchlistMembership.MEMBER))
        val movie = MediaKey.Catalog(CatalogMediaType.MOVIE, 42)
        val series = MediaKey.Catalog(CatalogMediaType.SERIES, 42)
        val key = mutableStateOf(movie)
        val membership =
            MutableStateFlow(
                WatchlistSnapshot(
                    owner = "viewer",
                    membership =
                        mapOf(
                            movie to WatchlistMembership.NOT_MEMBER,
                            series to WatchlistMembership.MEMBER,
                        ),
                ),
            )
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns membership
        coEvery { repository.membership(any(), any()) } returns Unit
        coEvery { repository.setMembership(any(), any(), any()) } coAnswers {
            val target = firstArg<MediaKey.Catalog>()
            val saved = secondArg<Boolean>()
            membership.value =
                membership.value.copy(
                    membership =
                        membership.value.membership + (target to if (saved) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER),
                )
            true
        }
        val viewModel = WatchlistActionViewModel(repository)
        composeTestRule.setContent {
            WholphinTheme {
                Row {
                    Button(onClick = {}) { Text("Other action") }
                    WatchlistActionButton(key.value, "Title", viewModel = viewModel)
                }
            }
        }
        composeTestRule.onNodeWithText("Other action").requestFocus()
        composeTestRule.onAllNodesWithText("Add to Watchlist").assertCountEquals(0)
        val compactWidth =
            composeTestRule
                .onNodeWithContentDescription("Add to Watchlist")
                .fetchSemanticsNode()
                .boundsInRoot.width
        composeTestRule.onNodeWithContentDescription("Add to Watchlist").requestFocus()
        composeTestRule.onNodeWithText("Add to Watchlist").assertIsDisplayed()
        val expandedWidth =
            composeTestRule
                .onNodeWithContentDescription("Add to Watchlist")
                .fetchSemanticsNode()
                .boundsInRoot.width
        Assert.assertTrue("The standard action expands when focused", expandedWidth > compactWidth)
        composeTestRule.onNodeWithContentDescription("Add to Watchlist").performClickEnter()
        composeTestRule.onNodeWithContentDescription("Remove from Watchlist").assertIsEnabled()
        coVerify(exactly = 1) { repository.setMembership(movie, true, "Title") }

        composeTestRule.runOnIdle { key.value = series }
        composeTestRule.onNodeWithContentDescription("Remove from Watchlist").performClickEnter()
        composeTestRule.onNodeWithContentDescription("Add to Watchlist").assertIsEnabled()
        coVerify(exactly = 1) { repository.setMembership(series, false, "Title") }
        coVerify(exactly = 1) { repository.membership(setOf(movie), false) }
        coVerify(exactly = 1) { repository.membership(setOf(series), false) }
        Assert.assertEquals(WatchlistMembership.MEMBER, membership.value.membership[movie])
    }

    @Test
    fun watchlist_bookmark_is_disabled_for_unknown_loading_and_busy_membership() {
        val key = MediaKey.Catalog(CatalogMediaType.MOVIE, 42)
        val membership = MutableStateFlow(WatchlistSnapshot(owner = "viewer"))
        val pendingRead = CompletableDeferred<Unit>()
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns membership
        coEvery { repository.membership(any(), any()) } coAnswers { pendingRead.await() }
        val viewModel = WatchlistActionViewModel(repository)
        composeTestRule.setContent {
            WholphinTheme { WatchlistActionButton(key, "Title", viewModel = viewModel) }
        }
        composeTestRule.onNodeWithContentDescription("Watchlist unavailable").assertIsNotEnabled()
        composeTestRule.runOnIdle { pendingRead.complete(Unit) }
        composeTestRule.onNodeWithContentDescription("Watchlist unavailable").assertIsNotEnabled()
        composeTestRule.runOnIdle {
            membership.value = membership.value.copy(membership = mapOf(key to WatchlistMembership.MEMBER), busy = setOf(key))
        }
        composeTestRule.onNodeWithContentDescription("Remove from Watchlist").assertIsNotEnabled()
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }
        composeTestRule.runOnIdle { membership.value = WatchlistSnapshot() }
        composeTestRule.onAllNodesWithContentDescription("Remove from Watchlist").assertCountEquals(0)
    }

    @Test
    fun watchlist_bookmark_read_failure_offers_one_read_only_retry() {
        val key = MediaKey.Catalog(CatalogMediaType.MOVIE, 42)
        val membership = MutableStateFlow(WatchlistSnapshot(owner = "viewer"))
        val pendingRetry = CompletableDeferred<Unit>()
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns membership
        coEvery { repository.membership(setOf(key), false) } throws IllegalStateException("offline")
        coEvery { repository.membership(setOf(key), true) } coAnswers {
            pendingRetry.await()
            membership.value = membership.value.copy(membership = mapOf(key to WatchlistMembership.MEMBER))
        }
        val viewModel = WatchlistActionViewModel(repository)
        composeTestRule.setContent {
            WholphinTheme { WatchlistActionButton(key, "Title", viewModel = viewModel) }
        }

        composeTestRule
            .onNodeWithContentDescription("Retry Watchlist")
            .assertIsEnabled()
            .requestFocus()
            .assertIsFocused()
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }
        composeTestRule.onNodeWithContentDescription("Retry Watchlist").performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.Enter)
        }
        composeTestRule.onNodeWithContentDescription("Watchlist unavailable").assertIsNotEnabled()
        coVerify(exactly = 1) { repository.membership(setOf(key), false) }
        coVerify(exactly = 1) { repository.membership(setOf(key), true) }
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }

        composeTestRule.runOnIdle { pendingRetry.complete(Unit) }
        composeTestRule.onNodeWithContentDescription("Remove from Watchlist").assertIsEnabled()
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }
    }

    @Test
    fun movie_action_row_keeps_play_watched_favorite_and_more_around_bookmark() {
        val key = MediaKey.Catalog(CatalogMediaType.MOVIE, 42)
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns
            MutableStateFlow(WatchlistSnapshot(owner = "viewer", membership = mapOf(key to WatchlistMembership.MEMBER)))
        coEvery { repository.membership(any(), any()) } returns Unit
        val viewModel = WatchlistActionViewModel(repository)
        val actions = mutableListOf<String>()
        var focusEvents = 0
        composeTestRule.setContent {
            WholphinTheme {
                ExpandablePlayButtons(
                    title = "Movie",
                    resumePosition = Duration.ZERO,
                    watched = false,
                    favorite = false,
                    canDelete = false,
                    trailers = null,
                    playOnClick = { actions += "play" },
                    watchOnClick = { actions += "watched" },
                    favoriteOnClick = { actions += "favorite" },
                    moreOnClick = { actions += "more" },
                    trailerOnClick = {},
                    onConfirmDelete = {},
                    chooseVersionParams = null,
                    buttonOnFocusChanged = { if (it.isFocused) focusEvents++ },
                    additionalAction = { actionModifier ->
                        WatchlistActionButton(key, "Movie", modifier = actionModifier, viewModel = viewModel)
                    },
                )
            }
        }
        // Labels are intentionally absent until focused. Enter through the accessible bookmark.
        composeTestRule.onNodeWithContentDescription("Remove from Watchlist").requestFocus().performKeyInput {
            repeat(3) { pressKey(Key.DirectionLeft) }
        }
        composeTestRule.onNodeWithText("Play").assertIsFocused().performClickEnter()
        composeTestRule.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("Mark as watched").performClickEnter().performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("Favorite").performClickEnter().performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule
            .onNodeWithContentDescription(
                "Remove from Watchlist",
            ).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("More").performClickEnter()
        Assert.assertEquals(listOf("play", "watched", "favorite", "more"), actions)
        Assert.assertTrue(focusEvents >= 5)
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }
    }

    @Test
    fun discover_action_row_keeps_series_request_and_bookmark_in_one_focus_group() {
        val key = MediaKey.Catalog(CatalogMediaType.SERIES, 42)
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns
            MutableStateFlow(WatchlistSnapshot(owner = "viewer", membership = mapOf(key to WatchlistMembership.NOT_MEMBER)))
        coEvery { repository.membership(any(), any()) } returns Unit
        val viewModel = WatchlistActionViewModel(repository)
        var requests = 0
        composeTestRule.setContent {
            WholphinTheme {
                ExpandableDiscoverButtons(
                    canRequest = true,
                    canCancel = false,
                    availability = SeerrAvailability.UNKNOWN,
                    trailers = null,
                    requestOnClick = { requests++ },
                    cancelOnClick = {},
                    goToOnClick = {},
                    moreOnClick = {},
                    trailerOnClick = {},
                    buttonOnFocusChanged = {},
                    additionalAction = { actionModifier ->
                        WatchlistActionButton(key, "Series", modifier = actionModifier, viewModel = viewModel)
                    },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Add to Watchlist").requestFocus().performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule
            .onNodeWithText("Request")
            .assertIsFocused()
            .performClickEnter()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Add to Watchlist").assertIsFocused()
        Assert.assertEquals(1, requests)
        coVerify(exactly = 1) { repository.membership(setOf(key), false) }
        coVerify(exactly = 0) { repository.setMembership(any(), any(), any()) }
    }

    @Test
    fun movie_details_four_k_only_keeps_go_to_without_a_dead_standard_request_action() {
        val standardEligible = mutableStateOf(false)
        var requests = 0
        var goTo = 0
        composeTestRule.setContent {
            WholphinTheme {
                ExpandableDiscoverButtons(
                    canRequest = standardEligible.value,
                    canCancel = false,
                    availability = SeerrAvailability.AVAILABLE,
                    trailers = null,
                    requestOnClick = { requests++ },
                    cancelOnClick = {},
                    goToOnClick = { goTo++ },
                    moreOnClick = {},
                    trailerOnClick = {},
                    buttonOnFocusChanged = {},
                )
            }
        }
        composeTestRule.onAllNodesWithText("Request").assertCountEquals(0)
        composeTestRule.onAllNodes(hasClickAction())[0].requestFocus().performClickEnter()
        Assert.assertEquals(1, goTo)
        Assert.assertEquals(0, requests)
        composeTestRule.runOnIdle { standardEligible.value = true }
        composeTestRule.onAllNodes(hasClickAction())[0].performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("Request").assertIsFocused().performClickEnter()
        Assert.assertEquals(1, requests)
    }

    @Test
    fun watchlist_toolbar_cycles_compact_actions_and_preserves_focus_during_query_resets() {
        Assert.assertEquals(androidx.compose.ui.graphics.Color.Unspecified, watchlistFilterIconColor(WatchlistFilter.ALL))
        Assert.assertEquals(AppColors.Discover.Blue, watchlistFilterIconColor(WatchlistFilter.YES))
        Assert.assertEquals(AppColors.Discover.Red, watchlistFilterIconColor(WatchlistFilter.NO))
        val rows =
            (1..20).map { id ->
                WatchlistEntry(
                    MediaKey.Catalog(CatalogMediaType.MOVIE, id),
                    "Saved $id",
                    Instant.EPOCH,
                    null,
                    null,
                    null,
                    DiscoverItem(id, SeerrItemType.MOVIE, "Saved $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
                )
            }
        val screen = MutableStateFlow(WatchlistScreenState(owner = "viewer", entries = rows, page = 1, totalPages = 2, totalResults = 45))
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns MutableStateFlow(WatchlistSnapshot(owner = "viewer"))
        val viewModel = mockk<WatchlistViewModel>(relaxed = true)
        every { viewModel.state } returns screen
        every { viewModel.repository } returns repository
        every { viewModel.owned(any()) } answers {
            screen.value =
                screen.value.copy(
                    filters = screen.value.filters.copy(owned = firstArg()),
                    entries = emptyList(),
                    loading = true,
                    focusedIndex = 0,
                )
        }
        every { viewModel.watched(any()) } answers {
            screen.value =
                screen.value.copy(
                    filters = screen.value.filters.copy(watched = firstArg()),
                    entries = emptyList(),
                    loading = true,
                    focusedIndex = 0,
                )
        }
        composeTestRule.setContent { WholphinTheme { WatchlistPage(viewModel = viewModel) } }
        composeTestRule.onAllNodesWithText("Owned: All").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Watched: All").assertCountEquals(0)
        val owned =
            composeTestRule
                .onNodeWithContentDescription(
                    "Owned: All",
                ).assertWidthIsEqualTo(MinButtonSize)
                .fetchSemanticsNode()
                .boundsInRoot
        val watched =
            composeTestRule
                .onNodeWithContentDescription(
                    "Watched: All",
                ).assertWidthIsEqualTo(MinButtonSize)
                .fetchSemanticsNode()
                .boundsInRoot
        val random =
            composeTestRule
                .onNodeWithContentDescription("Random")
                .assertWidthIsEqualTo(MinButtonSize)
                .fetchSemanticsNode()
                .boundsInRoot
        with(composeTestRule.density) {
            Assert.assertEquals(8.dp.toPx(), watched.left - owned.right, 1f)
            Assert.assertEquals(8.dp.toPx(), random.left - watched.right, 1f)
            Assert.assertEquals(
                40.dp.toPx(),
                composeTestRule
                    .onRoot()
                    .fetchSemanticsNode()
                    .boundsInRoot.right - random.right,
                1f,
            )
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeTestRule.onAllNodesWithText(context.getString(R.string.fa_film), useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(context.getString(R.string.fa_check), useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(context.getString(R.string.fa_dice), useUnmergedTree = true).assertCountEquals(1)

        composeTestRule.onNodeWithContentDescription("Owned: All").requestFocus()
        composeTestRule.onNodeWithText("Owned: All").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Watched: All").assertCountEquals(0)
        // Focus movement alone must not select a filter. Only the focused action expands.
        composeTestRule.onNodeWithContentDescription("Owned: All").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Watched: All").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Owned: All").assertWidthIsEqualTo(MinButtonSize)
        composeTestRule.onAllNodesWithText("Owned: All").assertCountEquals(0)
        Assert.assertEquals(
            com.github.damontecres.wholphin.data.model
                .WatchlistFilters(),
            screen.value.filters,
        )
        composeTestRule.onNodeWithContentDescription("Watched: All").performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithContentDescription("Watched: All").requestFocus()
        composeTestRule.onNodeWithContentDescription("Watched: All").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Random").performClickEnter()
        composeTestRule.waitForIdle()
        io.mockk.verify(exactly = 1) { viewModel.random() }

        for (label in listOf("Owned", "Watched")) {
            composeTestRule.onNodeWithContentDescription("$label: All").requestFocus()
            var previous = "All"
            for ((selection, text) in listOf(WatchlistFilter.YES to "Yes", WatchlistFilter.NO to "No", WatchlistFilter.ALL to "All")) {
                composeTestRule.onNodeWithContentDescription("$label: $previous").performClickEnter()
                composeTestRule.waitForIdle()
                val actual = if (label == "Owned") screen.value.filters.owned else screen.value.filters.watched
                Assert.assertEquals(selection, actual)
                composeTestRule.onNodeWithText("$label: $text").assertIsDisplayed()
                composeTestRule.onNodeWithContentDescription("$label: $text").assertIsFocused()
                composeTestRule.onAllNodesWithText("Yes").assertCountEquals(0) // No dropdown.
                composeTestRule.onAllNodesWithText("Saved 1").assertCountEquals(0)
                // The delayed filtered grid must not take focus from the cycling action.
                composeTestRule.runOnIdle {
                    screen.value = screen.value.copy(entries = rows.take(9), totalResults = 9, totalPages = 1, loading = false)
                }
                composeTestRule.onNodeWithText("1 / 9").assertIsDisplayed()
                composeTestRule.onNodeWithContentDescription("$label: $text").assertIsFocused()
                previous = text
            }
        }
        for (selection in WatchlistFilter.entries) {
            io.mockk.verify(exactly = 1) { viewModel.owned(selection) }
            io.mockk.verify(exactly = 1) { viewModel.watched(selection) }
        }
        composeTestRule.runOnIdle {
            screen.value = screen.value.copy(entries = emptyList(), totalResults = 0, loading = false)
        }
        composeTestRule.onNodeWithText(context.getString(R.string.watchlist_empty)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Watched: All").assertIsFocused()
    }

    @Test
    fun watchlist_category_reset_keeps_outgoing_grid_keys_bound_to_its_rows() {
        val rows =
            (1..20).map { id ->
                WatchlistEntry(
                    MediaKey.Catalog(CatalogMediaType.MOVIE, id),
                    "Saved $id",
                    Instant.EPOCH,
                    null,
                    null,
                    null,
                    DiscoverItem(id, SeerrItemType.MOVIE, "Saved $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
                )
            }
        val screen =
            MutableStateFlow(
                WatchlistScreenState(owner = "viewer", entries = rows, page = 1, totalPages = 2, totalResults = 25),
            )
        val membership =
            MutableStateFlow(
                WatchlistSnapshot(owner = "viewer", membership = rows.associate { it.key to WatchlistMembership.MEMBER }),
            )
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns membership
        val viewModel = mockk<WatchlistViewModel>(relaxed = true)
        every { viewModel.state } returns screen
        every { viewModel.repository } returns repository
        every { viewModel.category(any()) } answers {
            // The real ViewModel resets paging/rows before the next category response.
            screen.value = WatchlistScreenState(owner = "viewer", category = firstArg(), loading = true)
        }
        composeTestRule.setContent {
            WholphinTheme { WatchlistPage(viewModel = viewModel) }
        }
        composeTestRule.onNodeWithText("1 / 25").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Watchlist (25)").assertCountEquals(0)

        repeat(2) {
            for (category in listOf(
                WatchlistCategory.MOVIES,
                WatchlistCategory.SHOWS,
                WatchlistCategory.ANIMATION,
                WatchlistCategory.ALL,
            )) {
                val label =
                    when (category) {
                        WatchlistCategory.ALL -> "All"
                        WatchlistCategory.MOVIES -> "Movies"
                        WatchlistCategory.SHOWS -> "Shows"
                        WatchlistCategory.ANIMATION -> "Animation"
                    }
                composeTestRule.onNodeWithText(label).performClick()
                // Compose invalidates the old key map while it still owns 20 rows.
                composeTestRule.waitForIdle()
                composeTestRule.onAllNodesWithText("Saved 1").assertCountEquals(0)
                Assert.assertEquals(category, screen.value.category)
                Assert.assertTrue(screen.value.entries.isEmpty())
                Assert.assertEquals(0, screen.value.page)
                Assert.assertEquals(20, membership.value.membership.size)
                composeTestRule.runOnIdle {
                    screen.value = screen.value.copy(entries = rows, page = 1, totalPages = 2, totalResults = 25, loading = false)
                }
                composeTestRule.onNodeWithText("1 / 25").assertIsDisplayed()
            }
        }
    }

    @Test
    fun watchlist_random_focuses_settled_window_and_keeps_absolute_footer() {
        fun row(id: Int) =
            WatchlistEntry(
                MediaKey.Catalog(CatalogMediaType.MOVIE, id),
                "Saved $id",
                Instant.EPOCH,
                null,
                null,
                null,
                DiscoverItem(id, SeerrItemType.MOVIE, "Saved $id", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null),
            )
        val initial = listOf(row(1))
        val target = (581..600).map(::row)
        val previous = (561..580).map(::row)
        val next = (601..620).map(::row)
        val screen =
            MutableStateFlow(WatchlistScreenState(owner = "viewer", entries = initial, page = 1, totalPages = 50, totalResults = 990))
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns MutableStateFlow(WatchlistSnapshot(owner = "viewer"))
        val viewModel = mockk<WatchlistViewModel>(relaxed = true)
        every { viewModel.state } returns screen
        every { viewModel.repository } returns repository
        every { viewModel.random() } answers {
            screen.value =
                WatchlistScreenState(
                    owner = "viewer",
                    entries = previous + target + next,
                    firstPage = 29,
                    page = 31,
                    totalPages = 50,
                    totalResults = 990,
                    focusedIndex = 26,
                    focusedKey = target[6].key,
                    gridEpoch = 1,
                    randomFocusEpoch = 1,
                )
        }
        var exitCount = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { exitCount++ }
                WatchlistPage(viewModel = viewModel)
            }
        }
        composeTestRule.onNode(hasText("Saved 1", substring = true) and hasClickAction()).requestFocus().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Random").requestFocus().performClickEnter()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("587 / 990").assertIsDisplayed()
        io.mockk.verify { viewModel.position(26, any()) }
        io.mockk.verify(exactly = 0) { viewModel.open(any()) }
        composeTestRule
            .onNode(
                hasText("Saved 587", substring = true) and hasClickAction(),
            ).assertIsFocused()
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        Assert.assertEquals(0, exitCount)
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, exitCount)

        composeTestRule.runOnIdle {
            screen.value = screen.value.copy(entries = (541..560).map(::row) + screen.value.entries, firstPage = 28, focusedIndex = 46)
        }
        composeTestRule.onNodeWithText("587 / 990").assertIsDisplayed()
        io.mockk.verify(exactly = 0) { viewModel.open(any()) }
    }
}
