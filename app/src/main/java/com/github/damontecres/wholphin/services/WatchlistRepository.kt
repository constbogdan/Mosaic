package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.MediaKey
import com.github.damontecres.wholphin.data.model.WatchlistCategory
import com.github.damontecres.wholphin.data.model.WatchlistMembership
import com.github.damontecres.wholphin.data.model.WatchlistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal interface WatchlistBackend {
    suspend fun page(
        category: WatchlistCategory,
        page: Int,
    ): WatchlistPage

    suspend fun membership(keys: Set<MediaKey.Catalog>): Map<MediaKey.Catalog, Boolean>

    suspend fun add(
        key: MediaKey.Catalog,
        title: String,
    )

    suspend fun remove(key: MediaKey.Catalog)
}

internal class WatchlistSession(
    val owner: String,
    val backend: WatchlistBackend,
    val epoch: Any = backend,
    val isCurrent: () -> Boolean,
)

class WatchlistUnavailable(
    message: String,
) : IllegalStateException(message)

data class WatchlistSnapshot(
    val owner: String? = null,
    val revision: Long = 0,
    val membership: Map<MediaKey.Catalog, WatchlistMembership> = emptyMap(),
    val busy: Set<MediaKey.Catalog> = emptySet(),
)

/** Personal, in-memory authority. No acquisition polling or persistent membership cache. */
@Singleton
class WatchlistRepository internal constructor(
    sessions: Flow<WatchlistSession?>,
    scope: CoroutineScope,
    private val now: () -> Long,
) {
    @Inject
    constructor(source: WatchlistSessionSource) : this(
        source.sessions,
        CoroutineScope(SupervisorJob() + com.github.damontecres.wholphin.util.WholphinDispatchers.Main.immediate),
        System::currentTimeMillis,
    )

    private val lock = Any()
    private var session: WatchlistSession? = null
    private val lookups = Mutex()
    private val pages = LinkedHashMap<Pair<WatchlistCategory, Int>, WatchlistPage>()
    private val observed = LinkedHashMap<MediaKey.Catalog, Long>()
    private var capability: WatchlistSession? = null
    private val _state = MutableStateFlow(WatchlistSnapshot())
    val state: StateFlow<WatchlistSnapshot> = _state

    init {
        scope.launch {
            sessions.collect { next ->
                synchronized(lock) {
                    if (session === next) return@synchronized
                    session = next
                    pages.clear()
                    observed.clear()
                    capability = null
                    _state.value = WatchlistSnapshot(owner = next?.owner, revision = _state.value.revision + 1)
                }
            }
        }
    }

    private fun current(): WatchlistSession =
        session?.takeIf { it.isCurrent() }
            ?: throw WatchlistUnavailable("Sign in to Seerr with the Jellyfin-linked personal account. Shared API keys are not supported.")

    private fun valid(captured: WatchlistSession) = session === captured && captured.isCurrent()

    suspend fun page(
        category: WatchlistCategory,
        page: Int,
        refresh: Boolean = false,
    ): WatchlistPage =
        lookups.withLock {
            require(page > 0)
            val captured: WatchlistSession
            val revision: Long
            synchronized(lock) {
                captured = current()
                revision = _state.value.revision
                if (refresh && page == 1) pages.clear()
                if (!refresh) pages[category to page]?.let { return@withLock it }
            }
            val result = captured.backend.page(category, page)
            require(result.page == page && result.totalPages >= page && result.totalResults >= 0)
            // This endpoint distinguishes the Phase 1 capability from an older/Plex list.
            var verifiedMembership: Map<MediaKey.Catalog, Boolean>? = null
            if (capability !== captured) {
                val probe =
                    result.entries
                        .take(100)
                        .mapTo(linkedSetOf()) { it.key }
                        .ifEmpty { linkedSetOf(MediaKey.Catalog(com.github.damontecres.wholphin.data.model.CatalogMediaType.MOVIE, 1)) }
                val response = captured.backend.membership(probe)
                require(response.keys == probe) { "Incomplete Watchlist capability response" }
                verifiedMembership = response
                synchronized(lock) { if (valid(captured)) capability = captured }
            }
            synchronized(lock) {
                if (!valid(captured) || revision != _state.value.revision) throw CancellationException("Watchlist page superseded")
                pages[category to page] = result
                while (pages.size > 8) pages.remove(pages.keys.first())
                result.entries.filterNot { it.key in _state.value.busy }.forEach {
                    val member = verifiedMembership?.get(it.key) != false
                    publish(it.key, if (member) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER)
                }
            }
            result
        }

    /** Serialized batches coalesce overlapping callers and retain the slot for hung requests. */
    suspend fun membership(
        keys: Set<MediaKey.Catalog>,
        refresh: Boolean = false,
    ) = lookups.withLock {
        keys.chunked(100).forEach { batch ->
            val captured: WatchlistSession
            val requested: Set<MediaKey.Catalog>
            val fence: Long
            synchronized(lock) {
                captured = current()
                requested =
                    batch.filterTo(linkedSetOf()) { key ->
                        key !in _state.value.busy && (
                            refresh || _state.value.membership[key] == WatchlistMembership.UNKNOWN ||
                                now() - (observed[key] ?: Long.MIN_VALUE / 2) >= 60_000
                        )
                    }
                fence = _state.value.revision
            }
            if (requested.isEmpty()) return@forEach
            val result =
                try {
                    captured.backend.membership(requested).also {
                        require(it.keys == requested) { "Incomplete Watchlist membership response" }
                    }
                } catch (error: Exception) {
                    synchronized(lock) {
                        if (valid(captured) && fence == _state.value.revision) {
                            requested.filter { it in _state.value.membership && it !in _state.value.busy }.forEach {
                                publish(it, WatchlistMembership.UNKNOWN)
                            }
                        }
                    }
                    throw error
                }
            synchronized(lock) {
                if (!valid(captured)) throw CancellationException("Watchlist session changed")
                result.forEach { (key, member) ->
                    if (key !in _state.value.busy && fence == _state.value.revision) {
                        publish(key, if (member) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER)
                    }
                }
            }
        }
    }

    suspend fun setMembership(
        key: MediaKey.Catalog,
        member: Boolean,
        title: String,
    ): Boolean {
        val captured: WatchlistSession
        synchronized(lock) {
            captured = current()
            if (key in _state.value.busy) return false
            if (_state.value.membership[key] == null || _state.value.membership[key] == WatchlistMembership.UNKNOWN) return false
            if ((_state.value.membership[key] == WatchlistMembership.MEMBER) == member) return true
            _state.value = _state.value.copy(busy = _state.value.busy + key, revision = _state.value.revision + 1)
            pages.clear()
        }
        try {
            try {
                if (member) captured.backend.add(key, title) else captured.backend.remove(key)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // A 409 or timeout is not proof of either outcome. Reconcile once, never resubmit.
                synchronized(lock) { if (valid(captured)) publish(key, WatchlistMembership.UNKNOWN) }
                val response = captured.backend.membership(setOf(key))
                require(response.keys == setOf(key))
                synchronized(lock) {
                    if (!valid(captured)) throw CancellationException("Watchlist session changed")
                    publish(key, if (response.getValue(key)) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER)
                }
                return response.getValue(key) == member
            }
            synchronized(lock) {
                if (!valid(captured)) throw CancellationException("Watchlist session changed")
                publish(key, if (member) WatchlistMembership.MEMBER else WatchlistMembership.NOT_MEMBER)
            }
            return true
        } catch (error: Exception) {
            synchronized(lock) { if (valid(captured)) publish(key, WatchlistMembership.UNKNOWN) }
            throw error
        } finally {
            synchronized(lock) {
                if (valid(captured)) {
                    pages.clear()
                    _state.value = _state.value.copy(busy = _state.value.busy - key, revision = _state.value.revision + 1)
                }
            }
        }
    }

    private fun publish(
        key: MediaKey.Catalog,
        membership: WatchlistMembership,
    ) {
        observed.remove(key)
        observed[key] = now()
        val values = _state.value.membership.toMutableMap()
        values[key] = membership
        while (observed.size > 2048) {
            val oldest = observed.keys.first()
            observed.remove(oldest)
            values.remove(oldest)
        }
        _state.value = _state.value.copy(membership = values.toMap())
    }
}
