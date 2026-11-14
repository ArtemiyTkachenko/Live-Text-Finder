package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Composes the app's retained-search regions in their measured vertical order. */
@Composable
internal fun FinderContentAssembly(
    scrollState: ScrollState,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
    topContent: @Composable ColumnScope.() -> Unit,
    managerContent: @Composable ColumnScope.() -> Unit,
    viewportContent: @Composable ColumnScope.() -> Unit,
    footerContent: @Composable ColumnScope.() -> Unit,
) {
    AppContentColumn(scrollState, scrollable, modifier) {
        topContent()
        managerContent()
        viewportContent()
        footerContent()
    }
}
