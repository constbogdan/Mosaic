package com.github.damontecres.wholphin.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.CollectionDetailsData
import com.github.damontecres.wholphin.ui.components.SimpleStarRating
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import java.time.LocalDate
import java.util.Locale

/** Uses the same full-width fixed-column geometry as CardGrid's member cells. */
internal fun similarPosterWidth(
    gridWidth: androidx.compose.ui.unit.Dp,
    columns: Int,
    spacing: androidx.compose.ui.unit.Dp = 16.dp,
): androidx.compose.ui.unit.Dp = (gridWidth - spacing * (columns - 1)) / columns

internal sealed interface CollectionPreviewSelection {
    data object Collection : CollectionPreviewSelection

    data class Movie(
        val id: Int,
    ) : CollectionPreviewSelection

    data class Similar(
        val id: Int,
    ) : CollectionPreviewSelection
}

/** The selected section survives Details navigation without waiting for a focus callback. */
internal data class CollectionPreviewFocusState(
    val selection: CollectionPreviewSelection = CollectionPreviewSelection.Collection,
    val lastMemberId: Int? = null,
) {
    fun memberFocused(id: Int): CollectionPreviewFocusState = copy(selection = CollectionPreviewSelection.Movie(id), lastMemberId = id)

    fun similarFocused(id: Int): CollectionPreviewFocusState = copy(selection = CollectionPreviewSelection.Similar(id))

    /** Similar lives inside CardGrid's focus group, so returning to its prior member may not emit a new item-focus callback. */
    fun returningToMember(id: Int): CollectionPreviewFocusState = memberFocused(id)

    fun posterFocused(): CollectionPreviewFocusState = copy(selection = CollectionPreviewSelection.Collection, lastMemberId = null)
}

/** Keep the focused preview when a member's Details route temporarily removes this page from composition. */
internal val CollectionPreviewFocusStateSaver =
    listSaver<CollectionPreviewFocusState, Int>(
        save = { state ->
            val (kind, id) =
                when (val selected = state.selection) {
                    CollectionPreviewSelection.Collection -> 0 to 0
                    is CollectionPreviewSelection.Movie -> 1 to selected.id
                    is CollectionPreviewSelection.Similar -> 2 to selected.id
                }
            listOf(kind, id, state.lastMemberId ?: 0)
        },
        restore = { values ->
            CollectionPreviewFocusState(
                selection =
                    when (values[0]) {
                        1 -> CollectionPreviewSelection.Movie(values[1])
                        2 -> CollectionPreviewSelection.Similar(values[1])
                        else -> CollectionPreviewSelection.Collection
                    },
                lastMemberId = values[2].takeIf { it > 0 },
            )
        },
    )

internal enum class CollectionPreviewKind { COLLECTION, MOVIE, SIMILAR }

internal data class CollectionPreviewData(
    val kind: CollectionPreviewKind,
    val title: String,
    val overview: String?,
    val artworkUrl: String?,
    val releaseDate: LocalDate? = null,
    val availability: SeerrAvailability? = null,
    val availableMovies: Int? = null,
    val totalMovies: Int? = null,
    val genreIds: List<Int> = emptyList(),
    val rating: Double? = null,
)

internal data class CollectionBackdropTarget(
    val key: String,
    val url: String?,
)

internal fun similarSelectionNeedsScroll(
    firstVisibleIndex: Int,
    columns: Int,
    targetIndex: Int,
): Boolean = targetIndex !in firstVisibleIndex until firstVisibleIndex + columns

/** Backdrops use the current section's already-loaded artwork; never substitute a different item's poster. */
internal fun collectionBackdropTarget(
    collection: CollectionDetailsData,
    similar: List<CollectionDetailsData>,
    selection: CollectionPreviewSelection,
): CollectionBackdropTarget =
    when (selection) {
        CollectionPreviewSelection.Collection -> {
            CollectionBackdropTarget("tmdb:collection:${collection.id}", collection.backdropUrl)
        }

        is CollectionPreviewSelection.Movie -> {
            CollectionBackdropTarget(
                "tmdb:movie:${selection.id}",
                collection.movies.firstOrNull { it.id == selection.id }?.backDropUrl,
            )
        }

        is CollectionPreviewSelection.Similar -> {
            CollectionBackdropTarget(
                "tmdb:collection:${selection.id}",
                similar.firstOrNull { it.id == selection.id }?.backdropUrl,
            )
        }
    }

