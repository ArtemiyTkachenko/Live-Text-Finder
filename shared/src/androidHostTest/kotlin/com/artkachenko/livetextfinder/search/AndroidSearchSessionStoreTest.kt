package com.artkachenko.livetextfinder.search

import android.os.Looper
import android.util.AtomicFile
import com.artkachenko.livetextfinder.androidSearchSessionStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        HighlightColor.entries.forEach { color ->
            val colored = initial.copy(highlightColor = color)
            assertTrue(await { first.save(colored, it) }.isSuccess)
            assertEquals(
                colored,
                await { androidSearchSessionStore(context).load(it) }.getOrThrow(),
            )
        }
        val custom = initial.copy(
            highlightColor = HighlightColor.fromRgb(18, 171, 239),
            matchMode = SearchMatchMode.WholeWord,
        )
        assertTrue(await { first.save(custom, it) }.isSuccess)
        val customJson = JSONObject(File(context.filesDir, "saved-searches.json").readText())
        assertEquals(5, customJson.getInt("version"))
        assertEquals("custom:#12ABEF", customJson.getString("highlightColor"))
        assertEquals("whole_word", customJson.getString("matchMode"))
        assertEquals(initial.lists.single().paletteSlots, customJson.getJSONArray("lists")
            .getJSONObject(0).getJSONArray("paletteSlots").let { slots ->
                List(slots.length()) { index -> slots.getInt(index) }
            })
        assertEquals(custom, await { androidSearchSessionStore(context).load(it) }.getOrThrow())

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
    fun versionOneFileLoadsYellowPreservesSearchAndNextEditWritesVersionFour() {
        val file = File(context.filesDir, "saved-searches.json")
        val versionOne = JSONObject()
            .put("version", 1)
            .put("manualQuery", "manual")
            .put("activeListId", "stable")
            .put(
                "lists",
                JSONArray().put(
                    JSONObject()
                        .put("id", "stable")
                        .put("name", "Parts")
                        .put("terms", JSONArray().put("foo").put("new york")),
                ),
            )
            .toString()
        file.writeText(versionOne)

        val store = androidSearchSessionStore(context)
        val loaded = await { store.load(it) }.getOrThrow()
        assertEquals(
            PersistedSearchState(
                "manual",
                listOf(SavedSearchList("stable", "Parts", listOf("foo", "new york"))),
                "stable",
                HighlightColor.Yellow,
            ),
            loaded,
        )
        assertEquals(versionOne, file.readText())

        val updated = checkNotNull(loaded).copy(highlightColor = HighlightColor.Cyan)
        assertTrue(await { store.save(updated, it) }.isSuccess)
        val written = JSONObject(file.readText())
        assertEquals(5, written.getInt("version"))
        assertEquals("cyan", written.getString("highlightColor"))
        assertEquals(updated, await { androidSearchSessionStore(context).load(it) }.getOrThrow())
    }

    @Test
    fun versionTwoKeepsPresetOnlyDecodingAndDoesNotRewriteDuringLoad() {
        val file = File(context.filesDir, "saved-searches.json")
        val versionTwo = JSONObject()
            .put("version", 2)
            .put("manualQuery", "manual")
            .put("activeListId", JSONObject.NULL)
            .put("lists", JSONArray())
            .put("highlightColor", "cyan")
            .toString()
        file.writeText(versionTwo)

        val store = androidSearchSessionStore(context)
        val loaded = await { store.load(it) }.getOrThrow()
        assertEquals(HighlightColor.Cyan, loaded?.highlightColor)
        assertEquals(versionTwo, file.readText())

        assertTrue(
            await { store.save(checkNotNull(loaded).copy(manualQuery = "updated"), it) }.isSuccess,
        )
        assertEquals(5, JSONObject(file.readText()).getInt("version"))

        val customVersionTwo = versionTwo.replace("\"cyan\"", "\"custom:#12ABEF\"")
        file.writeText(customVersionTwo)
        assertTrue(await { androidSearchSessionStore(context).load(it) }.isFailure)
        assertEquals(customVersionTwo, file.readText())
    }

    @Test
    fun legacyVersionsKeepTheirOldTermColorsAndRewriteOnlyOnOrdinarySave() {
        val file = File(context.filesDir, "saved-searches.json")
        val terms = listOf("zulu", "apple", "middle")
        val expectedSlots = listOf(2, 0, 1)
        val versions = listOf(
            1 to HighlightColor.Yellow,
            2 to HighlightColor.Cyan,
            3 to HighlightColor.fromRgb(18, 171, 239),
        )
        versions.forEach { (version, color) ->
            val root = JSONObject()
                .put("version", version)
                .put("manualQuery", "manual")
                .put("activeListId", "stable")
                .put(
                    "lists",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "stable")
                            .put("name", "Parts")
                            .put("terms", JSONArray(terms)),
                    ),
                )
            if (version >= 2) {
                root.put("highlightColor", color.storageKey)
            }
            val originalBytes = root.toString()
            file.writeText(originalBytes)

            val store = androidSearchSessionStore(context)
            val loaded = checkNotNull(await { store.load(it) }.getOrThrow())
            assertEquals(originalBytes, file.readText())
            assertEquals(expectedSlots, loaded.lists.single().paletteSlots)
            assertEquals(color, loaded.highlightColor)
            assertEquals(SearchMatchMode.Substring, loaded.matchMode)
            val loadedSpec = SearchSessionState(persisted = loaded, loaded = true).activeSearch
            val loadedColors = SearchHighlightPalette.colorsByTerm(
                loadedSpec,
                loaded.highlightColor,
            )
            assertEquals(
                expectedSlots.indices.sortedBy { index -> expectedSlots[index] }
                    .map { index -> SearchHighlightPalette.colors[expectedSlots[index]] },
                loadedColors.values.toList(),
            )

            val editedList = SavedSearchList(
                "stable",
                "Parts",
                listOf("aardvark", "middle", "ZULU", "apple"),
                listOf(3, 1, 2, 0),
            )
            val edited = loaded.copy(manualQuery = "edited", lists = listOf(editedList))
            val rewrittenSlots = listOf(3, 1, 2, 0)
            assertTrue(await { store.save(edited, it) }.isSuccess)
            assertEquals(5, JSONObject(file.readText()).getInt("version"))
            val reopened = checkNotNull(
                await { androidSearchSessionStore(context).load(it) }.getOrThrow(),
            )
            assertEquals(edited, reopened)
            assertEquals(rewrittenSlots, reopened.lists.single().paletteSlots)
            val reopenedSpec = SearchSessionState(persisted = reopened, loaded = true).activeSearch
            val reopenedColors = SearchHighlightPalette.colorsByTerm(
                reopenedSpec,
                reopened.highlightColor,
            )
            assertEquals(
                rewrittenSlots.indices.sortedBy { index -> rewrittenSlots[index] }
                    .map { index -> SearchHighlightPalette.colors[rewrittenSlots[index]] },
                reopenedColors.values.toList(),
            )
        }
    }

    @Test
    fun oversizedLegacyListsPreserveBytesAndRequireExplicitRecoveryBeforeWrites() {
        val file = File(context.filesDir, "saved-searches.json")
        val terms = JSONArray().apply {
            repeat(MAX_TERMS_PER_LIST + 1) { index -> put("term-$index") }
        }
        (1..3).forEach { version ->
            val root = JSONObject()
                .put("version", version)
                .put("manualQuery", "")
                .put("activeListId", "stable")
                .put(
                    "lists",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "stable")
                            .put("name", "Parts")
                            .put("terms", terms),
                    ),
                )
            if (version >= 2) {
                root.put("highlightColor", "cyan")
            }
            val originalBytes = root.toString()
            file.writeText(originalBytes)

            val store = androidSearchSessionStore(context)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(originalBytes, file.readText())

            val controller = SearchSessionController(store)
            assertTrue(await { store.load(it) }.isFailure)
            assertTrue(controller.state.value.loaded)
            assertTrue(controller.state.value.recoveryRequired)
            assertTrue(controller.saveList(null, "New list", "new term")!!.contains("unavailable"))
            controller.setManualQuery("ignored until reset")
            controller.setHighlightColor(HighlightColor.Magenta)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(originalBytes, file.readText())

            controller.retry()
            controller.setManualQuery("recovered query")
            assertTrue(await { store.load(it) }.getOrThrow() == null)
            assertFalse(controller.state.value.recoveryRequired)
            val recovered = await { store.load(it) }.getOrThrow()
            assertEquals("recovered query", recovered?.manualQuery)
            assertEquals(HighlightColor.Yellow, recovered?.highlightColor)
        }
    }

    @Test
    fun versionFourRejectsMalformedSlotsAndInvalidSavePreservesExistingBytes() {
        val file = File(context.filesDir, "saved-searches.json")
        val prefix =
            """{"version":4,"manualQuery":"kept","activeListId":null,"highlightColor":"yellow","""
        val listPrefix =
            """"lists":[{"id":"stable","name":"Parts","terms":["zulu","apple"],"paletteSlots": """
        val suffix = """}]}"""
        val invalidSlots = listOf(
            "null",
            "[0]",
            "[0,1,2]",
            "[0,null]",
            "[0,\"1\"]",
            "[0,1.5]",
            "[0,true]",
            "[0,-1]",
            "[0,32]",
            "[0,0]",
        )
        val store = androidSearchSessionStore(context)
        val validBytes = prefix + listPrefix + "[0,1]" + suffix
        file.writeText(validBytes)
        val versionFour = checkNotNull(await { store.load(it) }.getOrThrow())
        assertEquals(SearchMatchMode.Substring, versionFour.matchMode)
        assertEquals(validBytes, file.readText())
        invalidSlots.forEach { slots ->
            val bytes = prefix + listPrefix + slots + suffix
            file.writeText(bytes)
            assertTrue(await { store.load(it) }.isFailure, slots)
            assertEquals(bytes, file.readText())
        }
        val missingSlots =
            """{"version":4,"highlightColor":"yellow","manualQuery":"kept","activeListId":null,""" +
                """"lists":[{"id":"stable","name":"Parts","terms":["zulu","apple"]}]}"""
        val extraListField = validBytes.replace(
            "\"paletteSlots\": [0,1]",
            "\"paletteSlots\": [0,1],\"extra\":true",
        )
        listOf(missingSlots, extraListField).forEach { bytes ->
            file.writeText(bytes)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(bytes, file.readText())
        }

        val stable = PersistedSearchState(
            "kept",
            listOf(SavedSearchList("stable", "Parts", listOf("zulu", "apple"))),
            null,
        )
        assertTrue(await { store.save(stable, it) }.isSuccess)
        val storedBytes = file.readText()
        val invalidSave = stable.copy(
            lists = listOf(
                SavedSearchList("stable", "Parts", listOf("zulu", "apple"), listOf(0, 0)),
            ),
        )
        assertTrue(await { store.save(invalidSave, it) }.isFailure)
        assertEquals(storedBytes, file.readText())
    }

    @Test
    fun versionFiveRejectsUnknownMissingNullAndExtraMatchModeFieldsWithoutChangingBytes() {
        val file = File(context.filesDir, "saved-searches.json")
        val valid = JSONObject()
            .put("version", 5)
            .put("manualQuery", "kept")
            .put("activeListId", JSONObject.NULL)
            .put("lists", JSONArray())
            .put("highlightColor", "yellow")
            .put("matchMode", "whole_word")
            .toString()
        val invalid = listOf(
            valid.replace("whole_word", "unknown"),
            valid.replace(",\"matchMode\":\"whole_word\"", ""),
            valid.replace("\"whole_word\"", "null"),
            valid.replace("\"whole_word\"", "3"),
            valid.replace("\"whole_word\"", "\"substring\"").replace(
                "\"matchMode\":\"substring\"",
                "\"matchMode\":\"substring\",\"extra\":true",
            ),
        )
        val store = androidSearchSessionStore(context)
        invalid.forEach { bytes ->
            file.writeText(bytes)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(bytes, file.readText())
        }
        file.writeText(valid)
        assertEquals(
            SearchMatchMode.WholeWord,
            await { store.load(it) }.getOrThrow()?.matchMode,
        )
    }

    @Test
    fun versionThreeRejectsNoncanonicalCustomKeysWithoutOverwritingSourceBytes() {
        val file = File(context.filesDir, "saved-searches.json")
        val valid = JSONObject()
            .put("version", 3)
            .put("manualQuery", "kept")
            .put("activeListId", JSONObject.NULL)
            .put("lists", JSONArray())
            .put("highlightColor", "custom:#12ABEF")
            .toString()
        val invalidValues = listOf(
            valid.replace("custom:#12ABEF", "custom:#12abef"),
            valid.replace("custom:#12ABEF", "custom:#12ABEF00"),
            valid.replace("custom:#12ABEF", "custom:#12ABEG"),
            valid.replace("custom:#12ABEF", "custom:#12ABEF "),
            valid.replace("\"custom:#12ABEF\"", "17"),
            valid.replace("\"custom:#12ABEF\"", "null"),
        )
        invalidValues.forEach { bytes ->
            file.writeText(bytes)
            assertTrue(await { androidSearchSessionStore(context).load(it) }.isFailure)
            assertEquals(bytes, file.readText())
        }
    }

    @Test
    fun unknownVersionTwoColorAndSchemaAreRejectedWithoutOverwritingSourceBytes() {
        val file = File(context.filesDir, "saved-searches.json")
        val valid = JSONObject()
            .put("version", 2)
            .put("manualQuery", "kept")
            .put("activeListId", JSONObject.NULL)
            .put("lists", JSONArray())
            .put("highlightColor", "ultraviolet")
            .toString()
        val store = androidSearchSessionStore(context)

        val invalidColors = listOf(
            valid,
            valid.replace("\"ultraviolet\"", "17"),
            valid.replace("\"ultraviolet\"", "true"),
            valid.replace("\"ultraviolet\"", "null"),
            valid.replace("\"highlightColor\":\"ultraviolet\"", "\"extra\":true"),
        )
        invalidColors.forEach { bytes ->
            file.writeText(bytes)
            assertTrue(await { store.load(it) }.isFailure)
            assertEquals(bytes, file.readText())
        }
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
