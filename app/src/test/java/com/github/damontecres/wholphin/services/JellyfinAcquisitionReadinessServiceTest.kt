package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.TvRequestHttpFixture
import com.github.damontecres.wholphin.api.seerr.model.DownloadStatus
import com.github.damontecres.wholphin.api.seerr.model.DownloadStatusEpisode
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.api.seerr.requestSession
import com.github.damontecres.wholphin.data.model.AcquisitionPhase
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAcquisitionState
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrRequestAcquisition
import com.github.damontecres.wholphin.data.model.toSeerrRequestAcquisition
import com.github.damontecres.wholphin.data.model.toTvSeasonTargets
import com.github.damontecres.wholphin.test.TestTracks
import com.github.damontecres.wholphin.ui.downloads.toDownloadSections
import com.github.damontecres.wholphin.ui.main.toHomeAcquiringState
import com.github.damontecres.wholphin.ui.successQueryResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinAcquisitionReadinessServiceTest {
    private val api: ApiClient = mockk()
    private val itemsApi: ItemsApi = mockk()
    private val inventoryService: JellyfinSeriesInventoryService = mockk()
    private val service = JellyfinAcquisitionReadinessService(api, inventoryService)

    @Before
    fun setUp() {
        every { api.itemsApi } returns itemsApi
    }

    @Test
    fun ordinaryMovieAndTvReadinessNeedsSourcesNotResolutionOrItemLevelStreams() =
        runTest {
            for (width in listOf(3840, 3596, null)) {
                val source = if (width == null) videoSource(null, null).copy(mediaStreams = null) else videoSource(width, 2160)
                val item = movie(UUID.randomUUID(), source)
                coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(item.id) }) } returns
                    successQueryResult(listOf(item))
                val movieReady =
                    service
                        .enrich(listOf(movieRequest(item.id)))
                        .single()
                        .request.jellyfinReadiness
                assertTrue(movieReady.movieReady)
                assertEquals(item.id, movieReady.playableMovieId)

                val seriesId = UUID.randomUUID()
                val episode = episode(seriesId, 1, 1920).copy(mediaSources = listOf(source))
                inventoryResponse(seriesId, listOf(episode))
                val tvReady =
                    realInventoryService()
                        .enrich(listOf(tvRequest(seriesId)))
                        .single()
                        .request.jellyfinReadiness
                assertTrue(tvReady.seasonReady(1, 1))
                assertEquals(episode.id, tvReady.episodeItemIds[1]?.get(1))
            }
            coVerify(exactly = 0) {
                itemsApi.getItems(match<GetItemsRequest> { ItemFields.MEDIA_STREAMS in it.fields.orEmpty() })
            }
        }

    @Test
    fun inPlaceExternalReplacementKeepsOrdinaryRequestAndCompletionWithoutUpgradeRequest() =
        runTest {
            val id = UUID.randomUUID()
            var item = movie(id, videoSource(1920, 1080))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } answers { successQueryResult(listOf(item)) }
            val request = movieRequest(id).let { it.copy(request = it.request.copy(updatedAt = "1970-01-01T00:00:00Z")) }
            val tracker =
                SeerrAcquisitionTracker(
                    loadRequests = { listOf(request) },
                    resolveJellyfinReadiness = service::enrich,
                    sessions = MutableStateFlow(SeerrAcquisitionSession(1, 7, "fixture", 1)),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    pollIntervalMillis = 30_000L,
                    maxBackoffMillis = 300_000L,
                    currentTimeMillis = { testScheduler.currentTime },
                )
            try {
                tracker.startForeground()
                runCurrent()
                val before =
                    tracker.state.value.requests
                        .single()
                        .request
                assertTrue(before.jellyfinReadiness.movieReady)
                for (source in listOf(
                    videoSource(3840, 2160),
                    videoSource(3596, 2160),
                    videoSource(null, null).copy(mediaStreams = null),
                )) {
                    item = movie(id, source)
                    tracker.refreshNow()
                    runCurrent()
                    val after =
                        tracker.state.value.requests
                            .single()
                            .request
                    assertEquals(before.requestId, after.requestId)
                    assertFalse(after.is4k)
                    assertEquals(id, after.jellyfinReadiness.playableMovieId)
                    assertTrue(after.jellyfinReadiness.movieReady)
                    assertEquals(before.jellyfinReadySinceEpochMillis, after.jellyfinReadySinceEpochMillis)
                    assertEquals(
                        1,
                        tracker.state.value.requests
                            .toDownloadSections(testScheduler.currentTime)
                            .completed.size,
                    )
                    assertTrue(
                        tracker.state.value
                            .toAcquisitionIndex()
                            .toHomeAcquiringState()
                            .items
                            .isEmpty(),
                    )
                }
            } finally {
                tracker.stopForeground()
            }
        }

    @Test
    fun historicalFourKRequestIsTrackedWhenCurrentPublicCapabilitiesAreDisabled() =
        runTest {
            val current = MutableStateFlow<CurrentSeerr?>(requestSession(movie4k = false, tv4k = false))
            val http =
                TvRequestHttpFixture(
                    200,
                    """{"pageInfo":{"page":1,"pages":1,"results":1},"results":[{"id":91,"status":5,"type":"movie","is4k":true,"media":{"id":1,"tmdbId":10,"status":5,"status4k":5}}]}""",
                )
            val repository =
                mockk<SeerrServerRepository> {
                    every { this@mockk.current } returns current
                    every { currentUserId } returns current.map { it?.config?.id }
                }
            val wrapper = mockk<SeerrApi> { every { api } returns http.api }
            val seerr = SeerrService(wrapper, repository, mockk())
            val requests = seerr.getRequestAcquisitions()
            val id = UUID.randomUUID()
            var item = movie(id, videoSource(1920, 1080))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } answers { successQueryResult(listOf(item)) }
            val tracker =
                SeerrAcquisitionTracker(
                    loadRequests = { requests.map { it.withDiscoverItem(SeerrItemType.MOVIE, 10, "Movie", id) } },
                    resolveJellyfinReadiness = service::enrich,
                    sessions = MutableStateFlow(SeerrAcquisitionSession(1, 7, "fixture", 1)),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    pollIntervalMillis = 30_000L,
                    maxBackoffMillis = 300_000L,
                    currentTimeMillis = { testScheduler.currentTime },
                )
            try {
                assertFalse(current.value.permitsRequest(SeerrItemType.MOVIE, true))
                tracker.startForeground()
                runCurrent()
                val historical =
                    tracker.state.value.requests
                        .single()
                        .request
                assertTrue(historical.is4k)
                assertFalse(historical.jellyfinReadiness.movieReady)
                assertEquals(id, historical.jellyfinReadiness.playableMovieId)
                item = movie(id, videoSource(3840, 2160))
                tracker.refreshNow()
                runCurrent()
                val fulfilled =
                    tracker.state.value.requests
                        .single()
                        .request
                assertTrue(fulfilled.is4k)
                assertTrue(fulfilled.jellyfinReadiness.movieReady)
                assertEquals(historical.requestId, fulfilled.requestId)
                assertTrue(http.calls.isNotEmpty())
                assertTrue(http.calls.all { it.method == "GET" && it.url.encodedPath == "/api/v1/request" })
            } finally {
                tracker.stopForeground()
            }
        }

    @Test
    fun coldStartMovieUsesExplicitJellyfinIdAndValidatesPlayability() =
        runTest {
            val movieId = UUID.randomUUID()
            val request = movieRequest(movieId)
            val item =
                BaseItemDto(
                    id = movieId,
                    type = BaseItemKind.MOVIE,
                    mediaSources = listOf(mockk<MediaSourceInfo>()),
                )
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(movieId) }) } returns
                successQueryResult(listOf(item))

            val enriched = service.enrich(listOf(request)).single()

            assertEquals(movieId, enriched.request.jellyfinReadiness.movieItemId)
            assertTrue(enriched.request.jellyfinReadiness.movieReady)
        }

    @Test
    fun coldStartTvUsesExplicitJellyfinIdAndRestoresExactSeasonIdentity() =
        runTest {
            val seriesId = UUID.randomUUID()
            val seasonId = UUID.randomUUID()
            val episodeId = UUID.randomUUID()
            val request = tvRequest(seriesId)
            val item = BaseItemDto(id = seriesId, type = BaseItemKind.SERIES)
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(seriesId) }) } returns
                successQueryResult(listOf(item))
            coEvery { inventoryService.get(seriesId) } returns
                JellyfinSeriesInventory(
                    seasonItemIds = mapOf(1 to seasonId),
                    playableEpisodeItemIds = mapOf(1 to mapOf(1 to episodeId)),
                )

            val enriched = service.enrich(listOf(request)).single()

            assertEquals(seriesId, enriched.request.jellyfinReadiness.seriesItemId)
            assertEquals(seasonId, enriched.request.jellyfinReadiness.seasonItemIds[1])
            assertEquals(
                episodeId,
                enriched.request.jellyfinReadiness.episodeItemIds[1]
                    ?.get(1),
            )
            assertTrue(enriched.request.jellyfinReadiness.seasonReady(1, 1))
        }

    @Test
    fun unresolvedExplicitIdDoesNotFakeReadinessFromSeerrAvailability() =
        runTest {
            val movieId = UUID.randomUUID()
            val request = movieRequest(movieId)
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult()

            val enriched = service.enrich(listOf(request)).single()

            assertFalse(enriched.request.jellyfinReadiness.movieReady)
        }

    @Test
    fun missingSeerrJellyfinIdFallsBackToIndependentJellyfinDiscovery() =
        runTest {
            val movieId = UUID.randomUUID()
            val request = movieRequest(jellyfinItemId = null, availability = SeerrAvailability.PROCESSING)
            val item = playableMovie(movieId)
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) } returns
                successQueryResult(listOf(item))

            val enriched = service.enrich(listOf(request)).single()

            assertEquals(movieId, enriched.request.jellyfinReadiness.movieItemId)
            coVerify(exactly = 1) { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) }
        }

    @Test
    fun staleSeerrJellyfinIdFallsBackToIndependentJellyfinDiscovery() =
        runTest {
            val staleId = UUID.randomUUID()
            val movieId = UUID.randomUUID()
            val request = movieRequest(jellyfinItemId = staleId, availability = SeerrAvailability.PROCESSING)
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(staleId) }) } returns successQueryResult()
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) } returns
                successQueryResult(listOf(playableMovie(movieId)))

            val enriched = service.enrich(listOf(request)).single()

            assertEquals(movieId, enriched.request.jellyfinReadiness.movieItemId)
            coVerify(exactly = 1) { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(staleId) }) }
            coVerify(exactly = 1) { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) }
        }

    @Test
    fun standardMovieDoesNotFulfill4kEvenWhenSeerrReportsAvailable() =
        runTest {
            val movieId = UUID.randomUUID()
            val standard =
                playableMovie(movieId).copy(
                    mediaSources = listOf(videoSource(1920, 1080)),
                )
            val request = movieRequest(movieId).let { it.copy(request = it.request.copy(is4k = true)) }
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(standard))

            assertFalse(
                service
                    .enrich(listOf(request))
                    .single()
                    .request.jellyfinReadiness.movieReady,
            )
        }

    @Test
    fun standardEpisodesDoNotFulfill4kSeason() =
        runTest {
            val seriesId = UUID.randomUUID()
            val request = tvRequest(seriesId).let { it.copy(request = it.request.copy(is4k = true)) }
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns
                successQueryResult(listOf(BaseItemDto(id = seriesId, type = BaseItemKind.SERIES)))
            coEvery { inventoryService.get(seriesId) } returns
                JellyfinSeriesInventory(mapOf(1 to UUID.randomUUID()), mapOf(1 to mapOf(1 to UUID.randomUUID())))

            assertFalse(
                service
                    .enrich(listOf(request))
                    .single()
                    .request.jellyfinReadiness
                    .seasonReady(1, 1),
            )
        }

    @Test
    fun separate4kMovieVersionIsFoundWithoutLosingStandardPlayback() =
        runTest {
            val standard = movie(UUID.randomUUID(), videoSource(1920, 1080))
            val uhd = movie(UUID.randomUUID(), videoSource(3840, 1600))
            val unrelated = uhd.copy(id = UUID.randomUUID(), providerIds = mapOf("Tmdb" to "999"))
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(standard.id) }) } returns
                successQueryResult(listOf(standard))
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) } returns
                successQueryResult(listOf(unrelated, standard, uhd))

            val request = movieRequest(standard.id).as4k()
            val readiness =
                service
                    .enrich(listOf(request))
                    .single()
                    .request.jellyfinReadiness

            assertEquals(uhd.id, readiness.movieItemId)
            assertEquals(standard.id, readiness.playableMovieId)
            coVerify(exactly = 1) {
                itemsApi.getItems(
                    match<GetItemsRequest> {
                        it.searchTerm == "Movie" && it.limit == 20 &&
                            ItemFields.MEDIA_SOURCES in it.fields.orEmpty() && ItemFields.MEDIA_STREAMS !in it.fields.orEmpty()
                    },
                )
            }
        }

    @Test
    fun groupedMovieSourcesIndependentlyFulfillBothQualities() =
        runTest {
            val item = movie(UUID.randomUUID(), videoSource(1920, 1080), videoSource(4096, 2160))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(item))
            val standard = movieRequest(item.id)
            val enriched = service.enrich(listOf(standard, standard.as4k()))

            assertTrue(enriched.all { it.request.jellyfinReadiness.movieReady })
            assertTrue(enriched.all { it.request.jellyfinReadiness.movieItemId == item.id })
        }

    @Test
    fun missingOrNonUhdDimensionsNeverUseNamesOrItemLevelStreamsAsProof() =
        runTest {
            for (source in listOf(videoSource(null, 2160), videoSource(3840, null), videoSource(2048, 1080), videoSource(3840, 0))) {
                val item =
                    movie(UUID.randomUUID(), source).copy(
                        name = "Movie 4K HDR",
                        mediaStreams = videoSource(3840, 2160).mediaStreams,
                    )
                coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(item))

                val enriched = service.enrich(listOf(movieRequest(item.id), movieRequest(item.id).as4k()))
                assertTrue(enriched[0].request.jellyfinReadiness.movieReady)
                assertFalse(enriched[1].request.jellyfinReadiness.movieReady)
                assertEquals(item.id, enriched[1].request.jellyfinReadiness.playableMovieId)
            }
        }

    @Test
    fun nonVideoAndConflictingStreamsDoNotProve4kSource() =
        runTest {
            val source = videoSource(3840, 2160)
            val video = source.mediaStreams!!.single()
            for (streams in listOf(
                emptyList(),
                listOf(video.copy(type = MediaStreamType.AUDIO)),
                listOf(video, video.copy(width = 1920)),
            )) {
                val item = movie(UUID.randomUUID(), source.copy(mediaStreams = streams))
                coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(item))
                assertFalse(
                    service
                        .enrich(listOf(movieRequest(item.id).as4k()))
                        .single()
                        .request.jellyfinReadiness.movieReady,
                )
            }
        }

    @Test
    fun newlyVisible4kVersionCompletesWhileSeerrStillProcesses() =
        runTest {
            val id = UUID.randomUUID()
            var item = movie(id, videoSource(1920, 1080))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } answers { successQueryResult(listOf(item)) }
            val pending = service.enrich(listOf(movieRequest(id, SeerrAvailability.PROCESSING).as4k())).single()
            assertFalse(pending.request.jellyfinReadiness.movieReady)
            assertTrue(pending.needsJellyfinReadinessCheck())

            item = item.copy(mediaSources = item.mediaSources!! + videoSource(3840, 2160))
            val ready = service.enrich(listOf(pending)).single()

            assertEquals(SeerrAvailability.PROCESSING, ready.request.availability)
            assertTrue(ready.request.jellyfinReadiness.movieReady)
            assertFalse(ready.needsJellyfinReadinessCheck())
        }

    @Test
    fun conflictingExplicitProviderIdentityCannotFulfillRequest() =
        runTest {
            val item = movie(UUID.randomUUID(), videoSource(3840, 2160)).copy(providerIds = mapOf("Tmdb" to "999"))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(item))

            assertFalse(
                service
                    .enrich(listOf(movieRequest(item.id).as4k()))
                    .single()
                    .request.jellyfinReadiness.movieReady,
            )
        }

    @Test
    fun mixedSeasonUsesOnlyQualityVerifiedEpisodesButPreservesStandardInventory() =
        runTest {
            val seriesId = UUID.randomUUID()
            val episodes = listOf(episode(seriesId, 1, 1920), episode(seriesId, 2, 3840), episode(seriesId, 3, 3840))
            inventoryResponse(seriesId, episodes)
            val standard = tvRequest(seriesId, 3)
            val results = realInventoryService().enrich(listOf(standard, standard.as4k()))

            assertTrue(results[0].request.jellyfinReadiness.seasonReady(1, 3))
            assertEquals(
                setOf(2, 3),
                results[1]
                    .request.jellyfinReadiness.episodeItemIds[1]
                    ?.keys,
            )
            assertFalse(results[1].request.jellyfinReadiness.seasonReady(1, 3))
            assertEquals(2, results[1].toTvSeasonTargets().single().playableEpisodeCount)
        }

    @Test
    fun multipleVersionsOfAnEpisodeDoNotOvercountOrHideVerifiedVersion() =
        runTest {
            val seriesId = UUID.randomUUID()
            val uhd = episode(seriesId, 1, 3840)
            inventoryResponse(seriesId, listOf(uhd, uhd.copy(id = UUID.randomUUID()), episode(seriesId, 1, 1920)))

            val partial = realInventoryService().enrich(listOf(tvRequest(seriesId, 2).as4k())).single()
            assertEquals(
                1,
                partial.request.jellyfinReadiness.episodeItemIds[1]
                    ?.size,
            )
            assertFalse(partial.request.jellyfinReadiness.seasonReady(1, 2))

            inventoryResponse(seriesId, listOf(uhd, episode(seriesId, 2, 3840)))
            assertTrue(
                realInventoryService()
                    .enrich(listOf(partial))
                    .single()
                    .request.jellyfinReadiness
                    .seasonReady(1, 2),
            )
        }

    @Test
    fun missingOrConflictingEpisodeIdentityDoesNotFillSeasonGaps() =
        runTest {
            val seriesId = UUID.randomUUID()
            val item = episode(seriesId, 1, 3840)
            inventoryResponse(
                seriesId,
                listOf(
                    item.copy(indexNumber = null),
                    item.copy(parentIndexNumber = null),
                    item.copy(indexNumber = 0),
                    item.copy(seriesId = UUID.randomUUID()),
                    item.copy(indexNumber = 2),
                ),
            )

            val result = realInventoryService().enrich(listOf(tvRequest(seriesId, 1).as4k())).single()
            assertFalse(result.request.jellyfinReadiness.seasonReady(1, 1))
            assertTrue(
                result.request.jellyfinReadiness.episodeItemIds[1]
                    .orEmpty()
                    .isEmpty(),
            )
        }

    @Test
    fun temporaryEpisodeOmissionPreservesPriorQualityProofDuringSameGeneration() =
        runTest {
            val seriesId = UUID.randomUUID()
            val first = episode(seriesId, 1, 3840)
            inventoryResponse(seriesId, listOf(first))
            val partial = realInventoryService().enrich(listOf(tvRequest(seriesId, 2).as4k())).single()
            inventoryResponse(seriesId, listOf(episode(seriesId, 2, 3840)))

            val complete = realInventoryService().enrich(listOf(partial)).single()
            assertTrue(complete.request.jellyfinReadiness.seasonReady(1, 2))
            assertEquals(
                first.id,
                complete.request.jellyfinReadiness.episodeItemIds[1]
                    ?.get(1),
            )
        }

    @Test
    fun changedExpectedCountCannotTurnNoncontiguous4kEpisodesIntoCompletion() =
        runTest {
            val seriesId = UUID.randomUUID()
            inventoryResponse(seriesId, listOf(episode(seriesId, 2, 3840), episode(seriesId, 3, 3840)))
            val partial = realInventoryService().enrich(listOf(tvRequest(seriesId, 3).as4k())).single()
            val changed = partial.copy(request = partial.request.copy(seasonEpisodeCounts = mapOf(1 to 2)))

            assertFalse(changed.request.jellyfinReadiness.seasonReady(1, 2))
            assertTrue(changed.needsJellyfinReadinessCheck())
        }

    @Test
    fun differentJellyfinSeriesCannotCombineRetainedEpisodeProof() =
        runTest {
            val firstSeries = UUID.randomUUID()
            val otherSeries = UUID.randomUUID()
            inventoryResponse(firstSeries, listOf(episode(firstSeries, 1, 3840)))
            val partial = realInventoryService().enrich(listOf(tvRequest(firstSeries, 2).as4k())).single()
            inventoryResponse(otherSeries, listOf(episode(otherSeries, 2, 3840)))
            val changed =
                partial.copy(
                    request = partial.request.copy(discoverItem = partial.request.discoverItem!!.copy(jellyfinItemId = otherSeries)),
                )

            val result = realInventoryService().enrich(listOf(changed)).single()
            assertEquals(otherSeries, result.request.jellyfinReadiness.seriesItemId)
            assertEquals(
                setOf(2),
                result.request.jellyfinReadiness.episodeItemIds[1]
                    ?.keys,
            )
            assertFalse(result.request.jellyfinReadiness.seasonReady(1, 2))
        }

    @Test
    fun alternateSearchFailureDoesNotEraseVerifiedStandardPlayback() =
        runTest {
            val item = movie(UUID.randomUUID(), videoSource(1920, 1080))
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(item.id) }) } returns successQueryResult(listOf(item))
            coEvery { itemsApi.getItems(match<GetItemsRequest> { it.searchTerm == "Movie" }) } throws
                IllegalStateException("fixture unavailable")

            val readiness =
                service
                    .enrich(listOf(movieRequest(item.id).as4k()))
                    .single()
                    .request.jellyfinReadiness
            assertFalse(readiness.movieReady)
            assertEquals(item.id, readiness.playableMovieId)
        }

    @Test
    fun unavailableJellyfinRetainsLastGoodSnapshotWithoutInventingProof() =
        runTest {
            val request = movieRequest(UUID.randomUUID()).as4k()
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } throws IllegalStateException("fixture unavailable")

            assertEquals(request, service.enrich(listOf(request)).single())
        }

    @Test
    fun cancelledJellyfinRefreshPropagatesCancellation() =
        runTest {
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } throws CancellationException("session replaced")
            try {
                service.enrich(listOf(movieRequest(UUID.randomUUID()).as4k()))
                fail("Cancellation must not be a successful old-session observation")
            } catch (_: CancellationException) {
                // Expected: tracker/session coroutine owns cancellation.
            }
        }

    @Test
    fun trackerKeeps4kOutstandingAcrossTransferImportFailureAndQueueDisappearanceWhileStandardPlays() =
        runTest {
            val id = UUID.randomUUID()
            var item = movie(id, videoSource(1920, 1080))
            coEvery { itemsApi.getItems(any<GetItemsRequest>()) } answers { successQueryResult(listOf(item)) }
            val sessions = MutableStateFlow<SeerrAcquisitionSession?>(SeerrAcquisitionSession(1, 7, "fixture", 1))
            val standard = movieRequest(id).let { it.copy(request = it.request.copy(updatedAt = "1970-01-01T00:00:00Z")) }

            fun upgrade(
                phase: String?,
                available: Boolean = false,
                transfer: String = "4k-transfer",
            ) = MediaRequest(
                id = 2,
                status = 2,
                type = "movie",
                is4k = true,
                updatedAt = "1970-01-01T00:00:00Z",
                media =
                    MediaInfo(
                        id = 1,
                        tmdbId = 10,
                        status = 5,
                        status4k = if (available) 5 else 3,
                        downloadStatus4k =
                            phase?.let {
                                listOf(
                                    DownloadStatus(
                                        downloadId = transfer,
                                        status = if (it == "downloading") "downloading" else "completed",
                                        propertySize = 100.0,
                                        sizeLeft = if (it == "downloading") 50.0 else 0.0,
                                        acquisitionPhase = it,
                                    ),
                                )
                            },
                    ),
            ).toSeerrRequestAcquisition().withDiscoverItem(SeerrItemType.MOVIE, 10, "Movie", id)
            var current = upgrade("downloading")
            val tracker =
                SeerrAcquisitionTracker(
                    loadRequests = { listOf(standard, current) },
                    resolveJellyfinReadiness = service::enrich,
                    sessions = sessions,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    pollIntervalMillis = 30_000L,
                    maxBackoffMillis = 300_000L,
                    currentTimeMillis = { testScheduler.currentTime },
                )
            try {
                tracker.startForeground()
                for (phase in listOf("downloading", "importing", "importBlocked", "failed")) {
                    current = upgrade(phase)
                    tracker.refreshNow()
                    runCurrent()
                    val requests = tracker.state.value.requests
                    assertTrue(
                        requests
                            .first()
                            .request.jellyfinReadiness.movieReady,
                    )
                    assertFalse(
                        requests
                            .last()
                            .request.jellyfinReadiness.movieReady,
                    )
                    val sections = requests.toDownloadSections(testScheduler.currentTime)
                    assertEquals(1, sections.completed.size)
                    val outstanding = (sections.active + sections.processing).single()
                    assertFalse(outstanding.completed)
                    if (phase == "importBlocked") assertEquals(AcquisitionPhase.IMPORT_BLOCKED, outstanding.lifecycle?.phase)
                    if (phase == "failed") assertEquals(AcquisitionPhase.FAILED, outstanding.lifecycle?.phase)
                    val home =
                        tracker.state.value
                            .toAcquisitionIndex()
                            .toHomeAcquiringState()
                            .items
                            .single()
                    assertEquals(id, home.verifiedJellyfinItemId)
                }

                // Even Seerr Available plus queue disappearance cannot substitute for UHD evidence.
                current = upgrade(null, available = true)
                repeat(4) {
                    tracker.refreshNow()
                    runCurrent()
                }
                assertFalse(
                    tracker.state.value.requests
                        .last()
                        .request.jellyfinReadiness.movieReady,
                )
                assertEquals(
                    1,
                    tracker.state.value
                        .toAcquisitionIndex()
                        .toHomeAcquiringState()
                        .items.size,
                )
                assertEquals(
                    1,
                    tracker.state.value.requests
                        .toDownloadSections(testScheduler.currentTime)
                        .processing.size,
                )

                item = movie(id, videoSource(1920, 1080), videoSource(3840, 2160))
                tracker.refreshNow()
                runCurrent()
                assertTrue(
                    tracker.state.value.requests
                        .all { it.request.jellyfinReadiness.movieReady },
                )
                assertEquals(
                    2,
                    tracker.state.value.requests
                        .toDownloadSections(testScheduler.currentTime)
                        .completed.size,
                )
                assertTrue(
                    tracker.state.value
                        .toAcquisitionIndex()
                        .toHomeAcquiringState()
                        .items
                        .isEmpty(),
                )

                // A distinct transfer must not inherit terminal proof or completion time.
                item = movie(id, videoSource(1920, 1080))
                current = upgrade("downloading", transfer = "replacement-4k")
                tracker.refreshNow()
                runCurrent()
                assertFalse(
                    tracker.state.value.requests
                        .last()
                        .request.jellyfinReadiness.movieReady,
                )
                assertEquals(
                    null,
                    tracker.state.value.requests
                        .last()
                        .request.jellyfinReadySinceEpochMillis,
                )
                item = movie(id, videoSource(3840, 2160))
                tracker.refreshNow()
                runCurrent()
                assertTrue(
                    tracker.state.value.requests
                        .last()
                        .request.jellyfinReadiness.movieReady,
                )
                item = movie(id, videoSource(1920, 1080))
                sessions.value = SeerrAcquisitionSession(1, 7, "another-server", 2)
                runCurrent()
                assertFalse(
                    tracker.state.value.requests
                        .last()
                        .request.jellyfinReadiness.movieReady,
                )
            } finally {
                tracker.stopForeground()
            }
        }

    @Test
    fun seasonPackAt100PercentRequiresEveryExpected4kEpisodeBeforeLedgerCompletion() =
        runTest {
            val seriesId = UUID.randomUUID()
            val seasonPack =
                MediaRequest(
                    id = 2,
                    status = 5,
                    type = "tv",
                    is4k = true,
                    seasons = listOf(Season(seasonNumber = 1)),
                    media =
                        MediaInfo(
                            id = 2,
                            tmdbId = 20,
                            status = 5,
                            status4k = 5,
                            seasons = listOf(Season(seasonNumber = 1, status = 5, status4k = 5)),
                            downloadStatus4k =
                                listOf(
                                    DownloadStatus(
                                        downloadId = "4k-season-pack",
                                        propertySize = 300.0,
                                        sizeLeft = 0.0,
                                        status = "completed",
                                        acquisitionPhase = "importing",
                                        episode = DownloadStatusEpisode(seasonNumber = 1),
                                    ),
                                ),
                        ),
                ).toSeerrRequestAcquisition().withDiscoverItem(SeerrItemType.TV, 20, "Series", seriesId).let {
                    it.copy(request = it.request.copy(seasonEpisodeCounts = mapOf(1 to 3)))
                }
            val ledger = SeerrAcquisitionLedger()
            ledger.reconcile(listOf(seasonPack))
            inventoryResponse(seriesId, listOf(episode(seriesId, 1, 3840), episode(seriesId, 2, 1920), episode(seriesId, 3, 3840)))
            val partial = realInventoryService().enrich(listOf(seasonPack)).single()
            assertFalse(partial.toTvSeasonTargets().single().jellyfinReady)
            assertTrue(ledger.reconcile(listOf(partial)).single().acquisition is SeerrAcquisitionState.Tv)

            inventoryResponse(seriesId, (1..3).map { episode(seriesId, it, 3840) })
            val ready = realInventoryService().enrich(listOf(partial)).single()
            assertTrue(ready.toTvSeasonTargets().single().jellyfinReady)
            val cleared = ledger.reconcile(listOf(ready)).single().acquisition as SeerrAcquisitionState.Tv
            assertTrue(cleared.seasons.isEmpty())
        }

    private fun realInventoryService() = JellyfinAcquisitionReadinessService(api, JellyfinSeriesInventoryService(api))

    private fun inventoryResponse(
        seriesId: UUID,
        episodes: List<BaseItemDto>,
    ) {
        coEvery { itemsApi.getItems(match<GetItemsRequest> { it.ids == listOf(seriesId) }) } returns
            successQueryResult(listOf(BaseItemDto(id = seriesId, type = BaseItemKind.SERIES)))
        coEvery { itemsApi.getItems(match<GetItemsRequest> { it.parentId == seriesId }) } returns
            successQueryResult(listOf(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.SEASON, indexNumber = 1)) + episodes)
    }

    private fun movie(
        id: UUID,
        vararg sources: MediaSourceInfo,
    ) = playableMovie(id).copy(mediaSources = sources.toList())

    private fun episode(
        seriesId: UUID,
        number: Int,
        width: Int,
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.EPISODE,
        seriesId = seriesId,
        parentIndexNumber = 1,
        indexNumber = number,
        mediaSources = listOf(videoSource(width, if (width >= 3840) 2160 else 1080)),
    )

    private fun com.github.damontecres.wholphin.data.model.SeerrRequestAcquisition.as4k() = copy(request = request.copy(is4k = true))

    private fun videoSource(
        width: Int?,
        height: Int?,
    ): MediaSourceInfo {
        val source = TestTracks.Builder().addVideo().buildMediaSourceInfo()
        return source.copy(mediaStreams = source.mediaStreams!!.map { it.copy(width = width, height = height) })
    }

    private fun movieRequest(
        jellyfinItemId: UUID?,
        availability: SeerrAvailability = SeerrAvailability.AVAILABLE,
    ) = MediaRequest(
        id = 1,
        status = 5,
        type = "movie",
        media = MediaInfo(id = 1, tmdbId = 10, status = availability.status),
    ).toSeerrRequestAcquisition().withDiscoverItem(SeerrItemType.MOVIE, 10, "Movie", jellyfinItemId)

    private fun playableMovie(movieId: UUID) =
        BaseItemDto(
            id = movieId,
            type = BaseItemKind.MOVIE,
            providerIds = mapOf("Tmdb" to "10"),
            mediaSources = listOf(mockk<MediaSourceInfo>()),
        )

    private fun tvRequest(
        jellyfinItemId: UUID,
        expectedEpisodes: Int = 1,
    ) = MediaRequest(
        id = 2,
        status = 5,
        type = "tv",
        seasons = listOf(Season(seasonNumber = 1, status = SeerrAvailability.AVAILABLE.status)),
        media = MediaInfo(id = 2, tmdbId = 20, status = SeerrAvailability.PARTIALLY_AVAILABLE.status),
    ).toSeerrRequestAcquisition().let { acquisition ->
        val withDiscoverItem = acquisition.withDiscoverItem(SeerrItemType.TV, 20, "Series", jellyfinItemId)
        withDiscoverItem.copy(
            request = withDiscoverItem.request.copy(seasonEpisodeCounts = mapOf(1 to expectedEpisodes)),
        )
    }

    private fun com.github.damontecres.wholphin.data.model.SeerrRequestAcquisition.withDiscoverItem(
        type: SeerrItemType,
        tmdbId: Int,
        title: String,
        jellyfinItemId: UUID?,
    ) = copy(
        request =
            request.copy(
                discoverItem =
                    DiscoverItem(
                        id = tmdbId,
                        type = type,
                        title = title,
                        subtitle = null,
                        overview = null,
                        availability = SeerrAvailability.AVAILABLE,
                        releaseDate = null,
                        posterUrl = null,
                        backDropUrl = null,
                        logoUrl = null,
                        jellyfinItemId = jellyfinItemId,
                    ),
                seasonEpisodeCounts = request.seasonEpisodeCounts,
            ),
    )
}
