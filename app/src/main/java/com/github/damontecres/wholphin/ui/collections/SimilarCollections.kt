package com.github.damontecres.wholphin.ui.collections

import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.SimilarRecommendationsPage
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

internal const val SIMILAR_COLLECTION_LIMIT = 10
internal const val SIMILAR_RECOMMENDATION_PAGE_LIMIT = 3

/** A missing date is not evidence that a collection member has been released. */
internal fun chooseReleasedSeed(
    movies: List<DiscoverItem>,
    today: LocalDate = LocalDate.now(),
    chooseIndex: (Int) -> Int,
): Int? {
    val eligible = movies.filter { it.id > 0 && it.releaseDate?.let { date -> date <= today } == true }
    if (eligible.isEmpty()) return null
    return eligible[chooseIndex(eligible.size)].id
}

/** The response order is authoritative; unresolved membership is not confirmed absence. */
internal suspend fun discoverSimilarCollectionIds(
    currentCollectionId: Int,
    fetch: suspend (Int) -> SimilarRecommendationsPage,
): List<Int> {
    val ids = linkedSetOf<Int>()
    for (page in 1..SIMILAR_RECOMMENDATION_PAGE_LIMIT) {
        val response =
            try {
                fetch(page)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                break // Preserve results from earlier pages when optional content fails.
            }
        response.collectionIds.forEach { id ->
            if (id != null && id > 0 && id != currentCollectionId) ids += id
            if (ids.size == SIMILAR_COLLECTION_LIMIT) return ids.toList()
        }
        if (page >= response.totalPages) break
    }
    return ids.toList()
}
