package com.artkachenko.livetextfinder

import android.content.Context
import com.artkachenko.livetextfinder.androidSearchSessionStore
import com.artkachenko.livetextfinder.search.SearchSessionController

/** Keeps the search controller with the application across Activity and screen recreation. */
internal object AndroidSearchSession {
    @Volatile
    private var controller: SearchSessionController? = null

    fun get(context: Context): SearchSessionController {
        return controller ?: synchronized(this) {
            controller ?: SearchSessionController(
                androidSearchSessionStore(context.applicationContext),
            ).also { controller = it }
        }
    }
}
