package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.ui.detail.CardGridItem
import java.time.Instant

enum class WatchlistCategory(
    val query: String,
) {
    ALL("all"),
    MOVIES("movies"),
    SHOWS("series"),
    ANIMATION("animation"),
}

enum class WatchlistMembership { UNKNOWN, MEMBER, NOT_MEMBER }

data class WatchlistEntry(
    val key: MediaKey.Catalog,
    val title: String,
    val addedAt: Instant,
    val posterPath: String?,
    val backdropPath: String?,
    val genreIds: List<Int>?,
    val item: DiscoverItem,
) : CardGridItem {
    override val gridId: String get() = "${key.mediaType}:${key.tmdbId}"
    override val playable: Boolean = false
    override val sortName: String get() = addedAt.toString()
}

data class WatchlistPage(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val entries: List<WatchlistEntry>,
)

internal fun watchlistFocusAfterRemoval(
    index: Int,
    remaining: Int,
): Int = index.coerceIn(0, (remaining - 1).coerceAtLeast(0))
