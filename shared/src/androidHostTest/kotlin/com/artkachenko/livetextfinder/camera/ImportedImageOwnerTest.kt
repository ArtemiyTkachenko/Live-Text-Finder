package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ImportedImageOwnerTest {
    @Test
    fun stoppedPickerResultWaitsForForegroundThenAdoptsAndRetainsImportedContent() {
        val owner = ImportedImageOwner()
        val entered = CountDownLatch(1)
        val output = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.RED) }
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val processor = ImportedImageProcessor { _, _, sourceIdentity, _ ->
            entered.countDown()
            ProcessedImportedImage(
                output,
                ImportedTextSnapshot("found text", emptyList(), 8, 6, sourceIdentity),
            )
        }
        owner.setProcessorForTest(resolver, processor)
        val ticket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(ticket))
        owner.stop()
        owner.acceptPickerResult(ticket, Uri.parse("content://provider/photo"))
        assertEquals(ImportedImagePhase.Picking, owner.state.value.phase)
        assertNull(owner.state.value.snapshot)

        assertFalse(entered.await(100, TimeUnit.MILLISECONDS))
        owner.start()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        awaitMainState(owner, ImportedImagePhase.Imported)
        val imported = owner.state.value
        assertEquals("found text", imported.snapshot?.text)
        assertSame(output, owner.currentBitmap())
        owner.dispose()
        assertFalse(output.isRecycled)
        output.recycle()
    }

    @Test
    fun cancelKeepsOutstandingLauncherSlotUntilItsOldCallbackDrains() {
        val owner = ImportedImageOwner()
        val launched = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(launched))
        owner.cancel()

        assertEquals(null, owner.beginPicker())
        assertFalse(owner.pickerWillLaunch(launched))
        owner.acceptPickerResult(launched, Uri.parse("content://provider/stale"))

        val next = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(next))
        owner.acceptPickerResult(next, null)
        owner.dispose()
    }

    @Test
    fun normalBackgroundInvalidatesInFlightResultAndProcessingFailureReleasesGate() {
        val owner = ImportedImageOwner()
        owner.start()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var output: Bitmap
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val blockedProcessor = ImportedImageProcessor { _, _, sourceIdentity, _ ->
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            output = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
            ProcessedImportedImage(
                output,
                ImportedTextSnapshot("late", emptyList(), 4, 3, sourceIdentity),
            )
        }
        owner.setProcessorForTest(resolver, blockedProcessor)
        val ticket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(ticket))
        owner.acceptPickerResult(ticket, Uri.parse("content://provider/slow"))
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        owner.stop()
        assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
        release.countDown()
        awaitWorkGate(owner)
        assertTrue(output.isRecycled)

        val failingOwner = ImportedImageOwner()
        failingOwner.start()
        failingOwner.setProcessorForTest(resolver, ImportedImageProcessor { _, _, _, _ ->
            throw IllegalArgumentException("fixture failure")
        })
        val failedTicket = checkNotNull(failingOwner.beginPicker())
        assertTrue(failingOwner.pickerWillLaunch(failedTicket))
        failingOwner.acceptPickerResult(failedTicket, Uri.parse("content://provider/bad"))
        awaitMainState(failingOwner, ImportedImagePhase.Error)

        val replacement = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
        failingOwner.setProcessorForTest(resolver, ImportedImageProcessor {
                _, _, sourceIdentity, _ ->
            ProcessedImportedImage(
                replacement,
                ImportedTextSnapshot("ok", emptyList(), 4, 3, sourceIdentity),
            )
        })
        val successTicket = checkNotNull(failingOwner.beginPicker())
        assertTrue(failingOwner.pickerWillLaunch(successTicket))
        failingOwner.acceptPickerResult(successTicket, Uri.parse("content://provider/good"))
        awaitMainState(failingOwner, ImportedImagePhase.Imported)
        failingOwner.dispose()
        replacement.recycle()
        owner.dispose()
    }

    @Test
    fun pickerTicketSurvivesExternalStopAndRejectsStaleDuplicateCallbacks() {
        val owner = ImportedImageOwner()
        owner.start()
        val firstTicket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(firstTicket))

        owner.stop()
        assertEquals(ImportedImagePhase.Picking, owner.state.value.phase)
        assertEquals(firstTicket, owner.state.value.requestId)
        assertEquals(null, owner.state.value.snapshot)

        owner.acceptPickerResult(firstTicket, null)
        assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
        val secondTicket = checkNotNull(owner.beginPicker())
        assertNotEquals(firstTicket, secondTicket)
        assertTrue(owner.pickerWillLaunch(secondTicket))

        owner.acceptPickerResult(firstTicket, Uri.parse("content://provider/old"))
        assertEquals(ImportedImagePhase.Picking, owner.state.value.phase)
        assertEquals(secondTicket, owner.state.value.requestId)

        owner.acceptPickerResult(secondTicket, null)
        owner.acceptPickerResult(secondTicket, Uri.parse("content://provider/duplicate"))
        assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
        assertFalse(owner.pickerWillLaunch(secondTicket))
        owner.dispose()
    }

    @Test
    fun recreationDisposalInvalidatesPendingPickerTicket() {
        val owner = ImportedImageOwner()
        val ticket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(ticket))

        owner.dispose()

        assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
        assertEquals(null, owner.state.value.requestId)
        assertFalse(owner.pickerWillLaunch(ticket))
    }

    private fun awaitMainState(owner: ImportedImageOwner, phase: ImportedImagePhase) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (owner.state.value.phase == phase && !owner.isImportWorkInFlightForTest()) return
            Thread.sleep(5)
        }
        assertEquals(phase, owner.state.value.phase)
    }

    private fun awaitWorkGate(owner: ImportedImageOwner) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!owner.isImportWorkInFlightForTest()) return
            Thread.sleep(5)
        }
        assertFalse(owner.isImportWorkInFlightForTest())
    }
}
