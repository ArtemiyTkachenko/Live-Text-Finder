package com.artkachenko.livetextfinder

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.screen.FinderScreen
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme

/** Main live text search screen. */
@Composable
fun App(session: SearchSessionController = remember { inMemorySearchSessionController() }) {
    LiveTextFinderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            FinderScreen(session)
        }
    }
}
