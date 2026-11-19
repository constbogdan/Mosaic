package com.github.damontecres.wholphin.services

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.data.AppDatabase
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.JellyfinIdentityItem
import com.github.damontecres.wholphin.data.model.JellyfinIdentitySnapshot
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinIdentityIndexTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val server = JellyfinServer(id(1), "local", "http://invalid", "10.10.7")
    private val secondServer = JellyfinServer(id(2), "other", "http://invalid2", "10.10.7")
    private var active: CurrentUser? = null
    private var now = 1234L
    private val source = FakeSource()
    private val index = JellyfinIdentityIndex(source, db.jellyfinIdentityDao(), { active }, { now })

    @After fun close() = db.close()

    private fun id(number: Int): UUID = UUID(0, number.toLong())

    private fun signIn(
        server: JellyfinServer,
        userId: UUID,
    ): CurrentUser {
        db.serverDao().addOrUpdateServer(server)
        val user = db.serverDao().addOrUpdateUser(JellyfinUser(id = userId, name = null, serverId = server.id, accessToken = null))
        return CurrentUser(server, user).also { active = it }
    }

    private inner class FakeSource : JellyfinIdentitySource {
        val views = mutableMapOf<UUID, List<IdentityView>>()
        val entries = mutableMapOf<UUID, List<IdentityPageItem>>()
        val calls = mutableListOf<Triple<UUID, Int, Int>>()
        val pageStarted = Channel<Unit>(Channel.UNLIMITED)
        var overridePage: ((UUID, Int, Int) -> IdentityPage)? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun views(userId: UUID): List<IdentityView> = views[userId].orEmpty()

        override suspend fun page(
            userId: UUID,
            viewId: UUID,
            offset: Int,
            limit: Int,
        ): IdentityPage {
            calls += Triple(viewId, offset, limit)
            pageStarted.send(Unit)
            gate?.await()
            overridePage?.let { return it(viewId, offset, limit) }
            val data = entries[viewId].orEmpty()
            return IdentityPage(data.size, data.drop(offset).take(limit))
        }
    }

    private fun movie(
        number: Int,
        tmdb: String?,
    ) = IdentityPageItem(id(number), BaseItemKind.MOVIE, tmdb)

    private fun series(
        number: Int,
        tmdb: String?,
    ) = IdentityPageItem(id(number), BaseItemKind.SERIES, tmdb)

    private fun discover(
        tmdb: Int,
        type: SeerrItemType = SeerrItemType.MOVIE,
    ) = DiscoverItem(tmdb, type, null, null, null, SeerrAvailability.AVAILABLE, null, null, null, null, id(9999))

    private fun resolver(verify: suspend (CurrentUser, BaseItemKind, Int, List<UUID>) -> List<UUID>) =
        SeerrOwnedNavigationResolver(
            index,
            object : JellyfinItemAccessVerifier {
                override suspend fun verifiedIds(
                    owner: CurrentUser,
                    kind: BaseItemKind,
                    tmdbId: Int,
                    candidates: List<UUID>,
                ) = verify(owner, kind, tmdbId, candidates)
            },
            { active },
            { true },
        )

    @Test fun `movies and series use explicit positive Tmdb only`() =
        runTest {
            val owner = signIn(server, id(10))
            source.views[owner.user.id] = listOf(IdentityView(id(20), CollectionType.MOVIES), IdentityView(id(21), CollectionType.TVSHOWS))
            source.entries[id(20)] = listOf(movie(30, "123"), movie(31, "0"), movie(32, "abc"), movie(33, null))
            source.entries[id(21)] = listOf(series(34, "456"))
            val snapshot = index.refresh()
            assertEquals(5, snapshot.returnedItemCount)
            assertEquals(3, snapshot.missingTmdbCount)
            assertEquals(listOf(id(30)), (index.lookup(BaseItemKind.MOVIE, 123) as JellyfinIdentityLookup.Candidates).itemIds)
            assertEquals(listOf(id(34)), (index.lookup(BaseItemKind.SERIES, 456) as JellyfinIdentityLookup.Candidates).itemIds)
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 456))
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 0))
            assertEquals(2, source.calls.size)
        }

    @Test fun `500 item pages cover each selected user view and omit unrelated views`() =
        runTest {
            val owner = signIn(server, id(11))
            source.views[owner.user.id] =
                listOf(
                    IdentityView(id(22), CollectionType.MOVIES),
                    IdentityView(id(23), CollectionType.TVSHOWS),
                    IdentityView(id(24), CollectionType.MUSIC),
                    IdentityView(id(25), null), // Jellyfin's mixed view can contain either kind.
                )
            source.entries[id(22)] = (1000..1500).map { movie(it, it.toString()) }
            source.entries[id(23)] = (2000..2063).map { series(it, it.toString()) }
            source.entries[id(25)] = listOf(movie(3000, "3000"))
            assertEquals(566, index.refresh().returnedItemCount)
            assertEquals(listOf(0, 500), source.calls.filter { it.first == id(22) }.map { it.second })
            assertEquals(listOf(0), source.calls.filter { it.first == id(23) }.map { it.second })
            assertEquals(listOf(0), source.calls.filter { it.first == id(25) }.map { it.second })
            assertTrue(source.calls.all { it.first != id(24) && it.third == 500 })
        }

    @Test fun `incomplete scan and provider failure keep prior complete snapshot`() =
        runTest {
            val owner = signIn(server, id(12))
            source.views[owner.user.id] = listOf(IdentityView(id(25), CollectionType.MOVIES))
            source.entries[id(25)] = listOf(movie(40, "700"))
            index.refresh()
            source.overridePage = { _, _, _ -> IdentityPage(2, listOf(movie(41, "701"))) }
            assertThrows(IncompleteJellyfinIdentityScan::class.java) { kotlinx.coroutines.runBlocking { index.refresh() } }
            assertEquals(listOf(id(40)), (index.lookup(BaseItemKind.MOVIE, 700) as JellyfinIdentityLookup.Candidates).itemIds)
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 701))
            source.overridePage = { _, _, _ -> error("provider failed") }
            assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { index.refresh() } }
            assertEquals(1, db.jellyfinIdentityDao().all(owner.user.rowId).size)
        }

    @Test fun `server and user snapshots cannot cross identity boundaries`() =
        runTest {
            val first = signIn(server, id(13))
            source.views[first.user.id] = listOf(IdentityView(id(26), CollectionType.MOVIES))
            source.entries[id(26)] = listOf(movie(50, "800"))
            index.refresh()
            val second = signIn(server, id(14))
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 800))
            source.views[second.user.id] = listOf(IdentityView(id(27), CollectionType.MOVIES))
            source.entries[id(27)] = listOf(movie(51, "800"))
            index.refresh()
            assertEquals(listOf(id(51)), (index.lookup(BaseItemKind.MOVIE, 800) as JellyfinIdentityLookup.Candidates).itemIds)
            val third = signIn(secondServer, id(13))
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 800))
            assertTrue(third.user.rowId != first.user.rowId)
            active = first
            assertEquals(listOf(id(50)), (index.lookup(BaseItemKind.MOVIE, 800) as JellyfinIdentityLookup.Candidates).itemIds)
        }

    @Test fun `concurrent callers share one refresh and session change rejects publication`() =
        runTest {
            val owner = signIn(server, id(15))
            source.views[owner.user.id] = listOf(IdentityView(id(28), CollectionType.MOVIES))
            source.entries[id(28)] = listOf(movie(60, "900"))
            source.gate = CompletableDeferred()
            val first = async { index.refresh() }
            runCurrent()
            val second = async { index.refresh() }
            runCurrent()
            assertEquals(1, source.calls.size)
            source.gate!!.complete(Unit)
            assertEquals(first.await(), second.await())
            source.gate = CompletableDeferred()
            val stale = async { runCatching { index.refresh() } }
            runCurrent()
            active = signIn(server, id(16))
            source.gate!!.complete(Unit)
            assertTrue(stale.await().exceptionOrNull() is IncompleteJellyfinIdentityScan)
            assertEquals(1, db.jellyfinIdentityDao().all(owner.user.rowId).size)

            // Reauthenticating the same user creates a new session, not a follower of old work.
            active = owner
            source.gate = CompletableDeferred()
            val oldSession = async { runCatching { index.refresh() } }
            runCurrent()
            active = owner.copy()
            val newSession = async { index.refresh() }
            runCurrent()
            assertEquals(4, source.calls.size)
            source.gate!!.complete(Unit)
            assertTrue(oldSession.await().exceptionOrNull() is IncompleteJellyfinIdentityScan)
            assertEquals(owner.user.id, newSession.await().userId)
        }

    @Test fun `duplicate canonical identity retains candidates but grouping can hide underlying items`() =
        runTest {
            val owner = signIn(server, id(17))
            source.views[owner.user.id] = listOf(IdentityView(id(29), CollectionType.MOVIES))
            source.entries[id(29)] = listOf(movie(70, "1000"), movie(71, "1000"))
            index.refresh()
            assertEquals(listOf(id(70), id(71)), (index.lookup(BaseItemKind.MOVIE, 1000) as JellyfinIdentityLookup.Candidates).itemIds)
            // A 10.10.7 presentation-key-grouped response may expose only one of two underlying UUIDs.
            source.entries[id(29)] = listOf(movie(70, "1000"))
            index.refresh()
            assertEquals(1, db.jellyfinIdentityDao().all(owner.user.rowId).size)
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 9999))
        }

    @Test fun `replacement rolls back if insertion fails after deletion`() =
        runTest {
            val owner = signIn(server, id(18))
            val old = JellyfinIdentityItem(owner.user.rowId, "Movie", 42, id(80), id(30))
            val oldSnapshot = JellyfinIdentitySnapshot(owner.user.rowId, server.id, owner.user.id, 1, 1, 0)
            db.jellyfinIdentityDao().replace(oldSnapshot, listOf(old))
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER abort_identity_insert BEFORE INSERT ON jellyfin_identity_items BEGIN SELECT RAISE(ABORT, 'fixture failure'); END",
            )
            val replacement = old.copy(itemId = id(81))
            assertThrows(Exception::class.java) {
                kotlinx.coroutines.runBlocking {
                    db.jellyfinIdentityDao().replace(
                        oldSnapshot.copy(observedAtEpochMillis = 2),
                        listOf(replacement),
                    )
                }
            }
            assertEquals(listOf(old), db.jellyfinIdentityDao().all(owner.user.rowId))
            assertEquals(1L, db.jellyfinIdentityDao().snapshot(owner.user.rowId)?.observedAtEpochMillis)
        }

    @Test fun `authenticated session starts one asynchronous initial scan and reuses its snapshot`() =
        runTest {
            val sessions = MutableStateFlow<CurrentUser?>(null)
            val observer = launch { observeIdentitySessions(sessions, index) }
            runCurrent()
            val owner = signIn(server, id(19))
            source.views[owner.user.id] = listOf(IdentityView(id(31), CollectionType.MOVIES))
            source.entries[id(31)] = listOf(movie(90, "1900"))
            sessions.value = owner
            runCurrent()
            source.pageStarted.receive()
            assertEquals(false, index.refreshIfMissing())
            assertEquals(1, source.calls.size)
            assertEquals(listOf(id(90)), (index.lookup(BaseItemKind.MOVIE, 1900) as JellyfinIdentityLookup.Candidates).itemIds)
            sessions.value = owner.copy()
            active = sessions.value
            runCurrent()
            assertEquals(1, source.calls.size)
            observer.cancelAndJoin()
        }

    @Test fun `logout cancels an in-flight initial scan without publishing a snapshot`() =
        runTest {
            val owner = signIn(server, id(20))
            source.views[owner.user.id] = listOf(IdentityView(id(32), CollectionType.MOVIES))
            source.entries[id(32)] = listOf(movie(91, "2000"))
            source.gate = CompletableDeferred()
            val sessions = MutableStateFlow<CurrentUser?>(null)
            val observer = launch { observeIdentitySessions(sessions, index) }
            runCurrent()
            sessions.value = owner
            runCurrent()
            source.pageStarted.receive()
            assertEquals(1, source.calls.size)
            sessions.value = null
            active = null
            runCurrent()
            source.gate!!.complete(Unit)
            assertEquals(null, db.jellyfinIdentityDao().snapshot(owner.user.rowId))
            observer.cancelAndJoin()
        }

    @Test fun `session switching cancels old scan and indexes only the new user`() =
        runTest {
            val first = signIn(server, id(21))
            source.views[first.user.id] = listOf(IdentityView(id(33), CollectionType.MOVIES))
            source.entries[id(33)] = listOf(movie(93, "2100"))
            source.gate = CompletableDeferred()
            val sessions = MutableStateFlow<CurrentUser?>(null)
            val observer = launch { observeIdentitySessions(sessions, index) }
            runCurrent()
            sessions.value = first
            runCurrent()
            source.pageStarted.receive()
            assertEquals(1, source.calls.size)
            val second = signIn(secondServer, id(22))
            source.views[second.user.id] = listOf(IdentityView(id(34), CollectionType.TVSHOWS))
            source.entries[id(34)] = listOf(series(94, "2200"))
            sessions.value = second
            runCurrent()
            source.pageStarted.receive()
            source.gate!!.complete(Unit)
            assertEquals(false, index.refreshIfMissing())
            assertEquals(null, db.jellyfinIdentityDao().snapshot(first.user.rowId))
            assertEquals(second.user.id, db.jellyfinIdentityDao().snapshot(second.user.rowId)?.userId)
            assertEquals(JellyfinIdentityLookup.Unresolved, index.lookup(BaseItemKind.MOVIE, 2100))
            observer.cancelAndJoin()
        }

    @Test fun `concurrent initial refreshes share one scan and a changed API identity cannot publish`() =
        runTest {
            val owner = signIn(server, id(23))
            source.views[owner.user.id] = listOf(IdentityView(id(35), CollectionType.MOVIES))
            source.entries[id(35)] = listOf(movie(95, "2300"))
            source.gate = CompletableDeferred()
            var authenticated = true
            val guarded = JellyfinIdentityIndex(source, db.jellyfinIdentityDao(), { active }, { 1234L }) { authenticated }
            val first = async { guarded.refreshIfMissing() }
            val second = async { guarded.refreshIfMissing() }
            runCurrent()
            source.pageStarted.receive()
            assertEquals(1, source.calls.size)
            source.gate!!.complete(Unit)
            assertTrue(first.await())
            assertEquals(false, second.await())
            assertEquals(1, source.calls.size)
            source.gate = CompletableDeferred()
            val stale = async { runCatching { guarded.refresh() } }
            runCurrent()
            source.pageStarted.receive()
            authenticated = false
            source.gate!!.complete(Unit)
            assertTrue(stale.await().exceptionOrNull() is IncompleteJellyfinIdentityScan)
            assertEquals(1, db.jellyfinIdentityDao().all(owner.user.rowId).size)
        }

    @Test fun `recent snapshot is reused and stale refresh preserves old generation on failure`() =
        runTest {
            val owner = signIn(server, id(24))
            source.views[owner.user.id] = listOf(IdentityView(id(36), CollectionType.MOVIES))
            source.entries[id(36)] = listOf(movie(96, "2400"))
            assertTrue(index.refreshIfStale())
            assertEquals(false, index.refreshIfStale())
            assertEquals(1, source.calls.size)
            now += JellyfinIdentityIndex.MAX_SNAPSHOT_AGE_MS
            source.overridePage = { _, _, _ -> error("provider failed") }
            assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { index.refreshIfStale() } }
            assertEquals(listOf(id(96)), (index.lookup(BaseItemKind.MOVIE, 2400) as JellyfinIdentityLookup.Candidates).itemIds)
            assertEquals(1234L, db.jellyfinIdentityDao().snapshot(owner.user.rowId)?.observedAtEpochMillis)
            source.overridePage = null
            assertTrue(index.refreshIfStale())
            assertEquals(now, db.jellyfinIdentityDao().snapshot(owner.user.rowId)?.observedAtEpochMillis)
        }

    @Test fun `verified movie and series use indexed identities never Seerr identity`() =
        runTest {
            val owner = signIn(server, id(25))
            source.views[owner.user.id] = listOf(IdentityView(id(37), CollectionType.MOVIES), IdentityView(id(38), CollectionType.TVSHOWS))
            source.entries[id(37)] = listOf(movie(97, "2500"))
            source.entries[id(38)] = listOf(series(98, "2501"))
            index.refresh()
            val navigation =
                resolver { receivedOwner, kind, tmdb, candidates ->
                    assertTrue(receivedOwner === owner)
                    assertEquals(if (tmdb == 2500) BaseItemKind.MOVIE else BaseItemKind.SERIES, kind)
                    candidates
                }
            assertEquals(Destination.MediaItem(id(97), BaseItemKind.MOVIE), navigation.resolve(discover(2500), owner))
            assertEquals(Destination.MediaItem(id(98), BaseItemKind.SERIES), navigation.resolve(discover(2501, SeerrItemType.TV), owner))
            assertTrue(id(9999) != id(97))
        }

    @Test fun `missing identity index miss denial error and ambiguous candidates fall back to Discover`() =
        runTest {
            val owner = signIn(server, id(26))
            source.views[owner.user.id] = listOf(IdentityView(id(39), CollectionType.MOVIES))
            source.entries[id(39)] = listOf(movie(99, "2600"), movie(100, "2600"))
            index.refresh()
            var calls = 0
            val denied =
                resolver { _, _, _, _ ->
                    calls++
                    emptyList()
                }
            for (item in listOf(discover(0), discover(2601), discover(2600))) {
                assertEquals(Destination.DiscoveredItem(item), denied.resolve(item, owner))
            }
            assertEquals(1, calls)
            val failed = resolver { _, _, _, _ -> error("offline") }
            assertEquals(Destination.DiscoveredItem(discover(2600)), failed.resolve(discover(2600), owner))
            val ambiguous = resolver { _, _, _, candidates -> candidates }
            assertEquals(Destination.DiscoveredItem(discover(2600)), ambiguous.resolve(discover(2600), owner))
        }

    @Test fun `account switch while verifying suppresses navigation`() =
        runTest {
            val owner = signIn(server, id(27))
            source.views[owner.user.id] = listOf(IdentityView(id(40), CollectionType.MOVIES))
            source.entries[id(40)] = listOf(movie(101, "2700"))
            index.refresh()
            val gate = CompletableDeferred<Unit>()
            val navigation =
                resolver { _, _, _, candidates ->
                    gate.await()
                    candidates
                }
            val pending = async { navigation.resolve(discover(2700), owner) }
            runCurrent()
            signIn(secondServer, id(28))
            gate.complete(Unit)
            assertEquals(null, pending.await())
        }

    @Test fun `verification timeout falls back without losing index snapshot`() =
        runTest {
            val owner = signIn(server, id(29))
            source.views[owner.user.id] = listOf(IdentityView(id(41), CollectionType.TVSHOWS))
            source.entries[id(41)] = listOf(series(102, "2900"))
            index.refresh()
            val unresolved = CompletableDeferred<Unit>()
            val navigation =
                resolver { _, _, _, _ ->
                    unresolved.await()
                    listOf(id(102))
                }
            val item = discover(2900, SeerrItemType.TV)
            val pending = async { navigation.resolve(item, owner) }
            runCurrent()
            advanceTimeBy(SeerrOwnedNavigationResolver.VERIFY_TIMEOUT_MS + 1)
            assertEquals(Destination.DiscoveredItem(item), pending.await())
            assertEquals(1, db.jellyfinIdentityDao().all(owner.user.rowId).size)
        }

    @Test fun `verification requires exact item ID media type and Tmdb provider ID`() {
        val candidate = id(103)
        val items =
            listOf(
                BaseItemDto(id = candidate, type = BaseItemKind.MOVIE, providerIds = mapOf("Tmdb" to "3000")),
                BaseItemDto(id = id(104), type = BaseItemKind.MOVIE, providerIds = mapOf("Tmdb" to "3000")),
                BaseItemDto(id = id(105), type = BaseItemKind.SERIES, providerIds = mapOf("Tmdb" to "3000")),
                BaseItemDto(id = id(106), type = BaseItemKind.MOVIE, providerIds = mapOf("Tmdb" to "3001")),
                BaseItemDto(id = id(107), type = BaseItemKind.MOVIE, providerIds = null),
            )
        assertEquals(listOf(candidate), items.verifiedJellyfinIds(BaseItemKind.MOVIE, 3000, listOf(candidate)))
        assertEquals(emptyList<UUID>(), items.verifiedJellyfinIds(BaseItemKind.MOVIE, 3001, listOf(id(104))))
        assertEquals(emptyList<UUID>(), (items + items.first()).verifiedJellyfinIds(BaseItemKind.MOVIE, 3000, listOf(candidate)))
    }
}
