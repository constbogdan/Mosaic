package com.github.damontecres.wholphin.ui.collections

import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.CollectionDetailsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CollectionDetailsPreviewTest {
    @Test fun bothHeaderModesReserveTheSameGridPositionAndPosterKeepsItsAspectRatio() {
        assertEquals(226.dp, collectionHeaderHeight)
        assertEquals(115.dp, collectionOverviewPosterWidth)
        assertTrue(collectionOverviewPosterWidth < 140.dp)
    }

    private fun movie(
        id: Int,
        title: String? = "Movie $id",
        overview: String? = null,
        artwork: String? = null,
        availability: SeerrAvailability = SeerrAvailability.UNKNOWN,
        date: LocalDate? = null,
    ) = DiscoverItem(id, SeerrItemType.MOVIE, title, null, overview, availability, date, artwork, null, null, null)

    private fun collection(vararg movies: DiscoverItem) =
        CollectionDetailsData(
            7,
            "Active Collection",
            "Collection overview",
            "collection-poster",
            "collection-backdrop",
            movies.toList(),
            availableMovies = 2,
            totalMovies = 4,
        )

    @Test fun explicitSectionTransitionsSelectTheCorrectPreview() {
        val first = CollectionPreviewFocusState().memberFocused(11)
        assertEquals(CollectionPreviewSelection.Movie(11), first.selection)
        assertEquals(first, first.memberFocused(11))
        assertEquals(CollectionPreviewSelection.Movie(12), first.memberFocused(12).selection)
        assertEquals(CollectionPreviewSelection.Collection, first.memberFocused(12).posterFocused().selection)
        assertEquals(
            CollectionPreviewSelection.Movie(12),
            first
                .memberFocused(12)
                .posterFocused()
                .memberFocused(12)
                .selection,
        )
    }

    @Test fun entrySectionComesFromSavedPreviewInsteadOfTemporarilyShowingOverview() {
        assertEquals(CollectionFocusSection.POSTER, collectionEntryFocusSection(CollectionPreviewFocusState().selection))
        assertEquals(CollectionFocusSection.MOVIES, collectionEntryFocusSection(CollectionPreviewSelection.Movie(11)))
        assertEquals(CollectionFocusSection.SIMILAR, collectionEntryFocusSection(CollectionPreviewSelection.Similar(8)))
    }

    @Test fun rapidFocusChangesResolveOnlyTheLastLoadedIdentity() {
        val details = collection(movie(11), movie(12), movie(13))
        val state = CollectionPreviewFocusState().memberFocused(11).memberFocused(12).memberFocused(13)
        assertEquals("Movie 13", collectionPreviewData(details, emptyList(), state.selection).title)
        assertEquals(
            CollectionPreviewKind.COLLECTION,
            collectionPreviewData(details, emptyList(), CollectionPreviewSelection.Movie(99)).kind,
        )
    }

    @Test fun returningFromSimilarRestoresThePriorMemberPreview() {
        val state = CollectionPreviewFocusState().memberFocused(11).memberFocused(12).similarFocused(8)
        assertEquals(CollectionPreviewSelection.Similar(8), state.selection)
        assertEquals(12, state.lastMemberId)
        assertEquals(CollectionPreviewSelection.Movie(12), state.memberFocused(12).selection)
        assertEquals(CollectionPreviewSelection.Movie(12), state.returningToMember(12).selection)
    }

    @Test fun moviePreviewUsesOnlyLoadedMetadataAndDoesNotInventCounts() {
        val date = LocalDate.of(2027, 3, 2)
        val details = collection(movie(11, "First", "Synopsis", "movie-poster", SeerrAvailability.PARTIALLY_AVAILABLE, date))
        val preview = collectionPreviewData(details, emptyList(), CollectionPreviewSelection.Movie(11))
        assertEquals(CollectionPreviewKind.MOVIE, preview.kind)
        assertEquals("Synopsis", preview.overview)
        assertEquals("movie-poster", preview.artworkUrl)
        assertEquals(date, preview.releaseDate)
        assertEquals(SeerrAvailability.PARTIALLY_AVAILABLE, preview.availability)
        assertNull(preview.availableMovies)
        assertNull(preview.totalMovies)
    }

    @Test fun movieAndRecommendedPreviewsUseOnlyRetainedMemberMetadata() {
        val released = LocalDate.of(2020, 1, 1)
        val member = movie(11, date = released).copy(genreIds = listOf(28, 12), voteAverage = 7.4, voteCount = 100)
        val details = collection(member)
        val moviePreview = collectionPreviewData(details, emptyList(), CollectionPreviewSelection.Movie(11))
        assertEquals(listOf(28, 12), moviePreview.genreIds)
        assertEquals(7.4, moviePreview.rating!!, 0.0)
        val related = CollectionDetailsData(8, "Related", null, null, null, listOf(member))
        val recommendedPreview = collectionPreviewData(details, listOf(related), CollectionPreviewSelection.Similar(8))
        assertEquals(listOf(28, 12), recommendedPreview.genreIds)
        assertEquals(7.4, recommendedPreview.rating!!, 0.0)
    }

    @Test fun oneMemberCollectionMetadataMatchesInOverviewAndRecommendedPreview() {
        val only = movie(11).copy(genreIds = listOf(28, 12), voteAverage = 7.4, voteCount = 42)
        val details = collection(only)
        val overview = collectionPreviewData(details, emptyList(), CollectionPreviewSelection.Collection)
        val recommended = collectionPreviewData(details, listOf(details), CollectionPreviewSelection.Similar(details.id))
        assertEquals(listOf(28, 12), overview.genreIds)
        assertEquals(7.4, overview.rating!!, 0.0)
        assertEquals(overview.genreIds, recommended.genreIds)
        assertEquals(overview.rating, recommended.rating)
    }

    @Test fun similarPreviewRetainsNullableAuthoritativeCountsAndMissingArtwork() {
        val details = collection(movie(11))
        val related = CollectionDetailsData(8, "Related", null, null, null, emptyList())
        val preview = collectionPreviewData(details, listOf(related), CollectionPreviewSelection.Similar(8))
        assertEquals(CollectionPreviewKind.SIMILAR, preview.kind)
        assertEquals("Related", preview.title)
        assertNull(preview.artworkUrl)
        assertNull(preview.availableMovies)
        assertNull(preview.totalMovies)
        assertEquals(
            CollectionPreviewKind.COLLECTION,
            collectionPreviewData(details, emptyList(), CollectionPreviewSelection.Similar(8)).kind,
        )
    }

    @Test fun collectionPreviewStartsWithContextAndExistingBackdrop() {
        val preview = collectionPreviewData(collection(), emptyList(), CollectionPreviewSelection.Collection)
        assertEquals("Active Collection", preview.title)
        assertEquals("Collection overview", preview.overview)
        assertEquals("collection-backdrop", preview.artworkUrl)
        assertEquals(2, preview.availableMovies)
        assertEquals(4, preview.totalMovies)
    }

    @Test fun backdropFollowsLoadedSelectionAndNeverFallsBackToAnotherItemsArtwork() {
        val details = collection(movie(11).copy(backDropUrl = "movie-backdrop"), movie(12))
        val similar = listOf(CollectionDetailsData(8, "Related", null, "related-poster", "related-backdrop", emptyList()))
        assertEquals(
            CollectionBackdropTarget("tmdb:collection:7", "collection-backdrop"),
            collectionBackdropTarget(details, similar, CollectionPreviewSelection.Collection),
        )
        assertEquals(
            CollectionBackdropTarget("tmdb:movie:11", "movie-backdrop"),
            collectionBackdropTarget(details, similar, CollectionPreviewSelection.Movie(11)),
        )
        assertEquals(
            CollectionBackdropTarget("tmdb:movie:12", null),
            collectionBackdropTarget(details, similar, CollectionPreviewSelection.Movie(12)),
        )
        assertEquals(
            CollectionBackdropTarget("tmdb:collection:8", "related-backdrop"),
            collectionBackdropTarget(details, similar, CollectionPreviewSelection.Similar(8)),
        )
        assertEquals(
            CollectionBackdropTarget("tmdb:collection:9", null),
            collectionBackdropTarget(details, similar, CollectionPreviewSelection.Similar(9)),
        )
    }

    @Test fun relatedPosterWidthMatchesSixColumnGridGeometry() {
        assertEquals(100.dp, similarPosterWidth(680.dp, 6))
        assertEquals(100.dp, similarPosterWidth(332.dp, 3))
    }

    @Test fun visibleSimilarSelectionKeepsItsExistingHorizontalOffset() {
        assertFalse(similarSelectionNeedsScroll(firstVisibleIndex = 0, columns = 6, targetIndex = 1))
        assertFalse(similarSelectionNeedsScroll(firstVisibleIndex = 3, columns = 6, targetIndex = 8))
        assertTrue(similarSelectionNeedsScroll(firstVisibleIndex = 0, columns = 6, targetIndex = 6))
        assertTrue(similarSelectionNeedsScroll(firstVisibleIndex = 3, columns = 6, targetIndex = 2))
    }
}
