package com.github.damontecres.wholphin.ui.collections

import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CollectionMemberMetadataTest {
    private val today = LocalDate.of(2026, 11, 26)

    private fun movie(
        id: Int,
        genres: List<Int>? = null,
        rating: Double? = null,
        votes: Int? = null,
        date: LocalDate? = today,
    ) = DiscoverItem(
        id,
        SeerrItemType.MOVIE,
        "Movie $id",
        null,
        null,
        SeerrAvailability.UNKNOWN,
        date,
        null,
        null,
        null,
        null,
        genres,
        rating,
        votes,
    )

    @Test fun weightedRatingUsesVotesNotArithmeticMean() {
        val members = listOf(movie(1, rating = 8.0, votes = 100), movie(2, rating = 6.0, votes = 10))
        assertEquals((8.0 * 100 + 6.0 * 10) / 110, collectionMemberMetadata(members, today).rating!!, 0.0001)
    }

    @Test fun singleDistinctMemberUsesItsMetadataWithoutMultiMemberCoverage() {
        val only = movie(1, genres = listOf(28, 12, 28, 16, 99), rating = 7.4, votes = 42, date = null)
        val expected = CollectionMemberMetadata(listOf(28, 12, 16), 7.4)
        assertEquals(expected, collectionMemberMetadata(listOf(only), today))
        assertEquals(expected, collectionMemberMetadata(listOf(only, only), today))
        assertEquals(expected, collectionMemberMetadata(listOf(only.copy(releaseDate = today.plusDays(1))), today))
    }

    @Test fun singleMemberMissingOrUnratedMetadataRemainsAbsent() {
        assertEquals(CollectionMemberMetadata(emptyList(), null), collectionMemberMetadata(listOf(movie(1)), today))
        assertEquals(CollectionMemberMetadata(listOf(28), null), collectionMemberMetadata(listOf(movie(1, listOf(28), 7.0, 0)), today))
        assertEquals(CollectionMemberMetadata(listOf(28), null), collectionMemberMetadata(listOf(movie(1, listOf(28), 11.0, 2)), today))
    }

    @Test fun insufficientCoverageHidesAggregate() {
        val members = listOf(movie(1, listOf(1), 8.0, 100), movie(2), movie(3), movie(4))
        assertNull(collectionMemberMetadata(members, today).rating)
        assertEquals(emptyList<Int>(), collectionMemberMetadata(members, today).genreIds)
    }

    @Test fun genreTiesUseAscendingIdAndDuplicateMembersDoNotSkewCounts() {
        val members = listOf(movie(1, listOf(8, 7, 8)), movie(1, listOf(8)), movie(2, listOf(7, 8)))
        assertEquals(listOf(7, 8), collectionMemberMetadata(members, today).genreIds)
    }

    @Test fun upcomingAndUnratedMembersDoNotContributeVotes() {
        val members =
            listOf(
                movie(1, rating = 7.0, votes = 10),
                movie(2, rating = 9.0, votes = 0),
                movie(3, rating = 10.0, votes = 1000, date = today.plusDays(1)),
            )
        assertNull(collectionMemberMetadata(members, today).rating)
        assertEquals(7.0, validMovieRating(members[0])!!, 0.0)
        assertNull(validMovieRating(members[1]))
    }

    @Test fun upcomingGenresAreNotIncluded() {
        val members =
            listOf(
                movie(1, genres = listOf(12)),
                movie(2, genres = listOf(12)),
                movie(3, genres = listOf(99), date = today.plusDays(1)),
            )
        assertEquals(listOf(12), collectionMemberMetadata(members, today).genreIds)
    }

    @Test fun invalidAndUnknownReleaseMetadataFailClosed() {
        val members =
            listOf(
                movie(1, rating = Double.NaN, votes = 10),
                movie(2, rating = 11.0, votes = 10),
                movie(3, rating = 9.0, votes = 10, date = null),
            )
        assertNull(collectionMemberMetadata(members, today).rating)
    }
}
