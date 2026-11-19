package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinIdentityDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinIdentityItem
import com.github.damontecres.wholphin.data.model.JellyfinIdentitySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class IdentityView(
    val id: UUID,
    val type: CollectionType?,
)

data class IdentityPageItem(
    val id: UUID,
    val type: BaseItemKind?,
    val tmdb: String?,
)

data class IdentityPage(
    val total: Int,
    val items: List<IdentityPageItem>,
)

interface JellyfinIdentitySource {
    suspend fun views(userId: UUID): List<IdentityView>

    suspend fun page(
        userId: UUID,
        viewId: UUID,
        offset: Int,
        limit: Int,
    ): IdentityPage
}

/** Uses the same authenticated ApiClient as ordinary Jellyfin browsing. */
class JellyfinIdentityApiSource
    @Inject
    constructor(
        private val api: ApiClient,
    ) : JellyfinIdentitySource {
        override suspend fun views(userId: UUID): List<IdentityView> {
            val result = api.userViewsApi.getUserViews(userId = userId).content
            if (result.items.size != result.totalRecordCount) throw IncompleteJellyfinIdentityScan("Incomplete user views")
            return result.items.map { IdentityView(it.id, it.collectionType) }
        }

        override suspend fun page(
            userId: UUID,
            viewId: UUID,
            offset: Int,
            limit: Int,
        ): IdentityPage {
            val result =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            parentId = viewId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            fields = listOf(ItemFields.PROVIDER_IDS),
                            enableUserData = false,
                            enableImages = false,
                            excludeLocationTypes = listOf(LocationType.VIRTUAL),
                            collapseBoxSetItems = false,
                            startIndex = offset,
                            limit = limit,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            return IdentityPage(
                total = result.totalRecordCount,
                items = result.items.map { IdentityPageItem(it.id, it.type, it.providerIds?.get("Tmdb")) },
            )
        }
    }

/** An index hit is an observation, not a fresh authorization or playability check. */
sealed interface JellyfinIdentityLookup {
    data class Candidates(
        val itemIds: List<UUID>,
        val observedAtEpochMillis: Long,
    ) : JellyfinIdentityLookup

    data object Unresolved : JellyfinIdentityLookup
}

class IncompleteJellyfinIdentityScan(
    message: String,
) : IllegalStateException(message)

