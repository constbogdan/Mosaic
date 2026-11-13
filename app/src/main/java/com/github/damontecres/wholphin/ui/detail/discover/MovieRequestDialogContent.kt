package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.PopcornRating
import com.github.damontecres.wholphin.ui.components.TomatoRating
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** The same quality choices and layout are used by details and card Quick Request. */
@Composable
internal fun MovieRequestDialogContent(
    loading: LoadingState,
    data: SeerrRequestData,
    requestEnabled: Boolean,
    movie: MovieDetails?,
    posterUrl: String?,
    rating: DiscoverRating?,
    fallbackItem: DiscoverItem? = null,
    confirmationReady: Boolean,
    onSubmit: (MovieRequest) -> Unit,
    confirmAction: () -> Boolean,
    onPosterClick: (() -> Unit)? = null,
) {
    val movieId = movie?.id
    val title = movie?.title ?: fallbackItem?.title
    val year = movie?.releaseDate?.take(4) ?: fallbackItem?.releaseDate?.year?.toString()
    val overview = movie?.overview?.takeIf { it.isNotBlank() } ?: fallbackItem?.overview.orEmpty()
    val action1080 = remember(data, movieId) { movieRequestForQuality(data, movieId, MOVIE_PROFILE_1080P_HDR) }
    val action2160 = remember(data, movieId) { movieRequestForQuality(data, movieId, MOVIE_PROFILE_2160P_HDR) }
    var submitted by remember(movieId, data) { mutableStateOf(false) }
    var initialFocusAssigned by remember(movieId, fallbackItem?.id) { mutableStateOf(false) }
    var focusedQuality by remember { mutableIntStateOf(0) }
    val synopsisScroll = rememberScrollState()
    val synopsisPixelsPerSecond = with(LocalDensity.current) { 18.dp.toPx() }
    val firstFocus = remember { FocusRequester() }
    val secondFocus = remember { FocusRequester() }
    val posterFocus = remember { FocusRequester() }
    var posterFocused by remember { mutableStateOf(false) }
    val posterInteraction = remember { MutableInteractionSource() }
    val can1080 = loading == LoadingState.Success && requestEnabled && confirmationReady && !submitted && action1080 != null
    val can2160 = loading == LoadingState.Success && requestEnabled && confirmationReady && !submitted && action2160 != null

    val canPosterFallback = onPosterClick != null && loading == LoadingState.Success && !can1080 && !can2160
    LaunchedEffect(can1080, can2160, canPosterFallback, movieId) {
        if (!initialFocusAssigned && (can1080 || can2160 || canPosterFallback)) {
            val target =
                if (can1080) {
                    firstFocus
                } else if (can2160) {
                    secondFocus
                } else {
                    posterFocus
                }
            repeat(3) {
                withFrameNanos { }
                if (target.tryRequestFocus()) {
                    initialFocusAssigned = true
                    return@LaunchedEffect
                }
            }
        }
    }
    LaunchedEffect(
        movieId,
        fallbackItem?.id,
        overview,
        loading,
        synopsisScroll.maxValue,
        synopsisScroll.viewportSize,
        synopsisPixelsPerSecond,
    ) {
        synopsisScroll.scrollTo(0)
        val overflow = synopsisScroll.maxValue
        if (loading == LoadingState.Success && synopsisScroll.viewportSize > 0 && overflow > 0 && overflow != Int.MAX_VALUE) {
            runMovieSynopsisAutoScroll(overflow, synopsisPixelsPerSecond) { target, duration ->
                synopsisScroll.animateScrollTo(target, animationSpec = tween(durationMillis = duration, easing = LinearEasing))
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        modifier =
            Modifier
                .width(860.dp)
                .height(450.dp)
                .testTag("movieRequestDialogShell")
                .padding(20.dp),
    ) {
        val posterShape = RoundedCornerShape(16.dp)
        val posterDescription = stringResource(R.string.view_movie_details, title.orEmpty())
        val posterActionModifier =
            if (onPosterClick != null) {
                Modifier
                    .testTag("movieRequestPoster")
                    .focusRequester(posterFocus)
                    .focusProperties {
                        right =
                            if (can1080) {
                                firstFocus
                            } else if (can2160) {
                                secondFocus
                            } else {
                                FocusRequester.Default
                            }
                    }.semantics { contentDescription = posterDescription }
                    .onFocusChanged { posterFocused = it.isFocused }
                    .clickable(interactionSource = posterInteraction, indication = null, onClick = onPosterClick)
            } else {
                Modifier
            }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(272.dp)
                    .height(408.dp)
                    .clip(posterShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(posterActionModifier),
        ) {
            Text(title.orEmpty(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
            val imageUrl = posterUrl ?: fallbackItem?.posterUrl
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (posterFocused && onPosterClick != null) {
                Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.border, posterShape))
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                val facts =
                    listOfNotNull(
                        year,
                        movie
                            ?.genres
                            ?.mapNotNull { it.name }
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(" / "),
                    )
                Text(
                    facts.joinToString("  -  "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(184.dp).height(32.dp).testTag("movieRequestRatingSlot"),
                ) {
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier.width(88.dp).height(32.dp).testTag("movieRequestCriticsSlot"),
                    ) {
                        TomatoRating(rating?.criticRating?.toFloat())
                    }
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier.width(88.dp).height(32.dp).testTag("movieRequestAudienceSlot"),
                    ) {
                        PopcornRating(rating?.audienceScore)
                    }
                }
            }
            OriginalLanguageText(movie?.originalLanguage, Modifier.padding(top = 6.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 26.dp, bottom = 18.dp)) {
                when (loading) {
                    is LoadingState.Error -> {
                        ErrorMessage(loading, Modifier)
                    }

                    LoadingState.Loading, LoadingState.Pending -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.border,
                            modifier = Modifier.align(Alignment.Center).size(48.dp),
                        )
                    }

                    LoadingState.Success -> {
                        Column(Modifier.fillMaxSize().verticalScroll(synopsisScroll).testTag("movieRequestSynopsis")) {
                            Text(overview, style = MaterialTheme.typography.bodyLarge)
                            if (requestEnabled && action1080 == null && action2160 == null) {
                                Text("1080p HDR and 2160p HDR profiles unavailable", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth(),
            ) {
                QualityButton(
                    R.drawable.request_1080p,
                    "1080p HDR",
                    can1080,
                    Modifier.focusRequester(firstFocus).focusProperties {
                        left =
                            posterFocus
                    },
                    { focusedQuality = 1 },
                ) {
                    val request = action1080 ?: return@QualityButton
                    if (submitted || !confirmAction()) return@QualityButton
                    submitted = true
                    onSubmit(request)
                }
                QualityButton(
                    R.drawable.request_2160p,
                    "2160p HDR",
                    can2160,
                    Modifier.focusRequester(secondFocus).focusProperties { left = if (can1080) firstFocus else posterFocus },
                    { focusedQuality = 2 },
                ) {
                    val request = action2160 ?: return@QualityButton
                    if (submitted || !confirmAction()) return@QualityButton
                    submitted = true
                    onSubmit(request)
                }
            }
        }
    }
}

/** One layout-scoped loop: cancellation of its LaunchedEffect also cancels an in-flight scroll. */
internal suspend fun runMovieSynopsisAutoScroll(
    maxScrollPx: Int,
    pixelsPerSecond: Float,
    animateTo: suspend (targetPx: Int, durationMillis: Int) -> Unit,
) {
    if (maxScrollPx <= 0 || !pixelsPerSecond.isFinite() || pixelsPerSecond <= 0f) return
    val durationMillis =
        (maxScrollPx.toDouble() * 1000 / pixelsPerSecond.toDouble())
            .roundToLong()
            .coerceIn(1, Int.MAX_VALUE.toLong())
            .toInt()
    delay(5000)
    while (true) {
        animateTo(maxScrollPx, durationMillis)
        delay(2000)
        animateTo(0, durationMillis)
        delay(5000)
    }
}

@Composable
private fun QualityButton(
    image: Int,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .semantics {
                    contentDescription = description
                }.onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }.size(width = 144.dp, height = 82.dp)
                .scale(if (focused && enabled) 1.06f else 1f)
                .alpha(
                    if (!enabled) {
                        0.42f
                    } else if (focused) {
                        1f
                    } else {
                        0.8f
                    },
                ).clickable(enabled = enabled, interactionSource = interactions, indication = null, onClick = onClick),
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            // The supplied SVG has transparent rounded corners, but its PNG export is white there.
            // Clip only the artwork to its original corner radius; do not add a button container.
            modifier = Modifier.width(132.dp).height(70.dp).clip(RoundedCornerShape(11.dp)),
        )
    }
}
