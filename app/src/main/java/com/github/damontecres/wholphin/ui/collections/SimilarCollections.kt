package com.github.damontecres.wholphin.ui.collections

/** Keep the server's relationship order, without treating invalid IDs as displayable collections. */
internal fun verifiedSimilarIds(
    originId: Int,
    relatedIds: List<Int>,
): List<Int> = relatedIds.filter { it > 0 && it != originId }.distinct()

internal const val SIMILAR_POLL_INTERVAL_MS = 500L
internal const val SIMILAR_OBSERVATION_WINDOW_MS = 24_000L
internal const val SIMILAR_INACTIVE_GRACE_POLLS = 2
