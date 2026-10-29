package com.artkachenko.livetextfinder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.MAX_LISTS
import com.artkachenko.livetextfinder.search.PersistedSearchState
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchSessionStore
import com.artkachenko.livetextfinder.search.validatePersistedSearchState
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** App-private versioned JSON storage using one process-owned I/O worker. */
private class AndroidSearchSessionStore(context: Context) : SearchSessionStore {
    private val atomicFile = AtomicFile(File(context.filesDir, "saved-searches.json"))
    private val executor = processIo
    private val main = mainHandler

    override fun load(callback: (Result<PersistedSearchState?>) -> Unit) {
        executor.execute {
            val result = runCatching {
                if (!atomicFile.baseFile.exists() && !backupFile().exists()) {
                    null
                } else {
                    decode(atomicFile.openRead().use(::readBounded))
                }
            }
            main.post { callback(result) }
        }
    }

    override fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val validationError = validatePersistedSearchState(value)
                require(validationError == null) {
                    validationError ?: "Saved search data is invalid."
                }
                val bytes = encode(value).toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_FILE_BYTES) {
                    "Saved search data exceeds the 256 KB limit."
                }
                val stream = atomicFile.startWrite()
                try {
                    stream.write(bytes)
                    atomicFile.finishWrite(stream)
                } catch (failure: Throwable) {
                    atomicFile.failWrite(stream)
                    throw failure
                }
            }
            main.post { callback(result) }
        }
    }

    override fun reset(callback: (Result<Unit>) -> Unit) {
        executor.execute {
            val result = runCatching { atomicFile.delete() }
            main.post { callback(result) }
        }
    }

    private fun backupFile(): File = File(atomicFile.baseFile.path + ".bak")

    private fun readBounded(stream: java.io.InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) {
                break
            }
            require(bytes.size() + count <= MAX_FILE_BYTES) {
                "Saved search file exceeds the 256 KB limit."
            }
            bytes.write(buffer, 0, count)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun encode(state: PersistedSearchState): String = JSONObject().apply {
        put("version", VERSION)
        put("highlightColor", state.highlightColor.storageKey)
        put("manualQuery", state.manualQuery)
        put("activeListId", state.activeListId ?: JSONObject.NULL)
        put("lists", JSONArray().apply {
            state.lists.forEach { list ->
                put(JSONObject().apply {
                    put("id", list.id)
                    put("name", list.name)
                    put("terms", JSONArray(list.terms))
                })
            }
        })
    }.toString()

    private fun decode(json: String): PersistedSearchState {
        val root = JSONObject(json)
        val version = root.get("version")
        require(version is Int && version in MIN_VERSION..VERSION) {
            "Unsupported saved search file version."
        }
        require(root.length() == if (version == 1) V1_ROOT_FIELD_COUNT else ROOT_FIELD_COUNT) {
            "Unsupported saved search file fields."
        }
        val manualQuery = root.get("manualQuery") as? String
            ?: error("Saved manual query has an invalid type.")
        val activeRaw = root.get("activeListId")
        val activeListId = if (activeRaw == JSONObject.NULL) {
            null
        } else {
            activeRaw as? String ?: error("Saved active list identity has an invalid type.")
        }
        val array = root.get("lists") as? JSONArray
            ?: error("Saved search lists have an invalid type.")
        require(array.length() <= MAX_LISTS) {
            "Saved search file contains too many lists."
        }
        val lists = buildList {
            for (index in 0 until array.length()) {
                val item = array.get(index) as? JSONObject
                    ?: error("Saved search list has an invalid type.")
                require(item.length() == LIST_FIELD_COUNT) {
                    "Saved search list has unsupported fields."
                }
                val id = item.get("id") as? String
                    ?: error("Saved search list ID has an invalid type.")
                val name = item.get("name") as? String
                    ?: error("Saved search list name has an invalid type.")
                val termsArray = item.get("terms") as? JSONArray
                    ?: error("Saved search terms have an invalid type.")
                val terms = buildList {
                    for (termIndex in 0 until termsArray.length()) {
                        val term = termsArray.get(termIndex) as? String
                            ?: error("Saved search term has an invalid type.")
                        add(term)
                    }
                }
                add(SavedSearchList(id, name, terms))
            }
        }
        val highlightColor = when (version) {
            1 -> HighlightColor.Yellow
            2 -> {
                val key = root.get("highlightColor") as? String
                    ?: error("Saved highlight colour has an invalid type.")
                HighlightColor.entries.firstOrNull { it.storageKey == key }
                    ?: error("Saved highlight colour is unknown.")
            }
            else -> {
                val key = root.get("highlightColor") as? String
                    ?: error("Saved highlight colour has an invalid type.")
                HighlightColor.fromStorageKey(key)
                    ?: error("Saved highlight colour is unknown.")
            }
        }
        val value = PersistedSearchState(manualQuery, lists, activeListId, highlightColor)
        val validationError = validatePersistedSearchState(value)
        require(validationError == null) {
            validationError ?: "Saved search data is invalid."
        }
        return value
    }

    private companion object {
        const val VERSION = 3
        const val MIN_VERSION = 1
        const val V1_ROOT_FIELD_COUNT = 4
        const val ROOT_FIELD_COUNT = 5
        const val LIST_FIELD_COUNT = 3
        const val MAX_FILE_BYTES = 256 * 1024
        const val BUFFER_SIZE = 8 * 1024

        val processIo = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "saved-search-storage").apply { isDaemon = true }
        }

        val mainHandler = Handler(Looper.getMainLooper())
    }
}

/** Creates the Android app-private saved-search store. */
fun androidSearchSessionStore(context: Context): SearchSessionStore =
    AndroidSearchSessionStore(context.applicationContext)
