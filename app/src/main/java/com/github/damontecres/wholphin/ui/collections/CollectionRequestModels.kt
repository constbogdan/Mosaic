package com.github.damontecres.wholphin.ui.collections

import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.canRequestMovie
import com.github.damontecres.wholphin.ui.toLocalDate
import java.time.LocalDate

internal enum class CollectionRequestEligibility {
    ELIGIBLE,
    REQUESTED,
    PROCESSING,
    AVAILABLE,
    EXCLUDED,
    UNKNOWN,
}

/** Collection membership alone never proves requestability; inspect the movie's current Seerr details. */
internal fun collectionRequestEligibility(
    item: DiscoverItem,
    movie: MovieDetails,
    current: CurrentSeerr?,
    today: LocalDate = LocalDate.now(),
): CollectionRequestEligibility {
    if (item.id <= 0 || movie.id != item.id) return CollectionRequestEligibility.UNKNOWN
    val released = item.releaseDate ?: return CollectionRequestEligibility.UNKNOWN
    if (released > today) return CollectionRequestEligibility.EXCLUDED
    movie.releaseDate?.let { date ->
        val verified = toLocalDate(date) ?: return CollectionRequestEligibility.UNKNOWN
        if (verified > today) return CollectionRequestEligibility.EXCLUDED
    }
    val status = SeerrAvailability.from(movie.mediaInfo?.status)
    if (status == SeerrAvailability.AVAILABLE || status == SeerrAvailability.PARTIALLY_AVAILABLE) {
        return CollectionRequestEligibility.AVAILABLE
    }
    if (status == SeerrAvailability.BLOCKLISTED) return CollectionRequestEligibility.EXCLUDED
    if (status == SeerrAvailability.PENDING) return CollectionRequestEligibility.REQUESTED
    if (status == SeerrAvailability.PROCESSING) return CollectionRequestEligibility.PROCESSING
    if (current.canRequestMovie(movie, is4k = false)) return CollectionRequestEligibility.ELIGIBLE
    val activeRequest =
        movie.mediaInfo?.requests.orEmpty().any {
            it.is4k != true && it.status != RequestStatus.DECLINED.status
        }
    return if (activeRequest) CollectionRequestEligibility.REQUESTED else CollectionRequestEligibility.EXCLUDED
}

internal enum class CollectionRequestOutcome {
    REQUESTED,
    NO_LONGER_ELIGIBLE,
    FAILED,
    UNKNOWN_RESULT,
    NOT_ATTEMPTED,
}

internal data class CollectionRequestMovie(
    val item: DiscoverItem,
    val eligibility: CollectionRequestEligibility,
    val selected: Boolean = eligibility == CollectionRequestEligibility.ELIGIBLE,
    val outcome: CollectionRequestOutcome? = null,
    val error: String? = null,
) {
    val selectable: Boolean get() =
        eligibility == CollectionRequestEligibility.ELIGIBLE &&
            outcome != CollectionRequestOutcome.REQUESTED && outcome != CollectionRequestOutcome.UNKNOWN_RESULT
}

internal data class CollectionRequestState(
    val owner: String,
    val collectionId: Int,
    val movies: List<CollectionRequestMovie>,
    val loadingProfiles: Boolean = true,
    val profileError: Boolean = false,
    val profiles: com.github.damontecres.wholphin.ui.detail.discover.SeerrRequestData =
        com.github.damontecres.wholphin.ui.detail.discover
            .SeerrRequestData(),
    val submitting: Boolean = false,
    val submitted: Int = 0,
    val failed: Int = 0,
    val sessionInvalid: Boolean = false,
) {
    val selectedCount: Int get() = movies.count { it.selected && it.selectable }
    val eligibleCount: Int get() = movies.count { it.selectable }
    val allSelected: Boolean get() = eligibleCount > 0 && selectedCount == eligibleCount
}
