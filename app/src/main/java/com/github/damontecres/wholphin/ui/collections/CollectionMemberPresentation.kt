package com.github.damontecres.wholphin.ui.collections

import androidx.compose.ui.graphics.ColorMatrix
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import java.time.LocalDate

/** Release date only controls presentation/actionability, never Seerr's authoritative count. */
internal fun collectionMemberActionable(
    item: DiscoverItem,
    today: LocalDate = LocalDate.now(),
): Boolean = item.releaseDate?.let { !it.isAfter(today) } ?: false

internal fun collectionMembersOrdered(
    movies: List<DiscoverItem>,
    today: LocalDate = LocalDate.now(),
): List<DiscoverItem> {
    val priorOrder = movies.sortedWith(compareBy<DiscoverItem> { it.releaseDate == null }.thenBy { it.releaseDate }.thenBy { it.id })
    val (actionable, nonActionable) = priorOrder.partition { collectionMemberActionable(it, today) }
    return actionable + nonActionable
}

internal fun collectionUnreleasedPosterMatrix(): ColorMatrix = ColorMatrix().apply { setToSaturation(0f) }

internal fun collectionHasAvailableReleasedMember(
    movies: List<DiscoverItem>,
    today: LocalDate = LocalDate.now(),
): Boolean = movies.any { collectionMemberActionable(it, today) && it.availability == SeerrAvailability.AVAILABLE }
