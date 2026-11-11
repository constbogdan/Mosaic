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

enum class WatchlistFilter { ALL, YES, NO }

data class WatchlistFilters(
    val owned: WatchlistFilter = WatchlistFilter.ALL,
    val watched: WatchlistFilter = WatchlistFilter.ALL,
) {
    val libraryStatus: String get() =
        when (owned) {
            WatchlistFilter.ALL -> "any"

            WatchlistFilter.YES -> "active"

            // Seerr's absent excludes archived titles; this is deliberately not !active.
            WatchlistFilter.NO -> "absent"
        }
    val watchedQuery: String get() =
        when (watched) {
            WatchlistFilter.ALL -> "all"

            WatchlistFilter.YES -> "watched"

            // Preserve Seerr's accepted inclusion of Unknown playback state.
            WatchlistFilter.NO -> "not_watched"
        }

    fun encode(): String = "${owned.name}:${watched.name}"

    companion object {
        fun decode(value: String?): WatchlistFilters {
            val parts = value.orEmpty().split(':')

            fun selection(index: Int) = WatchlistFilter.entries.firstOrNull { it.name == parts.getOrNull(index) } ?: WatchlistFilter.ALL
            return WatchlistFilters(selection(0), selection(1))
        }
    }
}

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
