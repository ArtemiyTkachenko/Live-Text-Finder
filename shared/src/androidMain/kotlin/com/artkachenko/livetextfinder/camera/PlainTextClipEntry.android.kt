package com.artkachenko.livetextfinder.camera

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("Matched text", text))
