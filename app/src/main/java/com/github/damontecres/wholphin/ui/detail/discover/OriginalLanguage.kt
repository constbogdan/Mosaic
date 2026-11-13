package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.util.languageName
import java.util.Locale

private val iso639Languages = Locale.getISOLanguages().toSet()

internal fun originalLanguageName(code: String?): String? {
    val normalized = code?.trim()?.lowercase(Locale.ROOT) ?: return null
    if (normalized.length != 2 || normalized !in iso639Languages) return null
    return languageName(normalized).takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
}

@Composable
internal fun OriginalLanguageText(
    code: String?,
    modifier: Modifier = Modifier,
) {
    originalLanguageName(code)?.let { name ->
        Text(
            text = stringResource(R.string.original_language, name),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
