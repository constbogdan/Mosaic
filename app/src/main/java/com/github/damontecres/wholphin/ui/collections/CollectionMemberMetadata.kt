package com.github.damontecres.wholphin.ui.collections

import com.github.damontecres.wholphin.data.model.DiscoverItem
import java.time.LocalDate

internal data class CollectionMemberMetadata(
    val genreIds: List<Int>,
    val rating: Double?,
)

/** A two-thirds coverage floor avoids presenting a small known subset as the whole collection. */
internal fun collectionMemberMetadata(
    members: List<DiscoverItem>,
    today: LocalDate = LocalDate.now(),
): CollectionMemberMetadata {
    val distinct = members.distinctBy { it.id }
    if (distinct.size == 1) {
        val member = distinct.single()
        return CollectionMemberMetadata(
            member.genreIds
                .orEmpty()
                .distinct()
                .take(3),
            validMovieRating(member),
        )
    }
    val relevant = distinct.filter { it.releaseDate == null || !it.releaseDate.isAfter(today) }
    if (relevant.isEmpty()) return CollectionMemberMetadata(emptyList(), null)

    val withGenres = relevant.filter { it.releaseDate != null && !it.genreIds.isNullOrEmpty() }
    val genres =
        if (withGenres.size * 3 >= relevant.size * 2) {
            withGenres
                .flatMap { it.genreIds.orEmpty().distinct() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .take(3)
                .map { it.key }
        } else {
            emptyList()
        }

    val rated =
        relevant.filter { item ->
            item.releaseDate != null && item.voteCount != null && item.voteCount > 0 &&
                item.voteAverage != null && item.voteAverage.isFinite() && item.voteAverage > 0.0 && item.voteAverage <= 10.0
        }
    val rating =
        if (rated.size * 3 >= relevant.size * 2) {
            val votes = rated.sumOf { it.voteCount!!.toLong() }
            if (votes > 0) rated.sumOf { it.voteAverage!! * it.voteCount!! } / votes else null
        } else {
            null
        }
    return CollectionMemberMetadata(genres, rating)
}

internal fun validMovieRating(item: DiscoverItem): Double? =
    item.voteAverage?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 && (item.voteCount ?: 0) > 0 }