@Composable
internal fun CollectionDetailsOverview(
    title: String,
    overview: String?,
    metadata: CollectionMemberMetadata,
    genreNames: Map<Int, String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(190.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag("collectionOverviewTitle"),
        )
        CollectionMetadataLine(null, metadata.rating, metadata.genreIds, genreNames)
        overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.64f).weight(1f, fill = false).testTag("collectionOverviewDescription"),
            )
        }
    }
}

/** Resolves only metadata already loaded for this visit; a missing target restores collection context. */
internal fun collectionPreviewData(
    collection: CollectionDetailsData,
    similar: List<CollectionDetailsData>,
    selection: CollectionPreviewSelection,
): CollectionPreviewData =
    when (selection) {
        CollectionPreviewSelection.Collection -> {
            collection.collectionPreview()
        }

        is CollectionPreviewSelection.Movie -> {
            collection.movies.firstOrNull { it.id == selection.id }?.let { movie ->
                CollectionPreviewData(
                    kind = CollectionPreviewKind.MOVIE,
                    title = movie.title.orEmpty(),
                    overview = movie.overview,
                    artworkUrl = movie.posterUrl ?: movie.backDropUrl,
                    releaseDate = movie.releaseDate,
                    availability = movie.availability,
                    genreIds = movie.genreIds.orEmpty(),
                    rating = validMovieRating(movie),
                )
            } ?: collection.collectionPreview()
        }

        is CollectionPreviewSelection.Similar -> {
            similar.firstOrNull { it.id == selection.id }?.let {
                it.collectionPreview(CollectionPreviewKind.SIMILAR)
            } ?: collection.collectionPreview()
        }
    }

private fun CollectionDetailsData.collectionPreview(kind: CollectionPreviewKind = CollectionPreviewKind.COLLECTION): CollectionPreviewData {
    val metadata = collectionMemberMetadata(movies)
    return CollectionPreviewData(
        kind = kind,
        title = name,
        overview = overview,
        artworkUrl = if (kind == CollectionPreviewKind.COLLECTION) backdropUrl ?: posterUrl else posterUrl ?: backdropUrl,
        availableMovies = availableMovies,
        totalMovies = totalMovies,
        genreIds = metadata.genreIds,
        rating = metadata.rating,
    )
}

@Composable
internal fun CollectionDetailsPreview(
    preview: CollectionPreviewData,
    genreNames: Map<Int, String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(190.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        TitleOrLogo(
            title = preview.title,
            logoImageUrl = null,
            showLogo = false,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("collectionPreviewTitle"),
        )
        CollectionMetadataLine(preview.releaseDate?.year, preview.rating, preview.genreIds, genreNames)
        preview.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(.8f).weight(1f, fill = false).testTag("collectionPreviewOverview"),
            )
        }
    }
}

@Composable
internal fun CollectionMetadataLine(
    year: Int?,
    rating: Double?,
    genreIds: List<Int>,
    genreNames: Map<Int, String>,
) {
    val genres = genreIds.mapNotNull(genreNames::get).joinToString(", ")
    if (year == null && rating == null && genres.isEmpty()) return
    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("collectionMetadataLine"),
        ) {
            year?.let { Text(it.toString(), maxLines = 1) }
            if (year != null && (rating != null || genres.isNotEmpty())) Text("·", maxLines = 1)
            rating?.let { SimpleStarRating(String.format(Locale.getDefault(), "%.1f", it)) }
            if (rating != null && genres.isNotEmpty()) Text("·", maxLines = 1)
            if (genres.isNotEmpty()) {
                Text(genres, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
