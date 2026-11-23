package com.github.damontecres.wholphin.ui

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.LibraryCollectionItem
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.model.CatalogMediaType
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistEntry
import com.github.damontecres.wholphin.data.model.WatchlistFilter
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.services.CollectionMovieLink
import com.github.damontecres.wholphin.services.CollectionsEntry
import com.github.damontecres.wholphin.services.NavigationManager
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
import com.github.damontecres.wholphin.ui.collections.CollectionDetailsState
import com.github.damontecres.wholphin.ui.collections.CollectionMovieAction
import com.github.damontecres.wholphin.ui.collections.CollectionMovieLinkState
import com.github.damontecres.wholphin.ui.collections.CollectionRequestEligibility
import com.github.damontecres.wholphin.ui.collections.CollectionRequestMovie
import com.github.damontecres.wholphin.ui.collections.CollectionRequestState
import com.github.damontecres.wholphin.ui.collections.CollectionsPage
import com.github.damontecres.wholphin.ui.collections.CollectionsScreenState
import com.github.damontecres.wholphin.ui.collections.CollectionsViewModel
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetails
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.wholphin.ui.components.MinButtonSize
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.discover.ExpandableDiscoverButtons
import com.github.damontecres.wholphin.ui.detail.discover.OriginalLanguageText
import com.github.damontecres.wholphin.ui.detail.discover.RequestMovieDialog
import com.github.damontecres.wholphin.ui.detail.discover.RequestSeason
import com.github.damontecres.wholphin.ui.detail.discover.RequestSeasons
import com.github.damontecres.wholphin.ui.detail.discover.SeerrProfile
import com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData
import com.github.damontecres.wholphin.ui.detail.discover.originalLanguageName
import com.github.damontecres.wholphin.ui.discover.QuickMovieConfirmGate
import com.github.damontecres.wholphin.ui.nav.Destination
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
import io.mockk.verify
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
    fun foreign_pending_season_is_not_selected_or_submitted_by_dpad() {
        val foreignPending = RequestSeason(Season(seasonNumber = 1), RequestStatus.PENDING, SeerrAvailability.PENDING, false)
        val missing = RequestSeason(Season(seasonNumber = 2), RequestStatus.UNKNOWN, SeerrAvailability.UNKNOWN, true)
        val submitted = mutableListOf<List<Int>>()
        composeTestRule.setContent {
            WholphinTheme {
                RequestSeasons(
                    id = 42,
                    title = "Fixture series",
                    seasons = listOf(foreignPending, missing),
                    seasons4k = emptyList(),
                    data = SeerrRequestData(serverId = 11),
                    requestEnabled = true,
                    request4kEnabled = false,
                    onSubmit = { submitted += it.seasons },
                )
            }
        }
        composeTestRule.onNodeWithTag("requestSeasonSubmit").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("requestSeasonSwitch:1", useUnmergedTree = true).assertIsOff().assertIsNotEnabled()
        Assert.assertTrue(submitted.isEmpty())
        val requestable = composeTestRule.onNodeWithTag("requestSeasonSwitch:2", useUnmergedTree = true)
        requestable
            .assertIsOff()
            .assertIsEnabled()
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        requestable.assertIsOn()
        composeTestRule
            .onNodeWithTag(
                "requestSeasonSubmit",
            ).assertIsEnabled()
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        Assert.assertEquals(listOf(listOf(2)), submitted)
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
    fun quick_movie_request_back_then_first_short_ok_opens_originating_card() {
        val gate = QuickMovieConfirmGate()
        val showing = mutableStateOf(false)
        val dialogReady = mutableStateOf(false)
        var details = 0
        var openings = 0
        var submissions = 0
        val item = DiscoverItem(42, SeerrItemType.MOVIE, "Fixture", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val dismiss = {
            gate.clear()
            showing.value = false
        }
        val cardGuard =
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                gate.onCardKey(native.action, native.keyCode, native.repeatCount)
            }
        val dialogGuard =
            Modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                gate.onKey(native.action, native.keyCode, native.repeatCount)
            }
        composeTestRule.setContent {
            WholphinTheme {
                LaunchedEffect(showing.value, gate.released) {
                    dialogReady.value = false
                    if (showing.value && gate.released) {
                        withFrameNanos { }
                        dialogReady.value = showing.value && gate.released
                    }
                }
                Box(cardGuard) {
                    DiscoverItemCard(item, onClick = { details++ }, onLongClick = {
                        openings++
                        gate.reset()
                        showing.value = true
                    }, useFallbackText = true)
                }
                if (dialogReady.value) {
                    RequestMovieDialog(
                        loading = LoadingState.Success,
                        data = SeerrRequestData(serverId = 11, profiles = listOf(SeerrProfile(7, "1080p HDR", true))),
                        requestEnabled = true,
                        movie =
                            com.github.damontecres.wholphin.api.seerr.model
                                .MovieDetails(id = 42, title = "Fixture"),
                        onSubmit = { submissions++ },
                        confirmAction = gate::consumeConfirmation,
                        onDismissRequest = dismiss,
                        confirmationReady = gate.released,
                        dialogModifier = dialogGuard,
                    )
                }
            }
        }
        val card = composeTestRule.onNode(hasClickAction())
        card.requestFocus().performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(800)
            keyUp(Key.DirectionCenter)
        }
        Assert.assertEquals(1, openings)
        Assert.assertTrue(showing.value)
        // Exercise the same callback that BasicDialog invokes for Back; Robolectric does not dispatch dialog-window Back.
        composeTestRule.runOnIdle { dismiss() }
        Assert.assertFalse(showing.value)
        Assert.assertEquals(0, submissions)
        card.assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        Assert.assertEquals(1, details)
        card.performKeyInput {
            keyDown(Key.DirectionCenter)
            advanceEventTime(800)
            keyUp(Key.DirectionCenter)
        }
        Assert.assertEquals(2, openings)
        Assert.assertEquals(0, submissions)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun quick_movie_poster_is_focusable_only_when_navigation_is_supplied_and_never_submits() {
        var navigations = 0
        var submissions = 0
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(serverId = 0, profiles = listOf(SeerrProfile(12, "1080p HDR", true))),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = { submissions++ },
                    onDismissRequest = {},
                    onPosterClick = { navigations++ },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithTag("movieRequestPoster").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        Assert.assertEquals(1, navigations)
        Assert.assertEquals(0, submissions)
        composeTestRule.onNodeWithTag("movieRequestPoster").performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("1080p HDR").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun quick_movie_poster_is_focus_fallback_when_quality_actions_are_unavailable() {
        var navigations = 0
        var submissions = 0
        composeTestRule.setContent {
            WholphinTheme {
                RequestMovieDialog(
                    loading = LoadingState.Success,
                    data = SeerrRequestData(),
                    requestEnabled = true,
                    movie =
                        com.github.damontecres.wholphin.api.seerr.model
                            .MovieDetails(id = 42, title = "Fixture"),
                    onSubmit = { submissions++ },
                    onDismissRequest = {},
                    onPosterClick = { navigations++ },
                )
            }
        }
        composeTestRule.onNodeWithTag("movieRequestPoster").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        Assert.assertEquals(1, navigations)
        Assert.assertEquals(0, submissions)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun original_language_uses_existing_movie_and_series_codes_without_unknown_labels() {
        val movie =
            com.github.damontecres.wholphin.api.seerr.model
                .MovieDetails(originalLanguage = "en")
        val series =
            com.github.damontecres.wholphin.api.seerr.model
                .TvDetails(originalLanguage = "ja")
        val movieLanguage = originalLanguageName(movie.originalLanguage)
        val seriesLanguage = originalLanguageName(series.originalLanguage)
        Assert.assertTrue(!movieLanguage.isNullOrBlank() && movieLanguage != "en")
        Assert.assertTrue(!seriesLanguage.isNullOrBlank() && seriesLanguage != "ja")
        Assert.assertEquals(null, originalLanguageName(null))
        Assert.assertEquals(null, originalLanguageName("und"))
        Assert.assertEquals(null, originalLanguageName("zz"))
        Assert.assertEquals(null, originalLanguageName("  "))
        composeTestRule.setContent {
            WholphinTheme {
                Column {
                    OriginalLanguageText(movie.originalLanguage)
                    OriginalLanguageText(series.originalLanguage)
                    OriginalLanguageText("und")
                }
            }
        }
        composeTestRule.onNodeWithText("Original language: $movieLanguage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Original language: $seriesLanguage").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Original language: Unknown").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun quick_movie_request_metadata_shows_original_language_and_omits_missing_values() {
        val language = mutableStateOf<String?>("en")
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
                            originalLanguage = language.value,
                        ),
                    onSubmit = {},
                    onDismissRequest = {},
                    onPosterClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Original language: ${originalLanguageName("en")}").assertIsDisplayed()
        composeTestRule.runOnIdle { language.value = null }
        composeTestRule.onAllNodesWithText("Original language: ${originalLanguageName("en")}").assertCountEquals(0)
        composeTestRule.runOnIdle { language.value = "und" }
        composeTestRule.onAllNodesWithText("Original language: Unknown").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun normal_movie_request_metadata_shows_original_language_without_poster_navigation() {
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
                            originalLanguage = "ja",
                        ),
                    rating = DiscoverRating(86, 7.5f, 75),
                    onSubmit = {},
                    onDismissRequest = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Original language: ${originalLanguageName("ja")}").assertIsDisplayed()
        composeTestRule.onNodeWithText("86%").assertIsDisplayed()
        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audienceUprightIcon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movieRequestPoster").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land")
    fun movie_request_rating_arrives_in_place_without_changing_quality_focus() {
        val rating = mutableStateOf<DiscoverRating?>(null)
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
                            originalLanguage = "en",
                            releaseDate = "2026-01-01",
                            overview = "A fixture synopsis",
                        ),
                    rating = rating.value,
                    onSubmit = {},
                    onDismissRequest = {},
                    onPosterClick = {},
                )
            }
        }
        val quality = composeTestRule.onNodeWithContentDescription("1080p HDR")
        quality.assertIsFocused()
        val slot = composeTestRule.onNodeWithTag("movieRequestRatingSlot")
        val before = slot.getUnclippedBoundsInRoot()
        val criticsBefore = composeTestRule.onNodeWithTag("movieRequestCriticsSlot").getUnclippedBoundsInRoot()
        val audienceBefore = composeTestRule.onNodeWithTag("movieRequestAudienceSlot").getUnclippedBoundsInRoot()
        val factsBefore = composeTestRule.onNodeWithText("2026").getUnclippedBoundsInRoot()
        val languageBefore = composeTestRule.onNodeWithText("Original language: ${originalLanguageName("en")}").getUnclippedBoundsInRoot()
        val synopsisBefore = composeTestRule.onNodeWithText("A fixture synopsis").getUnclippedBoundsInRoot()
        val qualityBefore = quality.getUnclippedBoundsInRoot()
        composeTestRule.onAllNodesWithText("86%").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("75%").assertCountEquals(0)
        composeTestRule.runOnIdle { rating.value = DiscoverRating(86, 7.5f, 75) }
        composeTestRule.onNodeWithText("86%").assertIsDisplayed()
        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audienceUprightIcon").assertIsDisplayed()
        Assert.assertEquals(before, slot.getUnclippedBoundsInRoot())
        Assert.assertEquals(criticsBefore, composeTestRule.onNodeWithTag("movieRequestCriticsSlot").getUnclippedBoundsInRoot())
        Assert.assertEquals(audienceBefore, composeTestRule.onNodeWithTag("movieRequestAudienceSlot").getUnclippedBoundsInRoot())
        Assert.assertEquals(factsBefore, composeTestRule.onNodeWithText("2026").getUnclippedBoundsInRoot())
        Assert.assertEquals(
            languageBefore,
            composeTestRule.onNodeWithText("Original language: ${originalLanguageName("en")}").getUnclippedBoundsInRoot(),
        )
        Assert.assertEquals(synopsisBefore, composeTestRule.onNodeWithText("A fixture synopsis").getUnclippedBoundsInRoot())
        Assert.assertEquals(qualityBefore, quality.getUnclippedBoundsInRoot())
        quality.assertIsFocused()
        composeTestRule.onNodeWithText("Original language: ${originalLanguageName("en")}").assertIsDisplayed()

        composeTestRule.runOnIdle { rating.value = DiscoverRating(86, null) }
        composeTestRule.onNodeWithText("86%").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("75%").assertCountEquals(0)
        composeTestRule.onNodeWithTag("audienceUprightIcon").assertDoesNotExist()

        composeTestRule.runOnIdle { rating.value = DiscoverRating(null, 7.5f, 75) }
        composeTestRule.onAllNodesWithText("86%").assertCountEquals(0)
        composeTestRule.onNodeWithText("75%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audienceUprightIcon").assertIsDisplayed()

        composeTestRule.runOnIdle { rating.value = DiscoverRating(null, 4.2f, 42) }
        composeTestRule.onNodeWithText("42%").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audienceSpilledIcon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audienceUprightIcon").assertDoesNotExist()

        composeTestRule.runOnIdle { rating.value = DiscoverRating(null, 0f, 0) }
        composeTestRule.onNodeWithText("0%").assertIsDisplayed()

        composeTestRule.runOnIdle { rating.value = DiscoverRating(null, null) }
        composeTestRule.onAllNodesWithText("0%").assertCountEquals(0)
        composeTestRule.onNodeWithTag("audienceSpilledIcon").assertDoesNotExist()
        Assert.assertEquals(before, slot.getUnclippedBoundsInRoot())
        Assert.assertEquals(qualityBefore, quality.getUnclippedBoundsInRoot())
        quality.assertIsFocused()
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
        composeTestRule.onNodeWithTag("movieRequestPoster").assertDoesNotExist()
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
    fun collection_action_is_compact_until_focused_and_keeps_movie_actions() {
        val actions = mutableListOf<String>()
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
                    buttonOnFocusChanged = {},
                    collectionAction = { modifier ->
                        CollectionMovieAction(
                            CollectionMovieLinkState(link = CollectionMovieLink(7, "Alien Collection", null)),
                            onOpen = { actions += "collection" },
                            onRetry = { actions += "retry" },
                            modifier = modifier,
                        )
                    },
                )
            }
        }
        val action = composeTestRule.onNodeWithTag("collectionMovieAction")
        action.assertWidthIsEqualTo(MinButtonSize)
        composeTestRule.onAllNodes(hasClickAction())[0].requestFocus().performKeyInput {
            repeat(3) { pressKey(Key.DirectionRight) }
        }
        action.assertIsFocused()
        composeTestRule.onNodeWithText("Alien Collection").assertIsDisplayed()
        action.performClickEnter()
        action.performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithText("More").assertIsFocused().performClickEnter()
        Assert.assertEquals(listOf("collection", "more"), actions)
    }

    @Test
    fun missing_collection_hides_action_but_failed_lookup_retains_retry() {
        val state = mutableStateOf(CollectionMovieLinkState())
        var retries = 0
        composeTestRule.setContent {
            WholphinTheme {
                CollectionMovieAction(state.value, onOpen = {}, onRetry = { retries++ })
            }
        }
        composeTestRule.onAllNodesWithTag("collectionMovieAction").assertCountEquals(0)
        composeTestRule.runOnIdle { state.value = CollectionMovieLinkState(error = true, retryable = true) }
        composeTestRule.onNodeWithTag("collectionMovieAction").requestFocus().performClickEnter()
        Assert.assertEquals(1, retries)
    }

    @Test
    fun discover_movie_collection_action_navigates_without_changing_request_action() {
        var requests = 0
        var collections = 0
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
                    collectionAction = { modifier ->
                        CollectionMovieAction(
                            CollectionMovieLinkState(link = CollectionMovieLink(7, "Alien Collection", null)),
                            onOpen = { collections++ },
                            onRetry = {},
                            modifier = modifier,
                        )
                    },
                )
            }
        }
        composeTestRule.onAllNodes(hasClickAction())[0].requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithTag("collectionMovieAction").assertIsFocused().performClickEnter()
        composeTestRule.onNodeWithTag("collectionMovieAction").performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithText("Request").assertIsFocused().performClickEnter()
        Assert.assertEquals(1, collections)
        Assert.assertEquals(1, requests)
    }

    @Test
    fun collections_browser_resume_restores_originating_card_after_poster_exit() {
        class Owner : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }

        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        val entry =
            CollectionsEntry(
                LibraryCollectionItem(7, "Alien Collection", null, null, null, 1, 4, LibraryCollectionItem.Completeness.INCOMPLETE),
                null,
                null,
            )
        val vm = mockk<CollectionsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionsScreenState(
                    owner = "viewer",
                    entries = listOf(entry),
                    total = 1,
                    focusedKey = entry.gridId,
                ),
            )
        val avatarFocus = FocusRequester()
        composeTestRule.setContent {
            WholphinTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    Box {
                        CollectionsPage(viewModel = vm)
                        Button(onClick = {}, modifier = Modifier.focusRequester(avatarFocus)) { Text("Avatar") }
                    }
                }
            }
        }
        composeTestRule.runOnIdle { avatarFocus.requestFocus() }
        composeTestRule.onNodeWithText("Avatar").assertIsFocused()
        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.onAllNodes(hasText("Alien Collection") and hasClickAction())[0].assertIsFocused()
    }

    @Test
    fun collections_stale_last_good_remains_visible_without_technical_notice() {
        val entry =
            CollectionsEntry(
                LibraryCollectionItem(7, "Alien Collection", null, null, null, 1, 4, LibraryCollectionItem.Completeness.INCOMPLETE),
                null,
                null,
            )
        val vm = mockk<CollectionsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(CollectionsScreenState(owner = "viewer", entries = listOf(entry), total = 1, stale = true))
        composeTestRule.setContent { WholphinTheme { CollectionsPage(viewModel = vm) } }
        composeTestRule.onAllNodesWithText("Alien Collection")[0].assertExists()
        composeTestRule.onAllNodesWithText("1/4").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Showing an older Collections snapshot.").assertCountEquals(0)
    }

    @Test
    fun collection_details_back_follows_grid_poster_navigation_contract() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val second = DiscoverItem(43, SeerrItemType.MOVIE, "Aliens", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie, second)),
                ),
            )
        var backs = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { backs++ }
                SeerrCollectionDetails(destination, viewModel = vm)
            }
        }
        composeTestRule.onAllNodes(hasText("Alien") and hasClickAction())[0].requestFocus().performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onAllNodes(hasText("Aliens") and hasClickAction())[0].assertIsFocused()
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        val poster = composeTestRule.onNodeWithTag("collectionPoster").assertIsFocused().assertIsDisplayed()
        val posterBounds = poster.getUnclippedBoundsInRoot()
        val screenBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        Assert.assertTrue(
            posterBounds.left >= screenBounds.left && posterBounds.top >= screenBounds.top &&
                posterBounds.right <= screenBounds.right && posterBounds.bottom <= screenBounds.bottom,
        )
        Assert.assertEquals(0, backs)
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, backs)
        verify(exactly = 0) { vm.openRequest() }
        composeTestRule.onAllNodesWithText("Back").assertCountEquals(0)
    }

    @Test
    fun collection_details_initial_back_exits_without_focusing_poster_or_avatar() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                ),
            )
        var backs = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { backs++ }
                SeerrCollectionDetails(destination, viewModel = vm)
            }
        }
        composeTestRule.onAllNodes(hasText("Alien") and hasClickAction())[0].assertIsFocused()
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, backs)
        composeTestRule.onNodeWithTag("collectionPoster").assertIsNotFocused()
        verify(exactly = 0) { vm.openRequest() }
    }

    @Test
    fun collection_details_back_from_similar_focuses_disabled_poster_then_exits() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similar = listOf(CollectionDetailsData(8, "Another Collection", null, null, null, emptyList())),
                    similarFocusedId = 8,
                ),
            )
        var backs = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { backs++ }
                SeerrCollectionDetails(destination, viewModel = vm)
            }
        }
        composeTestRule.onAllNodesWithText("Another Collection")[0].assertIsFocused()
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithTag("collectionPoster").assertIsFocused().assertIsDisplayed()
        Assert.assertEquals(0, backs)
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, backs)
        verify(exactly = 0) { vm.openRequest() }
    }

    @Test
    fun collection_details_back_from_member_focuses_request_poster_without_opening_modal() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val second = DiscoverItem(43, SeerrItemType.MOVIE, "Aliens", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 0, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie, second)),
                    requestEligibility = mapOf(42 to CollectionRequestEligibility.ELIGIBLE),
                ),
            )
        var backs = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { backs++ }
                SeerrCollectionDetails(destination, viewModel = vm)
            }
        }
        composeTestRule.onAllNodes(hasText("Alien") and hasClickAction())[0].requestFocus().performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onAllNodes(hasText("Aliens") and hasClickAction())[0].assertIsFocused()
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithTag("collectionRequestPoster").assertIsFocused().assertIsDisplayed()
        Assert.assertEquals(0, backs)
        verify(exactly = 0) { vm.openRequest() }
        composeTestRule.runOnIdle { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, backs)
    }

    @Test
    fun collection_details_uses_poster_badge_without_textual_completion_rows() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 2, 4, "viewer"), viewModel = vm) }
        }
        composeTestRule.onNodeWithTag("collectionPoster").assertExists()
        composeTestRule.onAllNodesWithText("2/4").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("2 available · 2 missing").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Movie").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Count unavailable").assertCountEquals(0)
    }

    @Test
    fun collection_details_unknown_count_has_no_badge() {
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, emptyList()),
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, null, null, "viewer"), viewModel = vm) }
        }
        composeTestRule.onAllNodesWithText("Count unavailable").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Request Collection").assertCountEquals(0)
    }

    @Test
    fun collection_details_prefers_authoritative_count_and_suppresses_known_zero() {
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val details = CollectionDetailsData(645, "James Bond", null, null, null, emptyList(), availableMovies = 10, totalMovies = 26)
        val state = MutableStateFlow(CollectionDetailsState(owner = "viewer", collectionId = 645, details = details))
        val destination = mutableStateOf(Destination.SeerrCollection(645, 1, 2, "viewer"))
        every { vm.state } returns state
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(destination.value, viewModel = vm) }
        }
        composeTestRule.onAllNodesWithText("10/26").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("1/2").assertCountEquals(0)
        composeTestRule.runOnIdle { destination.value = Destination.SeerrCollection(645, null, null, "viewer") }
        composeTestRule.onAllNodesWithText("10/26").assertCountEquals(1)
        composeTestRule.runOnIdle { state.value = state.value.copy(details = details.copy(availableMovies = 0)) }
        composeTestRule.onAllNodesWithText("10/26").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Count unavailable").assertCountEquals(0)
    }

    @Test
    fun collection_unreleased_card_is_focusable_but_cannot_open() {
        val future =
            DiscoverItem(
                43,
                SeerrItemType.MOVIE,
                "Future",
                null,
                null,
                SeerrAvailability.AVAILABLE,
                LocalDate.now().plusYears(1),
                null,
                null,
                null,
                null,
            )
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Saga", null, null, null, listOf(future)),
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, null, null, "viewer"), viewModel = vm) }
        }
        composeTestRule
            .onAllNodes(hasText("Future") and hasClickAction())[0]
            .requestFocus()
            .assertIsFocused()
            .performClickEnter()
        verify(exactly = 0) { vm.open(any()) }
    }

    @Test
    fun collection_released_unavailable_card_remains_focusable_and_actionable() {
        val released = LocalDate.now().minusDays(1)
        val available =
            DiscoverItem(42, SeerrItemType.MOVIE, "Available", null, null, SeerrAvailability.AVAILABLE, released, null, null, null, null)
        val unavailable =
            DiscoverItem(43, SeerrItemType.MOVIE, "Unavailable", null, null, SeerrAvailability.UNKNOWN, released, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Saga", null, null, null, listOf(available, unavailable)),
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, null, null, "viewer"), viewModel = vm) }
        }
        composeTestRule
            .onAllNodes(hasText("Unavailable") and hasClickAction())[0]
            .requestFocus()
            .assertIsFocused()
            .performClickEnter()
        verify(exactly = 1) { vm.open(unavailable) }
    }

    @Test
    fun eligible_collection_poster_opens_existing_modal_and_restores_focus_on_dismissal() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    requestEligibility = mapOf(42 to CollectionRequestEligibility.ELIGIBLE),
                ),
            )
        every { vm.state } returns state
        every { vm.openRequest() } answers {
            state.value =
                state.value.copy(
                    request =
                        CollectionRequestState(
                            "viewer",
                            7,
                            listOf(CollectionRequestMovie(movie, CollectionRequestEligibility.ELIGIBLE)),
                        ),
                )
        }
        every { vm.closeRequest() } answers { state.value = state.value.copy(request = null) }
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 0, 1, "viewer"), viewModel = vm) }
        }
        val poster = composeTestRule.onNodeWithTag("collectionRequestPoster").requestFocus().assertIsFocused()
        verify(exactly = 0) { vm.openRequest() }
        poster.performClickEnter()
        verify(exactly = 1) { vm.openRequest() }
        composeTestRule.onNodeWithTag("collectionRequestDialog").assertExists()
        composeTestRule.runOnIdle { state.value = state.value.copy(request = null) }
        composeTestRule.onNodeWithTag("collectionRequestPoster").assertIsFocused()
        verify(exactly = 0) { vm.submitRequests(any()) }
    }

    @Test
    fun collection_poster_becomes_non_actionable_when_eligibility_changes() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    requestEligibility = mapOf(42 to CollectionRequestEligibility.ELIGIBLE),
                ),
            )
        every { vm.state } returns state
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 1, 1, "viewer"), viewModel = vm) }
        }
        composeTestRule.onNodeWithTag("collectionRequestPoster").assertExists()
        composeTestRule.runOnIdle {
            state.value =
                state.value.copy(
                    requestEligibility = mapOf(42 to CollectionRequestEligibility.AVAILABLE),
                )
        }
        composeTestRule.onAllNodesWithTag("collectionRequestPoster").assertCountEquals(0)
        composeTestRule.onNodeWithTag("collectionPoster").assertExists()
        verify(exactly = 0) { vm.openRequest() }
    }

    @Test
    fun collection_first_row_up_reaches_eligible_poster() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.UNKNOWN, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    requestEligibility = mapOf(42 to CollectionRequestEligibility.ELIGIBLE),
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 0, 1, "viewer"), viewModel = vm) }
        }
        composeTestRule
            .onAllNodes(hasText("Alien") and hasClickAction())[0]
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeTestRule.onNodeWithTag("collectionRequestPoster").assertIsFocused()
        verify(exactly = 0) { vm.openRequest() }
    }

    @Test
    fun collection_card_overrides_keep_default_badges_and_pending_indicator() {
        val available =
            DiscoverItem(42, SeerrItemType.MOVIE, "Available", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val pending = DiscoverItem(43, SeerrItemType.MOVIE, "Pending", null, null, SeerrAvailability.PENDING, null, null, null, null, null)
        composeTestRule.setContent {
            WholphinTheme {
                Row {
                    DiscoverItemCard(available, onClick = {}, onLongClick = {})
                    DiscoverItemCard(
                        available,
                        onClick = {},
                        onLongClick = {},
                        showOverlay = false,
                        showAvailableIndicator = false,
                    )
                    DiscoverItemCard(
                        pending,
                        onClick = {},
                        onLongClick = {},
                        showOverlay = false,
                        showAvailableIndicator = false,
                    )
                }
            }
        }
        composeTestRule.onAllNodesWithText("Movie").assertCountEquals(1)
        composeTestRule.onAllNodesWithText(composeTestRule.activity.getString(R.string.fa_check)).assertCountEquals(1)
        composeTestRule.onAllNodesWithText(composeTestRule.activity.getString(R.string.fa_bell)).assertCountEquals(1)
    }

    @Test
    fun collection_details_shows_optional_similar_row_without_inventing_completion() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similar = listOf(CollectionDetailsData(8, "Another Collection", null, null, null, emptyList())),
                    similarFocusedId = 8,
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(destination, viewModel = vm) }
        }
        composeTestRule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(1)
        composeTestRule.onNodeWithText("Similar Collections").assertExists()
        composeTestRule.onAllNodesWithText("Another Collection").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Count unavailable").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Another Collection")[0].assertIsFocused()
    }

    @Test
    fun collection_details_keeps_focused_similar_card_while_more_results_arrive() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similar = listOf(CollectionDetailsData(8, "First Related", null, null, null, emptyList())),
                    similarLoading = true,
                    similarFocusedId = 8,
                ),
            )
        every { vm.state } returns state
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 1, 1, "viewer"), viewModel = vm) }
        }
        composeTestRule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(1)
        composeTestRule.onAllNodesWithText("First Related")[0].assertIsFocused()
        composeTestRule.runOnIdle {
            state.value =
                state.value.copy(
                    similar =
                        state.value.similar + CollectionDetailsData(9, "Second Related", null, null, null, emptyList()),
                )
        }
        composeTestRule.onAllNodesWithText("First Related")[0].assertIsFocused()
        composeTestRule.onAllNodesWithText("Second Related").assertCountEquals(2)
    }

    @Test
    fun collection_details_preserves_movie_grid_when_similar_read_is_unavailable() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        every { vm.state } returns
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similarReadError = true,
                ),
            )
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 1, 1, "viewer"), viewModel = vm) }
        }
        composeTestRule.onAllNodesWithText("Alien").assertCountEquals(2)
        composeTestRule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(1)
        composeTestRule.onNodeWithText("Similar Collections could not be refreshed.").assertExists()
    }

    @Test
    fun collection_details_restores_focus_to_offscreen_similar_card() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similar = (8..17).map { CollectionDetailsData(it, "Collection $it", null, null, null, emptyList()) },
                    similarFocusedId = 17,
                ),
            )
        every { vm.state } returns state
        every { vm.position(any()) } answers {
            state.value = state.value.copy(similarFocusedId = null)
        }
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(destination, viewModel = vm) }
        }
        composeTestRule.onNodeWithText("Similar Collections").assertExists()
        composeTestRule.onAllNodesWithText("Collection 17")[0].assertIsFocused()
    }

    @Test
    fun collection_details_similar_up_down_returns_to_high_index_with_only_two_members() {
        val movies =
            (1..2).map {
                DiscoverItem(it, SeerrItemType.MOVIE, "Member $it", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
            }
        val similar = (101..109).map { CollectionDetailsData(it, "Related $it", null, null, null, emptyList()) }
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Test Collection", null, null, null, movies),
                    focusedKey = "tmdb:movie:2",
                    focusedIndex = 1,
                    similar = similar,
                    similarFocusedId = 101,
                ),
            )
        every { vm.state } returns state
        every { vm.position(any()) } answers {
            val index = firstArg<Int>()
            state.value =
                state.value.copy(focusedKey = "tmdb:movie:${movies[index].id}", focusedIndex = index, similarFocusedId = null)
        }
        every { vm.focusSimilar(any()) } answers { state.value = state.value.copy(similarFocusedId = firstArg()) }
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 1, 2, "viewer"), viewModel = vm) }
        }
        for (id in 101..108) {
            composeTestRule.onAllNodes(hasText("Related $id") and hasClickAction())[0].assertIsFocused().performKeyInput {
                pressKey(Key.DirectionRight)
            }
        }
        composeTestRule.onAllNodes(hasText("Related 109") and hasClickAction())[0].assertIsFocused().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.onAllNodes(hasText("Member 2") and hasClickAction())[0].assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onAllNodes(hasText("Related 109") and hasClickAction())[0].assertIsFocused().assertIsDisplayed()
    }

    @Test
    fun collection_details_repeated_similar_member_transitions_keep_focus_with_multiple_rows() {
        val movies =
            (1..8).map {
                DiscoverItem(it, SeerrItemType.MOVIE, "Member $it", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
            }
        val similar = (101..109).map { CollectionDetailsData(it, "Related $it", null, null, null, emptyList()) }
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Test Collection", null, null, null, movies),
                    focusedKey = "tmdb:movie:8",
                    focusedIndex = 7,
                    similar = similar,
                    similarFocusedId = 109,
                ),
            )
        every { vm.state } returns state
        every { vm.position(any()) } answers {
            val index = firstArg<Int>()
            state.value =
                state.value.copy(focusedKey = "tmdb:movie:${movies[index].id}", focusedIndex = index, similarFocusedId = null)
        }
        every { vm.focusSimilar(any()) } answers { state.value = state.value.copy(similarFocusedId = firstArg()) }
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(Destination.SeerrCollection(7, 4, 8, "viewer"), viewModel = vm) }
        }
        repeat(2) {
            composeTestRule.onAllNodes(hasText("Related 109") and hasClickAction())[0].assertIsFocused().performKeyInput {
                pressKey(Key.DirectionUp)
            }
            composeTestRule.onAllNodes(hasText("Member 8") and hasClickAction())[0].assertIsFocused().performKeyInput {
                pressKey(Key.DirectionDown)
            }
            composeTestRule.onAllNodes(hasText("Related 109") and hasClickAction())[0].assertIsFocused().assertIsDisplayed()
        }
    }

    @Test
    fun collection_details_similar_dpad_right_does_not_snap_each_card_to_row_start() {
        val movie = DiscoverItem(42, SeerrItemType.MOVIE, "Alien", null, null, SeerrAvailability.AVAILABLE, null, null, null, null, null)
        val destination = Destination.SeerrCollection(7, 1, 1, "viewer")
        val vm = mockk<SeerrCollectionDetailsViewModel>(relaxed = true)
        val state =
            MutableStateFlow(
                CollectionDetailsState(
                    owner = "viewer",
                    collectionId = 7,
                    details = CollectionDetailsData(7, "Alien Collection", null, null, null, listOf(movie)),
                    similar = (8..17).map { CollectionDetailsData(it, "Collection $it", null, null, null, emptyList()) },
                    similarFocusedId = 8,
                ),
            )
        every { vm.state } returns state
        every { vm.focusSimilar(any()) } answers {
            state.value = state.value.copy(similarFocusedId = firstArg())
        }
        composeTestRule.setContent {
            WholphinTheme { SeerrCollectionDetails(destination, viewModel = vm) }
        }
        val first = composeTestRule.onAllNodesWithText("Collection 8")[0].assertIsFocused()
        val firstLeft = first.getUnclippedBoundsInRoot().left
        first.performKeyInput { pressKey(Key.DirectionRight) }
        val second = composeTestRule.onAllNodes(hasText("Collection 9") and hasClickAction())[0].assertIsFocused()
        Assert.assertTrue(second.getUnclippedBoundsInRoot().left > firstLeft)
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
    fun watchlist_random_back_restores_random_button_without_replacing_grid() {
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
        val initial = (1..20).map(::row)
        val screen =
            MutableStateFlow(WatchlistScreenState(owner = "viewer", entries = initial, page = 1, totalPages = 50, totalResults = 990))
        val repository = mockk<WatchlistRepository>()
        every { repository.state } returns MutableStateFlow(WatchlistSnapshot(owner = "viewer"))
        val viewModel = mockk<WatchlistViewModel>(relaxed = true)
        every { viewModel.state } returns screen
        every { viewModel.repository } returns repository
        var watchlistIsTop by mutableStateOf(true)
        every { viewModel.isWatchlistTop() } answers { watchlistIsTop }
        every { viewModel.consumeRandomReturnFocus() } answers {
            screen.value = screen.value.copy(restoreRandomFocus = false)
        }
        every { viewModel.random() } answers {
            watchlistIsTop = false
            screen.value = screen.value.copy(restoreRandomFocus = true)
        }
        var exitCount = 0
        composeTestRule.setContent {
            WholphinTheme {
                BackHandler { exitCount++ }
                WatchlistPage(viewModel = viewModel)
            }
        }
        composeTestRule.onNode(hasText("Saved 1") and hasClickAction()).requestFocus().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        composeTestRule.onNodeWithContentDescription("Random").requestFocus().performClickEnter()
        composeTestRule.waitForIdle()
        Assert.assertEquals(initial, screen.value.entries)
        Assert.assertEquals(1, screen.value.firstPage)
        Assert.assertEquals(1, screen.value.page)
        io.mockk.verify(exactly = 0) { viewModel.open(any()) }
        composeTestRule.onNode(hasText("Saved 1") and hasClickAction()).requestFocus()
        composeTestRule.runOnIdle { watchlistIsTop = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        Assert.assertEquals(false, screen.value.restoreRandomFocus)
        composeTestRule.onNode(hasText("Saved 1") and hasClickAction()).requestFocus().performClickEnter()
        io.mockk.verify(exactly = 1) { viewModel.open(initial.first()) }
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithContentDescription("Random").assertIsFocused()
        Assert.assertEquals(0, exitCount)
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        Assert.assertEquals(1, exitCount)
    }
}
