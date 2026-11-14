package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A column that scrolls as one page only when the preview is hidden. */
@Composable
internal fun AppContentColumn(
    scrollState: ScrollState,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.then(
            if (scrollable) Modifier.verticalScroll(scrollState) else Modifier,
        ),
        content = content,
    )
}
