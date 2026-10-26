package com.artkachenko.livetextfinder.search

import android.os.Looper
import android.util.AtomicFile
import com.artkachenko.livetextfinder.androidSearchSessionStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidSearchSessionStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun separateStoreInstancesRoundTripUpdateAndClearPersistedState() {
        val first = androidSearchSessionStore(context)
        val initial = PersistedSearchState(
            "manual",
            listOf(SavedSearchList("stable", "Parts", listOf("foo", "new york"))),
            "stable",
        )
        assertTrue(await { first.save(initial, it) }.isSuccess)
        assertEquals(initial, await { androidSearchSessionStore(context).load(it) }.getOrThrow())

        val updated = initial.copy(manualQuery = "", lists = emptyList(), activeListId = null)
        assertTrue(await { androidSearchSessionStore(context).save(updated, it) }.isSuccess)
        assertEquals(updated, await { androidSearchSessionStore(context).load(it) }.getOrThrow())
    }

    @Test
    fun malformedOrUnsupportedFilesFailWithoutBeingSilentlyReplaced() {
        val file = File(context.filesDir, "saved-searches.json")
        file.writeText("{\"version\":99}")
        val store = androidSearchSessionStore(context)
        assertIs<IllegalArgumentException>(await { store.load(it) }.exceptionOrNull())
        assertEquals("{\"version\":99}", file.readText())
    }

    @Test
    fun decoderRejectsCoercedTypesNoncanonicalNamesAndExcessTermCharacters() {
        val file = File(context.filesDir, "saved-searches.json")
        val excessiveTerms = JSONArray().apply {
            ('a'..'z').forEach { character -> put(character.toString().repeat(120)) }
        }
        val excessiveTermsFile = JSONObject()
            .put("version", 1)
            .put("manualQuery", "")
            .put("activeListId", JSONObject.NULL)
            .put(
                "lists",
                JSONArray().put(
                    JSONObject()
                        .put("id", "id")
                        .put("name", "Parts")
                        .put("terms", excessiveTerms),
                ),
            )
            .toString()
        val untrimmedNameFile = JSONObject()
            .put("version", 1)
            .put("manualQuery", "")
            .put("activeListId", JSONObject.NULL)
            .put(
                "lists",
                JSONArray().put(
                    JSONObject()
                        .put("id", "id")
                        .put("name", " Parts ")
                        .put("terms", JSONArray().put("foo")),
                ),
            )
            .toString()
        val invalidFiles = listOf(
            """{"version":1.9,"manualQuery":17,"activeListId":null,"lists":[]}""",
            """{"version":1,"manualQuery":17,"activeListId":null,"lists":[]}""",
            untrimmedNameFile,
            excessiveTermsFile,
        )
        val store = androidSearchSessionStore(context)

        invalidFiles.forEach { contents ->
            file.writeText(contents)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(contents, file.readText())
        }
    }

    @Test
    fun exactManualQueryLimitRoundTripsAndLongerQueryCannotOverwriteIt() {
        val file = File(context.filesDir, "saved-searches.json")
        val store = androidSearchSessionStore(context)
        val boundary = PersistedSearchState("x".repeat(10_000), emptyList(), null)
        assertTrue(await { store.save(boundary, it) }.isSuccess)
        assertEquals(boundary, await { store.load(it) }.getOrThrow())

        val tooLong = boundary.copy(manualQuery = boundary.manualQuery + "x")
        assertTrue(await { store.save(tooLong, it) }.isFailure)
        assertEquals(boundary, await { store.load(it) }.getOrThrow())
        assertTrue(file.readText().contains("x".repeat(100)))
    }

    @Test
    fun atomicFileRecoversPreviousCompleteValueAfterInterruptedWrite() {
        val atomic = AtomicFile(File(context.filesDir, "saved-searches.json"))
        val stable = PersistedSearchState("survives", emptyList(), null)
        assertTrue(await { androidSearchSessionStore(context).save(stable, it) }.isSuccess)
        val partial = atomic.startWrite()
        partial.write("{partial".toByteArray())
        // Simulate process death before finishWrite/failWrite. AtomicFile retains its backup.
        val recovered = await { androidSearchSessionStore(context).load(it) }.getOrThrow()
        assertEquals(stable, recovered)
    }

    private fun <T> await(start: ((Result<T>) -> Unit) -> Unit): Result<T> {
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        start {
            result = it
            latch.countDown()
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (latch.count > 0 && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(2)
        }
        assertTrue(latch.await(0, TimeUnit.MILLISECONDS), "Timed out waiting for storage callback")
        return checkNotNull(result)
    }
}
