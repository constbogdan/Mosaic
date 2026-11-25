package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** A stable focus target while a catalog page replaces its loading content with a lazy grid. */
@Composable
fun rememberCustomPageFocusOwner(
    isTop: Boolean,
    contentReady: Boolean,
    mayFocusGrid: Boolean,
    gridFocusRequester: FocusRequester,
    ribbonVisible: Boolean = false,
    ribbonFocusRequester: FocusRequester? = null,
    claimInitialFocus: Boolean = true,
): Modifier {
    val pageFocusRequester = remember { FocusRequester() }
    var pageHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(pageFocusRequester) {
        if (isTop && claimInitialFocus) pageFocusRequester.tryRequestFocus()
    }
    LaunchedEffect(isTop, contentReady, mayFocusGrid, pageHasFocus, gridFocusRequester, ribbonVisible, ribbonFocusRequester) {
        if (isTop && pageHasFocus) {
            if (ribbonVisible) {
                ribbonFocusRequester?.tryRequestFocus()
            } else if (contentReady && mayFocusGrid) {
                gridFocusRequester.tryRequestFocus()
            }
        }
    }

    return Modifier
        .focusRequester(pageFocusRequester)
        .onFocusChanged { pageHasFocus = it.isFocused }
        .focusProperties {
            onEnter = {
                if (isTop) {
                    if (ribbonVisible) {
                        ribbonFocusRequester?.tryRequestFocus()
                    } else if (contentReady && mayFocusGrid) {
                        gridFocusRequester.tryRequestFocus()
                    }
                }
            }
        }.focusable()
}
