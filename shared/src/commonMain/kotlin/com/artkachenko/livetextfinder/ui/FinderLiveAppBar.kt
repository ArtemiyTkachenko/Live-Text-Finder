package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderLiveAppBar(onOptions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(
            horizontal = FinderSpacing.large,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Live Text Finder",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
        FinderSecondaryAction(
            label = stringResource(Res.string.finder_options),
            onClick = onOptions,
        )
    }
}