/** Current-user index. A successful snapshot remains available during a stale refresh. */
@Singleton
class JellyfinIdentityIndex internal constructor(
    private val source: JellyfinIdentitySource,
    private val dao: JellyfinIdentityDao,
    private val session: () -> CurrentUser?,
    private val clock: () -> Long,
    private val authenticatedAs: (CurrentUser) -> Boolean = { true },
) {
    @Inject
    constructor(source: JellyfinIdentityApiSource, dao: JellyfinIdentityDao, serverRepository: ServerRepository) :
        this(
            source,
            dao,
            { serverRepository.current.value },
            System::currentTimeMillis,
            { owner ->
                serverRepository.apiClient.accessToken == owner.user.accessToken &&
                    serverRepository.apiClient.baseUrl?.trimEnd('/') == owner.server.url.trimEnd('/')
            },
        )

    private val inFlightMutex = Mutex()
    private val initialRefreshMutex = Mutex()
    private val inFlight = IdentityHashMap<CurrentUser, CompletableDeferred<JellyfinIdentitySnapshot>>()

    /** A successful snapshot is reused on later app starts; an explicit refresh remains available. */
    suspend fun refreshIfMissing(): Boolean = refreshIfStale(Long.MAX_VALUE)

    /** Refresh once when absent or older than [maxAgeMillis], without replacing a failed snapshot. */
    suspend fun refreshIfStale(maxAgeMillis: Long = MAX_SNAPSHOT_AGE_MS): Boolean =
        initialRefreshMutex.withLock {
            require(maxAgeMillis > 0)
            val owner = session() ?: return@withLock false
            checkOwner(owner)
            val existing = dao.snapshot(owner.user.rowId)
            checkOwner(owner)
            val age = existing?.let { clock() - it.observedAtEpochMillis }
            if (existing?.serverId == owner.server.id && existing.userId == owner.user.id &&
                age != null && age >= 0 && age < maxAgeMillis
            ) {
                Timber.i(
                    "JellyfinIdentityIndex skip existing snapshot server=%s user=%s",
                    owner.server.id.redacted(),
                    owner.user.id.redacted(),
                )
                false
            } else {
                refresh()
                true
            }
        }

    /** Concurrent callers for the same server/user share one scan, including its failure. */
    suspend fun refresh(): JellyfinIdentitySnapshot {
        val owner = session() ?: error("Jellyfin user is not authenticated")
        require(owner.user.rowId > 0)
        checkOwner(owner)
        var leader = false
        val pending =
            inFlightMutex.withLock {
                inFlight[owner] ?: CompletableDeferred<JellyfinIdentitySnapshot>().also {
                    inFlight[owner] = it
                    leader = true
                }
            }
        if (leader) {
            val progress = ScanProgress()
            val startNanos = System.nanoTime()
            Timber.i("JellyfinIdentityIndex start server=%s user=%s", owner.server.id.redacted(), owner.user.id.redacted())
            try {
                val snapshot = scan(owner, progress)
                Timber.i(
                    "JellyfinIdentityIndex complete server=%s user=%s views=%d movies=%d series=%d validTmdb=%d missingTmdb=%d requests=%d elapsedMs=%d replaced=true",
                    owner.server.id.redacted(),
                    owner.user.id.redacted(),
                    progress.views,
                    progress.movies,
                    progress.series,
                    progress.movies + progress.series - snapshot.missingTmdbCount,
                    snapshot.missingTmdbCount,
                    progress.requests,
                    (System.nanoTime() - startNanos) / 1_000_000,
                )
                pending.complete(snapshot)
            } catch (failure: Throwable) {
                Timber.w(
                    "JellyfinIdentityIndex failure server=%s user=%s views=%d movies=%d series=%d validTmdb=%d missingTmdb=%d requests=%d elapsedMs=%d replaced=false cause=%s",
                    owner.server.id.redacted(),
                    owner.user.id.redacted(),
                    progress.views,
                    progress.movies,
                    progress.series,
                    progress.movies + progress.series - progress.missingTmdb,
                    progress.missingTmdb,
                    progress.requests,
                    (System.nanoTime() - startNanos) / 1_000_000,
                    failure.javaClass.simpleName,
                )
                pending.completeExceptionally(failure)
            } finally {
                inFlightMutex.withLock { if (inFlight[owner] === pending) inFlight.remove(owner) }
            }
        }
        return pending.await()
    }

    suspend fun lookup(
        mediaType: BaseItemKind,
        tmdbId: Int,
    ): JellyfinIdentityLookup {
        if (tmdbId <= 0 || mediaType !in listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)) return JellyfinIdentityLookup.Unresolved
        val owner = session() ?: return JellyfinIdentityLookup.Unresolved
        val read = dao.find(owner.user.rowId, mediaType.serialName, tmdbId) ?: return JellyfinIdentityLookup.Unresolved
        if (read.snapshot.serverId != owner.server.id || read.snapshot.userId != owner.user.id) return JellyfinIdentityLookup.Unresolved
        val ids = read.items.map { it.itemId }
        if (session() !== owner) return JellyfinIdentityLookup.Unresolved
        return if (ids.isEmpty()) {
            JellyfinIdentityLookup.Unresolved
        } else {
            JellyfinIdentityLookup.Candidates(
                ids,
                read.snapshot.observedAtEpochMillis,
            )
        }
    }

    private fun checkOwner(owner: CurrentUser) {
        if (session() !== owner || !authenticatedAs(owner)) {
            throw IncompleteJellyfinIdentityScan("Jellyfin session changed during scan")
        }
    }

    private suspend fun scan(
        owner: CurrentUser,
        progress: ScanProgress,
    ): JellyfinIdentitySnapshot {
        checkOwner(owner)
        progress.requests++
        val views =
            source.views(owner.user.id).filter {
                it.type == CollectionType.MOVIES || it.type == CollectionType.TVSHOWS || it.type == null
            }
        checkOwner(owner)
        progress.views = views.size
        if (views.map { it.id }.toSet().size != views.size) throw IncompleteJellyfinIdentityScan("Duplicate user view")
        val itemsById = linkedMapOf<UUID, JellyfinIdentityItem>()
        for (view in views) {
            var offset = 0
            var expectedTotal: Int? = null
            val seenInView = mutableSetOf<UUID>()
            do {
                currentCoroutineContext().ensureActive()
                checkOwner(owner)
                progress.requests++
                val page = source.page(owner.user.id, view.id, offset, PAGE_SIZE)
                checkOwner(owner)
                if (page.total < 0 || page.total > MAX_ITEMS || page.items.size > PAGE_SIZE ||
                    (expectedTotal != null && expectedTotal != page.total) || offset + page.items.size > page.total
                ) {
                    throw IncompleteJellyfinIdentityScan("Inconsistent Jellyfin page count")
                }
                expectedTotal = page.total
                if (page.items.isEmpty() && offset < page.total) throw IncompleteJellyfinIdentityScan("Truncated Jellyfin page")
                for (item in page.items) {
                    if (!seenInView.add(item.id) ||
                        item.id == UUID(0, 0)
                    ) {
                        throw IncompleteJellyfinIdentityScan("Duplicate or invalid item ID")
                    }
                    if (item.type != BaseItemKind.MOVIE && item.type != BaseItemKind.SERIES) {
                        throw IncompleteJellyfinIdentityScan("Unexpected Jellyfin item type")
                    }
                    val row = JellyfinIdentityItem(owner.user.rowId, item.type.serialName, item.tmdb.toPositiveTmdbId(), item.id, view.id)
                    val previous = itemsById.putIfAbsent(item.id, row)
                    if (previous != null && (previous.mediaType != row.mediaType || previous.tmdbId != row.tmdbId)) {
                        throw IncompleteJellyfinIdentityScan("Conflicting item identity across views")
                    }
                    if (previous == null) {
                        if (item.type == BaseItemKind.MOVIE) progress.movies++ else progress.series++
                        if (row.tmdbId == null) progress.missingTmdb++
                    }
                }
                offset += page.items.size
            } while (offset < page.total)
            if (offset != expectedTotal) throw IncompleteJellyfinIdentityScan("Incomplete Jellyfin view")
        }
        currentCoroutineContext().ensureActive()
        checkOwner(owner)
        val items = itemsById.values.toList()
        val snapshot =
            JellyfinIdentitySnapshot(
                userRowId = owner.user.rowId,
                serverId = owner.server.id,
                userId = owner.user.id,
                observedAtEpochMillis = clock(),
                returnedItemCount = items.size,
                missingTmdbCount = items.count { it.tmdbId == null },
            )
        checkOwner(owner)
        dao.replace(snapshot, items)
        return snapshot
    }

    private class ScanProgress(
        var views: Int = 0,
        var movies: Int = 0,
        var series: Int = 0,
        var missingTmdb: Int = 0,
        var requests: Int = 0,
    )

    companion object {
        const val PAGE_SIZE = 500
        const val MAX_SNAPSHOT_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_ITEMS = 100_000
    }
}

internal fun String?.toPositiveTmdbId(): Int? = this?.trim()?.toIntOrNull()?.takeIf { it > 0 }

private fun UUID.redacted(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toString().toByteArray())
        .take(4)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
