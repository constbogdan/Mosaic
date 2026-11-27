package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.SeerrApiClient
import com.github.damontecres.wholphin.api.seerr.infrastructure.ServerException
import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.api.seerr.model.SimilarCollectionsRead
import com.github.damontecres.wholphin.api.seerr.model.User
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.ui.collections.SeerrCollectionDetailsViewModel
import com.github.damontecres.wholphin.ui.collections.verifiedSimilarIds
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SimilarCollectionsTest {
    private fun read(
        ids: List<Int> = emptyList(),
        active: Boolean = false,
        completed: Int = 0,
        incomplete: Int = 0,
    ) = SimilarCollectionsRead(ids, completed, incomplete, active)

    private class Backend : CollectionsBackend {
        var reads: List<SimilarCollectionsRead> = emptyList()
        var readCalls = 0
        var triggerCalls = 0
        var triggerStarted = true
        var triggerFailure: Exception? = null
        var triggerToken: UUID? = null
        var triggerOverride: (suspend () -> Boolean)? = null
        var readFailure: Exception? = null
        var readOverride: (suspend (Int) -> SimilarCollectionsRead)? = null
        var detailOverride: (suspend () -> CollectionDetailsData)? = null
        var metadata: suspend (
            Int,
        ) -> CollectionDetailsData = { id -> CollectionDetailsData(id, "Related $id", null, null, null, emptyList()) }
        val requestedMetadata = mutableListOf<Int>()

        override suspend fun page(
            skip: Int,
            filter: CollectionsFilter,
            sort: CollectionsSort,
        ): CollectionsPageData = error("unused")

        override suspend fun detail(id: Int) =
            detailOverride?.invoke() ?: CollectionDetailsData(id, "Origin", null, null, null, emptyList())

        override suspend fun similarDetail(id: Int): CollectionDetailsData {
            requestedMetadata += id
            return metadata(id)
        }

        override suspend fun readSimilar(id: Int): SimilarCollectionsRead {
            readCalls++
            readFailure?.let { throw it }
            readOverride?.let { return it(readCalls) }
            return reads.getOrElse(readCalls - 1) { reads.last() }
        }

        override suspend fun triggerSimilar(
            id: Int,
            visitToken: UUID,
        ): Boolean {
            triggerCalls++
            triggerToken = visitToken
            triggerFailure?.let { throw it }
            triggerOverride?.let { return it() }
            return triggerStarted
        }
    }

    private fun session(
        owner: String,
        backend: CollectionsBackend,
    ) = CollectionsSession(owner, SeerrApiClient("http://seerr.invalid/api/v1", null, OkHttpClient()), backend, { true })

    private fun viewModel(
        sessions: MutableStateFlow<CollectionsSession?>,
        navigation: NavigationManager = mockk(relaxed = true),
    ): SeerrCollectionDetailsViewModel {
        val source = mockk<CollectionsSessionSource>()
        every { source.sessions } returns sessions
        return SeerrCollectionDetailsViewModel(source, navigation, mockk(relaxed = true))
    }

    @Test fun verifiedIdsPreserveServerOrderWithoutDuplicatesOrOrigin() {
        assertEquals(listOf(4, 3, 12), verifiedSimilarIds(7, listOf(4, 7, 0, 3, 4, -1, 12)))
        assertEquals((1..24).toList(), verifiedSimilarIds(100, (1..24).toList()))
    }

    @Test fun generatedContractUsesOneAuthenticatedReadAndOneUuidTrigger() =
        runTest {
            val requests = mutableListOf<Pair<String, String>>()
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        requests += request.method to request.url.encodedPath
                        val body =
                            if (request.method == "GET") {
                                """{"relatedCollectionIds":[528,5547],"completedSeedCount":3,"incompleteSeedCount":0,"discoveryActive":false}"""
                            } else {
                                val postBody = okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
                                assertTrue(postBody.contains("\"visitToken\""))
                                """{"started":false}"""
                            }
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(if (request.method == "GET") 200 else 202)
                            .message("fixture")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val current =
                CurrentSeerr(
                    SeerrServer(1, "http://seerr.invalid"),
                    SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "admin", null, null),
                    User(id = 7),
                    PublicSettings(),
                )
            val client = SeerrApiClient("http://seerr.invalid/api/v1", "fixture-key", http)
            assertTrue(client.hasValidCredentials)
            val backend = SeerrCollectionsBackend(client, current)
            assertEquals(listOf(528, 5547), backend.readSimilar(7).relatedCollectionIds)
            assertFalse(backend.triggerSimilar(7, UUID.randomUUID()))
            assertEquals(
                listOf("GET" to "/api/v1/collection/7/similar", "POST" to "/api/v1/collection/7/similar"),
                requests,
            )
        }

    @Test fun persistenceRead500IsAnErrorNotAnEmptyState() =
        runTest {
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("fixture")
                            .body("""{"message":"Unable to read Similar Collections"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val current =
                CurrentSeerr(
                    SeerrServer(1, "http://seerr.invalid"),
                    SeerrUser(1, 1, SeerrAuthMethod.JELLYFIN, "admin", null, null),
                    User(id = 7),
                    PublicSettings(),
                )
            val backend = SeerrCollectionsBackend(SeerrApiClient("http://seerr.invalid/api/v1", "fixture-key", http), current)
            val failure = runCatching { backend.readSimilar(7) }.exceptionOrNull()
            assertTrue(failure is ServerException)
            assertEquals(500, (failure as ServerException).statusCode)
        }

    @Test fun collectionDetailsReadSimilarReadAndTriggerStartIndependently() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val detailGate = CompletableDeferred<CollectionDetailsData>()
                val readGate = CompletableDeferred<SimilarCollectionsRead>()
                val triggerGate = CompletableDeferred<Boolean>()
                var detailCalls = 0
                val backend =
                    Backend().apply {
                        detailOverride = {
                            detailCalls++
                            detailGate.await()
                        }
                        readOverride = { call -> if (call == 1) readGate.await() else read() }
                        triggerOverride = { triggerGate.await() }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                assertEquals(1, detailCalls)
                assertEquals(1, backend.readCalls)
                assertEquals(1, backend.triggerCalls)
                assertTrue(vm.state.value.loading)
                assertTrue(vm.state.value.similarLoading)

                readGate.complete(read(listOf(3)))
                runCurrent()
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertTrue(vm.state.value.loading)
                detailGate.complete(CollectionDetailsData(7, "Origin", null, null, null, emptyList()))
                runCurrent()
                assertEquals(
                    "Origin",
                    vm.state.value.details
                        ?.name,
                )
                assertTrue(vm.state.value.similarLoading)
                triggerGate.complete(false)
                vm.onScreenHidden()
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun inactiveReadBeforeTriggerClaimDoesNotStopFastObservation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val triggerGate = CompletableDeferred<Boolean>()
                val backend =
                    Backend().apply {
                        reads = listOf(read())
                        triggerOverride = { triggerGate.await() }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                assertEquals(1, backend.readCalls)
                advanceTimeBy(499)
                runCurrent()
                assertEquals(1, backend.readCalls)
                advanceTimeBy(1)
                runCurrent()
                assertEquals(2, backend.readCalls)
                advanceTimeBy(1_500)
                runCurrent()
                assertTrue(vm.state.value.similarLoading)
                triggerGate.complete(true)
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertEquals(1, backend.triggerCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun fastTriggerCanFinishBeforePersistedReadWithoutHidingCards() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val readGate = CompletableDeferred<SimilarCollectionsRead>()
                val backend =
                    Backend().apply {
                        readOverride = { call -> if (call == 1) readGate.await() else read(listOf(3)) }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                assertEquals(1, backend.triggerCalls)
                assertEquals(1, backend.readCalls)
                assertTrue(vm.state.value.similarLoading)

                readGate.complete(read(listOf(3)))
                runCurrent()
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
                advanceTimeBy(1_000)
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun slowSimilarGetNeverOverlapsAnotherGet() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val slowRead = CompletableDeferred<SimilarCollectionsRead>()
                var activeReads = 0
                var maximumReads = 0
                val backend =
                    Backend().apply {
                        readOverride = { call ->
                            activeReads++
                            maximumReads = maxOf(maximumReads, activeReads)
                            try {
                                if (call == 2) slowRead.await() else read(active = call == 1)
                            } finally {
                                activeReads--
                            }
                        }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(2, backend.readCalls)
                assertEquals(1, maximumReads)
                slowRead.complete(read())
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertEquals(1, maximumReads)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun activeDiscoveryObservationEndsWithinFiniteWindow() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = Backend().apply { reads = listOf(read(active = true)) }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(24_000)
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertTrue(backend.readCalls <= 50)
                assertEquals(1, backend.triggerCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun persistedAndNewIdsRenderProgressivelyInServerOrder() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val slow = CompletableDeferred<CollectionDetailsData>()
                val backend =
                    Backend().apply {
                        reads =
                            listOf(
                                read(listOf(3), true),
                                read(listOf(3, 4), true),
                                read(listOf(3, 4), false),
                                read(listOf(3, 4, 5), false),
                            )
                        metadata =
                            { id -> if (id == 3) slow.await() else CollectionDetailsData(id, "Fourth", null, null, null, emptyList()) }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                val token = UUID.randomUUID().toString()
                vm.show(7, "one", token)
                runCurrent()
                assertEquals(listOf(3), vm.state.value.similarIds)
                assertTrue(vm.state.value.similarLoading)
                assertEquals(1, backend.triggerCalls)
                assertEquals(UUID.fromString(token), backend.triggerToken)
                advanceTimeBy(500)
                runCurrent()
                assertEquals(
                    listOf(4),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertEquals(listOf(3, 4), vm.state.value.similarIds)
                slow.complete(CollectionDetailsData(3, "Third", null, null, null, emptyList()))
                runCurrent()
                assertEquals(
                    listOf(3, 4),
                    vm.state.value.similar
                        .map { it.id },
                )
                advanceTimeBy(500)
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertEquals(4, backend.readCalls) // Initial, two polls, final boundary read.
                assertEquals(
                    listOf(3, 4, 5),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertEquals(listOf(3, 4, 5), backend.requestedMetadata)
                vm.retry() // Ordinary full-detail refresh does not retrigger discovery or metadata.
                runCurrent()
                assertEquals(1, backend.triggerCalls)
                assertEquals(listOf(3, 4, 5), backend.requestedMetadata)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun inactiveAndEmptyAreValidButStillGetFinalRefresh() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    Backend().apply {
                        reads = listOf(read(), read(), read(), read())
                        triggerStarted = false
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(500)
                runCurrent()
                assertEquals(3, backend.readCalls)
                assertEquals(1, backend.triggerCalls)
                assertFalse(vm.state.value.similarLoading)
                assertTrue(
                    vm.state.value.similar
                        .isEmpty(),
                )
                assertFalse(vm.state.value.similarReadError)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun dozensOfRelatedCollectionsUseOnlyThreeConcurrentMetadataRequests() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<Unit>()
                var active = 0
                var maximum = 0
                val backend =
                    Backend().apply {
                        reads = listOf(read((1..24).toList()))
                        metadata = { id ->
                            active++
                            maximum = maxOf(maximum, active)
                            gate.await()
                            active--
                            CollectionDetailsData(id, "Related $id", null, null, null, emptyList())
                        }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(100, "one", UUID.randomUUID().toString())
                runCurrent()
                assertEquals(3, active)
                assertEquals(3, backend.requestedMetadata.size)
                gate.complete(Unit)
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals(3, maximum)
                assertEquals(
                    (1..24).toList(),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertEquals(24, backend.requestedMetadata.distinct().size)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun removedRelationshipDropsCardAndFocusedIdentity() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    Backend().apply {
                        reads = listOf(read(listOf(3, 4), true), read(listOf(3), false))
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                vm.focusSimilar(4)
                assertEquals(4, vm.state.value.similarFocusedId)
                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertEquals(null, vm.state.value.similarFocusedId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun failedMetadataDoesNotBlockOtherCardsOrRetryOnEveryPoll() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    Backend().apply {
                        reads = listOf(read(listOf(3, 4)))
                        metadata = { id ->
                            if (id == 3) throw IOException("metadata unavailable")
                            CollectionDetailsData(id, "Fourth", null, null, null, emptyList())
                        }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals(
                    listOf(4),
                    vm.state.value.similar
                        .map { it.id },
                )
                assertEquals(listOf(3, 4), backend.requestedMetadata)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun metadataStillFinishesAfterFiniteObservationSpinnerStops() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val gate = CompletableDeferred<Unit>()
                val backend =
                    Backend().apply {
                        reads = listOf(read(listOf(3)))
                        metadata = { id ->
                            gate.await()
                            CollectionDetailsData(id, "Related $id", null, null, null, emptyList())
                        }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(4_000)
                runCurrent()
                assertFalse(vm.state.value.similarLoading)
                assertTrue(
                    vm.state.value.similar
                        .isEmpty(),
                )
                gate.complete(Unit)
                runCurrent()
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun leavingScreenCancelsPollingAndReturningRefreshesWithoutNewTrigger() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = Backend().apply { reads = listOf(read(listOf(3), true)) }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                val entry = UUID.randomUUID().toString()
                vm.show(7, "one", entry)
                runCurrent()
                vm.onScreenHidden()
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals(1, backend.readCalls)
                assertEquals(1, backend.triggerCalls)
                vm.show(7, "one", entry)
                runCurrent()
                assertEquals(2, backend.readCalls)
                assertEquals(1, backend.triggerCalls)
                assertEquals(listOf(3), backend.requestedMetadata)
                vm.onScreenHidden()
                advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun unavailableReadDoesNotBlockTriggerOrEraseCollectionDetails() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = Backend().apply { readFailure = IOException("GET unavailable") }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                advanceTimeBy(1_000)
                runCurrent()
                assertEquals(
                    "Origin",
                    vm.state.value.details
                        ?.name,
                )
                assertTrue(vm.state.value.similarReadError)
                assertFalse(vm.state.value.similarLoading)
                assertEquals(1, backend.triggerCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun triggerFailureKeepsPersistedCardsAndDoesNotRetryThePost() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend =
                    Backend().apply {
                        reads = listOf(read(listOf(3)))
                        triggerFailure = IOException("POST failed")
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                assertTrue(vm.state.value.similarTriggerError)
                assertEquals(
                    listOf(3),
                    vm.state.value.similar
                        .map { it.id },
                )
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals(1, backend.triggerCalls)
                assertFalse(vm.state.value.similarLoading)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sameEntryAndChildBackDoNotRetriggerButNewSameIdEntryDoes() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = Backend().apply { reads = listOf(read()) }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                val first = UUID.randomUUID().toString()
                vm.show(7, "one", first)
                runCurrent()
                vm.show(7, "one", first) // Recomposition or return from a child.
                runCurrent()
                assertEquals(1, backend.triggerCalls)
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                assertEquals(2, backend.triggerCalls)
                vm.onScreenHidden()
                runCurrent()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun nestedCollectionBackRestoresFocusedRelationshipWithoutRetrigger() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val backend = Backend().apply { reads = listOf(read(listOf(3))) }
                val entry = UUID.randomUUID().toString()
                val stack = mutableListOf<Destination>(Destination.SeerrCollection(7, null, null, "one", entry))
                val navigation = mockk<NavigationManager>(relaxed = true)
                every { navigation.backStack } returns stack
                every { navigation.navigateTo(any()) } answers {
                    stack.add(firstArg())
                    Unit
                }
                val vm = viewModel(MutableStateFlow(session("one", backend)), navigation)
                vm.show(7, "one", entry)
                runCurrent()
                vm.focusSimilar(3)
                vm.openSimilar(3)
                assertEquals(3, (stack.last() as Destination.SeerrCollection).collectionId)
                assertTrue((stack.last() as Destination.SeerrCollection).entryToken.isNotBlank())
                vm.onScreenHidden()
                stack.removeLast()
                vm.show(7, "one", entry)
                runCurrent()
                assertEquals(3, vm.state.value.similarFocusedId)
                assertEquals(1, backend.triggerCalls)
                vm.onScreenHidden()
                advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionSwitchCancelsOldMetadataAndObservation() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val slow = CompletableDeferred<CollectionDetailsData>()
                val old =
                    Backend().apply {
                        reads = listOf(read(listOf(3), true))
                        metadata = { slow.await() }
                    }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", old))
                val vm = viewModel(sessions)
                vm.show(7, "one", UUID.randomUUID().toString())
                runCurrent()
                sessions.value = session("two", Backend())
                runCurrent()
                slow.complete(CollectionDetailsData(3, "Stale", null, null, null, emptyList()))
                advanceTimeBy(30_000)
                runCurrent()
                assertEquals("two", vm.state.value.owner)
                assertTrue(
                    vm.state.value.similar
                        .isEmpty(),
                )
                assertEquals(1, old.readCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun sessionRefreshWhileParentIsHiddenDoesNotStartPolling() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val first = Backend().apply { reads = listOf(read()) }
                val second = Backend().apply { reads = listOf(read()) }
                val sessions = MutableStateFlow<CollectionsSession?>(session("one", first))
                val vm = viewModel(sessions)
                val entry = UUID.randomUUID().toString()
                vm.show(7, "one", entry)
                runCurrent()
                vm.onScreenHidden()
                sessions.value = session("one", second)
                runCurrent()
                assertEquals(0, second.readCalls)
                vm.show(7, "one", entry)
                runCurrent()
                assertEquals(1, second.readCalls) // Resume read without a new trigger or active claim.
                assertEquals(0, second.triggerCalls)
                vm.onScreenHidden()
                advanceUntilIdle()
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test fun lateCancelledReadCannotStopNewObservation() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val old = CompletableDeferred<SimilarCollectionsRead>()
                val backend =
                    Backend().apply {
                        readOverride = { call ->
                            if (call == 1) withContext(NonCancellable) { old.await() } else read(active = true)
                        }
                    }
                val vm = viewModel(MutableStateFlow(session("one", backend)))
                val entry = UUID.randomUUID().toString()
                vm.show(7, "one", entry)
                runCurrent()
                vm.onScreenHidden()
                vm.show(7, "one", entry)
                runCurrent()
                assertTrue(vm.state.value.similarLoading)
                assertEquals(1, backend.triggerCalls)
                old.complete(read(listOf(9)))
                runCurrent()
                assertTrue(vm.state.value.similarLoading)
                assertTrue(
                    vm.state.value.similarIds
                        .isEmpty(),
                )
                vm.onScreenHidden()
                advanceUntilIdle()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
