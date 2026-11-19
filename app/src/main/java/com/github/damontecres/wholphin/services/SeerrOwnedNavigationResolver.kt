package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A candidate from Room is historical identity data, never current-user authorization. */
interface JellyfinItemAccessVerifier {
    suspend fun verifiedIds(
        owner: CurrentUser,
        kind: BaseItemKind,
        tmdbId: Int,
        candidates: List<UUID>,
    ): List<UUID>
}

class JellyfinItemAccessApiVerifier
    @Inject
    constructor(
        private val api: ApiClient,
    ) : JellyfinItemAccessVerifier {
        override suspend fun verifiedIds(
            owner: CurrentUser,
            kind: BaseItemKind,
            tmdbId: Int,
            candidates: List<UUID>,
        ): List<UUID> {
            val result =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = owner.user.id,
                            ids = candidates,
                            includeItemTypes = listOf(kind),
                            fields = listOf(ItemFields.PROVIDER_IDS),
                            enableUserData = false,
                            enableImages = false,
                            collapseBoxSetItems = false,
                            limit = candidates.size,
                            enableTotalRecordCount = true,
                        ),
                    ).content
            if (result.items.size != result.totalRecordCount) return emptyList()
            return result.items.verifiedJellyfinIds(kind, tmdbId, candidates)
        }
    }

internal fun List<BaseItemDto>.verifiedJellyfinIds(
    kind: BaseItemKind,
    tmdbId: Int,
    candidates: List<UUID>,
): List<UUID> {
    val expected = candidates.toSet()
    val returned = filter { it.id in expected }
    if (returned.map { it.id }.toSet().size != returned.size) return emptyList()
    return returned
        .filter { it.type == kind && it.providerIds?.get("Tmdb").toPositiveTmdbId() == tmdbId }
        .map { it.id }
}

@Singleton
class SeerrOwnedNavigationResolver internal constructor(
    private val index: JellyfinIdentityIndex,
    private val verifier: JellyfinItemAccessVerifier,
    private val session: () -> CurrentUser?,
    private val authenticatedAs: (CurrentUser) -> Boolean,
) {
    @Inject
    constructor(index: JellyfinIdentityIndex, verifier: JellyfinItemAccessApiVerifier, servers: ServerRepository) :
        this(
            index,
            verifier,
            { servers.current.value },
            { owner ->
                servers.apiClient.accessToken == owner.user.accessToken &&
                    servers.apiClient.baseUrl?.trimEnd('/') == owner.server.url.trimEnd('/')
            },
        )

    /** Null means the session changed; the caller must not navigate at all. */
    suspend fun resolve(
        item: DiscoverItem,
        owner: CurrentUser,
    ): Destination? {
        if (!isCurrent(owner)) return null
        val fallback = Destination.DiscoveredItem(item)
        val kind = item.type.baseItemKind
        if (item.id <= 0 || kind !in listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)) return fallback
        val ids =
            try {
                (index.lookup(kind!!, item.id) as? JellyfinIdentityLookup.Candidates)?.itemIds.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        if (!isCurrent(owner)) return null
        if (ids.isEmpty() || ids.size > MAX_CANDIDATES) return fallback
        val verified =
            try {
                withTimeoutOrNull(VERIFY_TIMEOUT_MS) { verifier.verifiedIds(owner, kind!!, item.id, ids) }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        if (!isCurrent(owner)) return null
        return verified.singleOrNull()?.let { Destination.MediaItem(it, kind!!) } ?: fallback
    }

    private fun isCurrent(owner: CurrentUser) = session() === owner && authenticatedAs(owner)

    companion object {
        internal const val VERIFY_TIMEOUT_MS = 4_000L
        private const val MAX_CANDIDATES = 20
    }
}
