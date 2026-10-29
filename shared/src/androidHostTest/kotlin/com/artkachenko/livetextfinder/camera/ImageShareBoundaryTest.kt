package com.artkachenko.livetextfinder.camera

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.SearchTerm
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.manualSearchSpec
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/** Covers production rendering, temporary publication and provider boundaries on host Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageShareBoundaryTest {
    @Before
    fun resetRealProviderPathCacheForCurrentTestContext() {
        val context = RuntimeEnvironment.getApplication()
        val provider = context.packageManager.getProviderInfo(
            android.content.ComponentName(context, SharedImageProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        SharedImageProvider().attachInfo(context, provider)
    }

    @Test
    fun everyPaletteColorSurvivesProductionRendererPngAndProviderDecode() {
        val colors = HighlightColor.entries + HighlightColor.fromRgb(18, 171, 239)
        colors.forEach { highlightColor ->
            val source = asymmetricBitmap(40, 24)
            val originalSourcePixels = IntArray(source.width * source.height)
            source.getPixels(
                originalSourcePixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height,
            )
            val snapshot = frozenSnapshot(40, 24, 0)
            val frozenSearch = searchRecognizedText(
                manualSearchSpec("target"),
                OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
            )
            val (importedFixtureBitmap, importedSnapshot, _) = importedFixture()
            importedFixtureBitmap.recycle()
            val importedSearch = searchRecognizedContent(
                manualSearchSpec("target"),
                importedSnapshot,
            )
            val renderedBitmaps = listOf(
                AndroidImageShareRenderer.renderFrozen(
                    source,
                    0,
                    projectFrozenHighlights(frozenSearch, 40f, 24f).outlines,
                    highlightColor,
                ),
                AndroidImageShareRenderer.renderImported(
                    source,
                    projectImportedHighlights(importedSearch, importedSnapshot, 40f, 24f)
                        .outlines,
                    highlightColor,
                ),
            )
            try {
                renderedBitmaps.forEach { rendered ->
                    val output = ShareCache.reserve(RuntimeEnvironment.getApplication().cacheDir)
                    try {
                        output.encode(rendered)
                        val file = output.publish()
                        val uri = FileProvider.getUriForFile(
                            RuntimeEnvironment.getApplication(),
                            "${RuntimeEnvironment.getApplication().packageName}.shared-images",
                            file,
                        )
                        val decoded = RuntimeEnvironment.getApplication().contentResolver
                            .openInputStream(uri)!!
                            .use { stream -> BitmapFactory.decodeStream(stream) }
                        assertEquals(highlightColor.argb.toInt(), decoded.getPixel(9, 6))
                        assertEquals(Color.BLACK, decoded.getPixel(5, 10))
                        assertEquals(Color.RED, decoded.getPixel(1, 1))
                        val pixelsAfterPipeline = IntArray(source.width * source.height)
                        source.getPixels(
                            pixelsAfterPipeline,
                            0,
                            source.width,
                            0,
                            0,
                            source.width,
                            source.height,
                        )
                        assertContentEquals(originalSourcePixels, pixelsAfterPipeline)
                        assertFalse(source.isRecycled)
                        decoded.recycle()
                    } finally {
                        output.delete()
                    }
                }
            } finally {
                renderedBitmaps.forEach(Bitmap::recycle)
                source.recycle()
            }
        }
    }

    @Test
    fun capturedShareColorRendersOldColorButLiveCallbackSuppressesStaleChooser() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, _) = importedFixture()
        val imageOwner = adoptedOwner(source, snapshot)
        val session = inMemorySearchSessionController()
        session.setManualQuery("target")
        val search = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        val sourceIdentity = importedIdentity(snapshot)
        val shareOwner = startedShareOwner(context)
        val heldWorker = AtomicReference<Runnable?>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val encodedColor = AtomicInteger()
        val launches = AtomicInteger()
        val customColor = HighlightColor.fromRgb(18, 171, 239)
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity, HighlightColor.Yellow)
        shareOwner.dispatchWorkerForTest = { worker -> heldWorker.set(worker) }
        shareOwner.beforeHandoffForTest = {
            val published = checkNotNull(BitmapFactory.decodeFile(shareFiles(context.cacheDir)
                .single().path))
            encodedColor.set(published.getPixel(9, 6))
            published.recycle()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.shareImported(
            imageOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
            currentHighlightColor = { session.state.value.persisted.highlightColor },
        )
        val worker = checkNotNull(heldWorker.get())

        try {
            ShareWorkGate.executor.execute(worker)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(HighlightColor.Yellow.argb.toInt(), encodedColor.get())
            session.setHighlightColor(customColor)
        } finally {
            release.countDown()
            waitForGate()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        shareOwner.dispose()
        imageOwner.dispose()
        source.recycle()
    }

    @Test
    fun colorChangeBeforeHeldProductionWorkerKeepsCapturedPngColorAndSuppressesChooser() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, _) = importedFixture()
        val imageOwner = adoptedOwner(source, snapshot)
        val session = inMemorySearchSessionController()
        session.setManualQuery("target")
        val search = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        val sourceIdentity = importedIdentity(snapshot)
        val shareOwner = startedShareOwner(context)
        val heldWorker = AtomicReference<Runnable?>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val encodedColor = AtomicInteger()
        val launches = AtomicInteger()
        val customColor = HighlightColor.fromRgb(18, 171, 239)
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity, HighlightColor.Yellow)
        shareOwner.dispatchWorkerForTest = { worker -> heldWorker.set(worker) }
        shareOwner.beforeHandoffForTest = {
            val published = checkNotNull(BitmapFactory.decodeFile(shareFiles(context.cacheDir)
                .single().path))
            encodedColor.set(published.getPixel(9, 6))
            published.recycle()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.shareImported(
            imageOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
            currentHighlightColor = { session.state.value.persisted.highlightColor },
        )
        val worker = checkNotNull(heldWorker.get())
        session.setHighlightColor(customColor)

        try {
            ShareWorkGate.executor.execute(worker)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(HighlightColor.Yellow.argb.toInt(), encodedColor.get())
        } finally {
            release.countDown()
            waitForGate()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        shareOwner.dispose()
        imageOwner.dispose()
        source.recycle()
    }

    @Test
    fun frozenColorUpdateCancelsPendingShareAndRetryPublishesNewColor() {
        val context = RuntimeEnvironment.getApplication()
        val source = asymmetricBitmap(40, 24)
        val snapshot = frozenSnapshot(40, 24, 0)
        val identity = FrameIdentity(4, 2)
        val frozenOwner = createFrozenOwner(source, snapshot, identity)
        val session = inMemorySearchSessionController()
        session.setManualQuery("target")
        val search = searchRecognizedText(
            session.state.value.activeSearch,
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        val sourceIdentity = "frozen:$identity"
        val shareOwner = startedShareOwner(context)
        val heldWorker = AtomicReference<Runnable?>()
        var launchCount = 0
        val customColor = HighlightColor.fromRgb(18, 171, 239)
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity, HighlightColor.Yellow)
        shareOwner.dispatchWorkerForTest = { worker -> heldWorker.set(worker) }
        shareOwner.shareLauncherForTest = { launchCount++ }
        shareOwner.shareFrozen(
            frozenOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
            currentHighlightColor = { session.state.value.persisted.highlightColor },
        )

        session.setHighlightColor(customColor)
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity, customColor)
        ShareWorkGate.executor.execute(checkNotNull(heldWorker.get()))
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, launchCount)
        assertTrue(shareFiles(context.cacheDir).isEmpty())

        shareOwner.dispatchWorkerForTest = null
        shareOwner.shareFrozen(
            frozenOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
            currentHighlightColor = { session.state.value.persisted.highlightColor },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, launchCount)
        val file = shareFiles(context.cacheDir).single()
        val decoded = context.contentResolver.openInputStream(
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.shared-images",
                file,
            ),
        )!!.use { stream -> BitmapFactory.decodeStream(stream) }
        assertEquals(customColor.argb.toInt(), decoded.getPixel(9, 6))
        assertEquals(Color.BLACK, decoded.getPixel(5, 10))
        decoded.recycle()
        shareFiles(context.cacheDir).forEach(File::delete)
        shareOwner.dispose()
        source.recycle()
    }

    @Test
    fun frozenRotationsRenderProjectedOutlinesIntoDecodedPng() {
        val expectedRedPixels = listOf(1 to 1, 22 to 1, 38 to 22, 1 to 38)
        val rotations = listOf(0, 90, 180, 270)
        rotations.forEachIndexed { index, rotation ->
            val source = asymmetricBitmap(40, 24)
            val snapshot = frozenSnapshot(40, 24, rotation)
            val search = searchRecognizedText(
                manualSearchSpec("target"),
                OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
            )
            val width = snapshot.frame.uprightWidth
            val height = snapshot.frame.uprightHeight
            val outlines = projectFrozenHighlights(search, width.toFloat(), height.toFloat())
            assertTrue(outlines.ready)
            assertEquals(1, outlines.outlines.size)
            val rendered = AndroidImageShareRenderer.renderFrozen(
                source,
                rotation,
                outlines.outlines,
            )
            assertEquals(width, rendered.width)
            assertEquals(height, rendered.height)
            val output = ShareCache.reserve(RuntimeEnvironment.getApplication().cacheDir)
            try {
                output.encode(rendered)
                val published = output.publish()
                val decoded = RuntimeEnvironment.getApplication().contentResolver
                    .openInputStream(FileProvider.getUriForFile(
                        RuntimeEnvironment.getApplication(),
                        "${RuntimeEnvironment.getApplication().packageName}.shared-images",
                        published,
                    ))!!.use { stream -> BitmapFactory.decodeStream(stream) }
                val (redX, redY) = expectedRedPixels[index]
                assertEquals(Color.RED, decoded.getPixel(redX, redY))
                assertEquals(Color.YELLOW, decoded.getPixel(9, 6))
                assertEquals(Color.BLACK, decoded.getPixel(5, 10))
                assertFalse(source.isRecycled)
                decoded.recycle()
            } finally {
                output.delete()
                rendered.recycle()
                source.recycle()
            }
        }
    }

    @Test
    fun zeroMatchesRenderAndPublishTheImageWithoutOutlines() {
        val source = asymmetricBitmap(40, 24)
        val snapshot = frozenSnapshot(40, 24, 0)
        val search = searchRecognizedText(
            manualSearchSpec("missing"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        val projection = projectFrozenHighlights(search, 40f, 24f)
        assertTrue(projection.ready)
        assertTrue(projection.outlines.isEmpty())
        val rendered = AndroidImageShareRenderer.renderFrozen(source, 0, projection.outlines)
        val output = ShareCache.reserve(RuntimeEnvironment.getApplication().cacheDir)
        try {
            output.encode(rendered)
            val file = output.publish()
            val uri = FileProvider.getUriForFile(
                RuntimeEnvironment.getApplication(),
                "${RuntimeEnvironment.getApplication().packageName}.shared-images",
                file,
            )
            val decoded = RuntimeEnvironment.getApplication().contentResolver
                .openInputStream(uri)!!.use { stream -> BitmapFactory.decodeStream(stream) }
            assertEquals(Color.RED, decoded.getPixel(5, 5))
            assertEquals(Color.GREEN, decoded.getPixel(35, 5))
            decoded.recycle()
        } finally {
            output.delete()
            rendered.recycle()
            source.recycle()
        }
    }

    @Test
    fun multipleTermsDeduplicateFallbackGeometryAndClipAtOutputEdges() {
        val source = asymmetricBitmap(40, 24)
        val element = TextElement(
            text = "alpha beta",
            bounds = TextBounds(-3, 4, 12, 16),
            corners = listOf(TextPoint(2, 2), TextPoint(2, 2), TextPoint(2, 2)),
        )
        val snapshot = ImportedTextSnapshot(
            text = "alpha beta",
            lines = listOf(TextLine("alpha beta", null, emptyList(), listOf(element))),
            width = source.width,
            height = source.height,
            sourceIdentity = 74L,
        )
        val spec = ActiveSearchSpec(
            identity = "list\u0000alpha\u0000beta",
            displayName = "Two terms",
            terms = listOf(SearchTerm("one", "alpha"), SearchTerm("two", "beta")),
        )
        val search = searchRecognizedContent(spec, snapshot)
        assertEquals(2, search.occurrences.size)
        val projection = projectImportedHighlights(search, snapshot, 40f, 24f)
        assertEquals(1, projection.outlines.size)
        assertEquals(-3f, projection.outlines.single().points.first().x)

        val rendered = AndroidImageShareRenderer.renderImported(source, projection.outlines)
        val output = ShareCache.reserve(RuntimeEnvironment.getApplication().cacheDir)
        try {
            output.encode(rendered)
            val file = output.publish()
            val uri = FileProvider.getUriForFile(
                RuntimeEnvironment.getApplication(),
                "${RuntimeEnvironment.getApplication().packageName}.shared-images",
                file,
            )
            val decoded = RuntimeEnvironment.getApplication().contentResolver
                .openInputStream(uri)!!.use { stream -> BitmapFactory.decodeStream(stream) }
            assertEquals(source.getPixel(0, 10), decoded.getPixel(0, 10))
            assertFalse(decoded.getPixel(0, 4) == source.getPixel(0, 4))
            decoded.recycle()
        } finally {
            output.delete()
            rendered.recycle()
            source.recycle()
        }
    }

    @Test
    fun pendingSearchChangeSuppressesChooserAndCleansThePublishedFile() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", search.spec.identity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var currentSearchIdentity = search.spec.identity
        val launchCount = AtomicInteger()
        val handoffCount = AtomicInteger()
        shareOwner.shareLauncherForTest = { launchCount.incrementAndGet() }
        shareOwner.beforeHandoffForTest = {
            handoffCount.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { currentSearchIdentity },
        )
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { currentSearchIdentity },
        )
        val reachedHandoff = try {
            entered.await(5, TimeUnit.SECONDS)
        } finally {
            if (entered.count > 0L) {
                release.countDown()
            }
        }
        assertTrue(reachedHandoff)
        currentSearchIdentity = "changed-search"
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", currentSearchIdentity)
        release.countDown()
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, launchCount.get())
        assertEquals(1, handoffCount.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        importOwner.dispose()
        shareOwner.dispose()
        source.recycle()
    }

    @Test
    fun successfulChooserUsesReadOnlyPngUriAndKeepsFileAfterStopAndDispose() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", search.spec.identity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var chooser: Intent? = null
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareLauncherForTest = { intent -> chooser = intent }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { search.spec.identity },
        )
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        release.countDown()
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()

        val chooserIntent = assertNotNull(chooser)
        val sendIntent = assertNotNull(
            IntentCompat.getParcelableExtra(
                chooserIntent,
                Intent.EXTRA_INTENT,
                Intent::class.java,
            ),
        )
        assertEquals(Intent.ACTION_SEND, sendIntent.action)
        assertEquals("image/png", sendIntent.type)
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
            sendIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        assertEquals(0, sendIntent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(sendIntent.component)
        assertNull(sendIntent.`package`)
        assertEquals(setOf(Intent.EXTRA_STREAM), sendIntent.extras?.keySet())
        val uri = assertNotNull(
            IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java),
        )
        assertEquals(1, sendIntent.clipData?.itemCount)
        assertEquals(uri, sendIntent.clipData?.getItemAt(0)?.uri)
        val decoded = context.contentResolver.openInputStream(uri)!!.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        assertEquals(source.width, decoded.width)
        assertEquals(source.height, decoded.height)
        decoded.recycle()
        assertEquals("Share sheet opened.", shareOwner.state.value.message)

        shareOwner.stop()
        shareOwner.dispose()
        assertEquals(1, shareFiles(context.cacheDir).size)
        assertFalse(source.isRecycled)
        importOwner.dispose()
        source.recycle()
    }

    @Test
    fun ownerStopCancelsPendingPublicationWithoutRecyclingTheCapturedSource() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", search.spec.identity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launches = AtomicInteger()
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { search.spec.identity },
        )
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        shareOwner.stop()
        assertEquals(ImageShareStatus.Idle, shareOwner.state.value.status)
        assertTrue(ShareWorkGate.isBusy())
        release.countDown()
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        shareOwner.dispose()
        importOwner.dispose()
        source.recycle()
    }

    @Test
    fun disposedOwnerCannotLaunchAndFreshOwnerRetriesAfterGateRelease() {
        val context = RuntimeEnvironment.getApplication()
        val (oldSource, oldSnapshot, oldSearch) = importedFixture()
        val oldImageOwner = adoptedOwner(oldSource, oldSnapshot)
        val oldShareOwner = startedShareOwner(context)
        oldShareOwner.updateCurrent(importedIdentity(oldSnapshot), oldSearch.spec.identity)
        val oldLaunches = AtomicInteger()
        val newLaunches = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        oldShareOwner.shareLauncherForTest = { oldLaunches.incrementAndGet() }
        oldShareOwner.beforeHandoffForTest = {
            assertEquals(1, shareFiles(context.cacheDir).size)
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        oldShareOwner.shareImported(
            oldImageOwner,
            oldSearch,
            currentSourceIdentity = { importedIdentity(oldSnapshot) },
            currentSearchIdentity = { oldSearch.spec.identity },
        )
        val reachedHandoff = try {
            entered.await(5, TimeUnit.SECONDS)
        } finally {
            if (entered.count > 0L) {
                release.countDown()
            }
        }
        assertTrue(reachedHandoff)

        val (newSource, newSnapshot, newSearch) = importedFixture().let { fixture ->
            Triple(fixture.first, fixture.second.copy(sourceIdentity = 74L), fixture.third)
        }
        val newImageOwner = adoptedOwner(newSource, newSnapshot)
        val newShareOwner = ImageShareOwner().apply {
            attachContext(context)
            start()
        }
        try {
            oldShareOwner.dispose()
            newShareOwner.updateCurrent(importedIdentity(newSnapshot), newSearch.spec.identity)
            newShareOwner.shareLauncherForTest = { newLaunches.incrementAndGet() }
            newShareOwner.shareImported(
                newImageOwner,
                newSearch.copy(content = newSnapshot),
                currentSourceIdentity = { importedIdentity(newSnapshot) },
                currentSearchIdentity = { newSearch.spec.identity },
            )
            assertEquals(ImageShareStatus.Error, newShareOwner.state.value.status)
            assertTrue(ShareWorkGate.isBusy())
        } finally {
            release.countDown()
        }
        waitForGate()
        assertEquals(0, oldLaunches.get())
        assertEquals(0, newLaunches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(oldSource.isRecycled)
        assertFalse(newSource.isRecycled)

        var chooser: Intent? = null
        newShareOwner.shareLauncherForTest = { intent ->
            newLaunches.incrementAndGet()
            chooser = intent
        }
        newShareOwner.shareImported(
            newImageOwner,
            newSearch.copy(content = newSnapshot),
            currentSourceIdentity = { importedIdentity(newSnapshot) },
            currentSearchIdentity = { newSearch.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, newLaunches.get())
        assertEquals(ImageShareStatus.Opened, newShareOwner.state.value.status)
        val chooserIntent = assertNotNull(chooser)
        val sendIntent = assertNotNull(
            IntentCompat.getParcelableExtra(
                chooserIntent,
                Intent.EXTRA_INTENT,
                Intent::class.java,
            ),
        )
        val uri = assertNotNull(
            IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java),
        )
        val decoded = context.contentResolver.openInputStream(uri)!!.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        assertEquals(Color.RED, decoded.getPixel(1, 1))
        assertEquals(Color.BLACK, decoded.getPixel(5, 10))
        decoded.recycle()

        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        newShareOwner.dispose()
        oldImageOwner.dispose()
        newImageOwner.dispose()
        oldSource.recycle()
        newSource.recycle()
    }

    @Test
    fun sourceIdentityChangeBeforeChooserSuppressesPublication() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        var sourceIdentity = "import:${snapshot.sourceIdentity}"
        val currentSource = { sourceIdentity }
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launches = AtomicInteger()
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = currentSource,
            currentSearchIdentity = { search.spec.identity },
        )
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        sourceIdentity = "import:changed"
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity)
        release.countDown()
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        shareOwner.dispose()
        importOwner.dispose()
        source.recycle()
    }

    @Test
    fun frozenOwnerAdmissionCapturesGeometryAndRejectsChangedSearch() {
        val context = RuntimeEnvironment.getApplication()
        val source = asymmetricBitmap(40, 24)
        val snapshot = frozenSnapshot(40, 24, 0)
        val sourceOwner = createFrozenOwner(source, snapshot, FrameIdentity(3L, 1L))
        val frozen = checkNotNull(sourceOwner.state.value.frame)
        val sourceIdentity = "frozen:${frozen.identity}"
        val search = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        val shareOwner = startedShareOwner(context)
        var currentSearchIdentity = search.spec.identity
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val capturedBorder = AtomicInteger()
        val launches = AtomicInteger()
        shareOwner.updateCurrent(sourceIdentity, currentSearchIdentity)
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.beforeHandoffForTest = {
            val published = BitmapFactory.decodeFile(shareFiles(context.cacheDir).single().path)
            capturedBorder.set(published.getPixel(5, 10))
            published.recycle()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareFrozen(
            sourceOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { currentSearchIdentity },
        )
        assertEquals(FreezeMode.Frozen, sourceOwner.state.value.mode)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(Color.BLACK, capturedBorder.get())

        currentSearchIdentity = manualSearchSpec("changed").identity
        shareOwner.updateCurrent(sourceIdentity, currentSearchIdentity)
        assertEquals(ImageShareStatus.Idle, shareOwner.state.value.status)
        release.countDown()
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)

        val noMatch = searchRecognizedText(
            manualSearchSpec("missing"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        currentSearchIdentity = noMatch.spec.identity
        shareOwner.beforeHandoffForTest = null
        shareOwner.shareFrozen(
            sourceOwner,
            noMatch,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { currentSearchIdentity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, launches.get())
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)
        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        shareOwner.dispose()
        sourceOwner.resume()
        source.recycle()
    }

    @Test
    fun explicitCancelOwnerReplacementAndShareOwnerRecreationReleasePendingWork() {
        val context = RuntimeEnvironment.getApplication()
        val originalBitmap = asymmetricBitmap(40, 24)
        val originalSnapshot = frozenSnapshot(40, 24, 0)
        val originalOwner = createFrozenOwner(
            originalBitmap,
            originalSnapshot,
            FrameIdentity(4L, 1L),
        )
        val originalFrame = checkNotNull(originalOwner.state.value.frame)
        val originalIdentity = "frozen:${originalFrame.identity}"
        val originalSearch = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = originalSnapshot),
        )
        val shareOwner = startedShareOwner(context)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val launches = AtomicInteger()
        shareOwner.updateCurrent(originalIdentity, originalSearch.spec.identity)
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareFrozen(
            originalOwner,
            originalSearch,
            currentSourceIdentity = { originalIdentity },
            currentSearchIdentity = { originalSearch.spec.identity },
        )
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            shareOwner.cancel()
            assertEquals(ImageShareStatus.Idle, shareOwner.state.value.status)
            originalOwner.resume()
            assertEquals(FreezeMode.Live, originalOwner.state.value.mode)
            assertNull(originalOwner.currentBitmap())
            assertFalse(originalBitmap.isRecycled)
        } finally {
            release.countDown()
        }

        val replacementBitmap = asymmetricBitmap(40, 24)
        val replacementSnapshot = frozenSnapshot(40, 24, 0)
        val replacementOwner = createFrozenOwner(
            replacementBitmap,
            replacementSnapshot,
            FrameIdentity(5L, 1L),
        )
        val replacementFrame = checkNotNull(replacementOwner.state.value.frame)
        val replacementIdentity = "frozen:${replacementFrame.identity}"
        val replacementSearch = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = replacementSnapshot),
        )
        shareOwner.updateCurrent(replacementIdentity, replacementSearch.spec.identity)
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(originalBitmap.isRecycled)

        shareOwner.beforeHandoffForTest = null
        shareOwner.shareFrozen(
            replacementOwner,
            replacementSearch,
            currentSourceIdentity = { replacementIdentity },
            currentSearchIdentity = { replacementSearch.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, launches.get())
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)

        shareOwner.dispose()
        val recreatedShareOwner = startedShareOwner(context)
        recreatedShareOwner.updateCurrent(replacementIdentity, replacementSearch.spec.identity)
        recreatedShareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        recreatedShareOwner.shareFrozen(
            replacementOwner,
            replacementSearch,
            currentSourceIdentity = { replacementIdentity },
            currentSearchIdentity = { replacementSearch.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, launches.get())
        assertEquals(ImageShareStatus.Opened, recreatedShareOwner.state.value.status)
        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        recreatedShareOwner.dispose()
        replacementOwner.resume()
        originalBitmap.recycle()
        replacementBitmap.recycle()
    }

    @Test
    fun actualFrozenOwnerResumeAndFrameReplacementSuppressStaleHandoffs() {
        val context = RuntimeEnvironment.getApplication()
        val source = asymmetricBitmap(40, 24)
        val firstSnapshot = frozenSnapshot(40, 24, 0)
        val sourceOwner = createFrozenOwner(source, firstSnapshot, FrameIdentity(8L, 1L))
        val firstFrame = checkNotNull(sourceOwner.state.value.frame)
        val firstIdentity = "frozen:${firstFrame.identity}"
        val firstSearch = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = firstSnapshot),
        )
        val shareOwner = startedShareOwner(context)
        val launches = AtomicInteger()
        shareOwner.updateCurrent(firstIdentity, firstSearch.spec.identity)
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        var entered = CountDownLatch(1)
        var release = CountDownLatch(1)
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareFrozen(
            sourceOwner,
            firstSearch,
            currentSourceIdentity = { firstIdentity },
            currentSearchIdentity = { firstSearch.spec.identity },
        )
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            sourceOwner.resume()
            assertEquals(FreezeMode.Live, sourceOwner.state.value.mode)
        } finally {
            release.countDown()
        }
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)

        val secondSource = asymmetricBitmap(40, 24)
        val secondSnapshot = frozenSnapshot(40, 24, 0)
        captureFrozenFrame(sourceOwner, secondSource, secondSnapshot, FrameIdentity(8L, 2L))
        val secondFrame = checkNotNull(sourceOwner.state.value.frame)
        val secondIdentity = "frozen:${secondFrame.identity}"
        val secondSearch = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = secondSnapshot),
        )
        shareOwner.updateCurrent(secondIdentity, secondSearch.spec.identity)
        entered = CountDownLatch(1)
        release = CountDownLatch(1)
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareFrozen(
            sourceOwner,
            secondSearch,
            currentSourceIdentity = { secondIdentity },
            currentSearchIdentity = { secondSearch.spec.identity },
        )
        val replacementSource = asymmetricBitmap(40, 24)
        val replacementSnapshot = frozenSnapshot(40, 24, 0)
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            sourceOwner.resume()
            captureFrozenFrame(
                sourceOwner,
                replacementSource,
                replacementSnapshot,
                FrameIdentity(8L, 3L),
            )
            assertEquals(replacementSource, sourceOwner.currentBitmap())
        } finally {
            release.countDown()
        }
        waitForGate()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)
        assertFalse(secondSource.isRecycled)
        assertFalse(replacementSource.isRecycled)

        val replacementFrame = checkNotNull(sourceOwner.state.value.frame)
        val replacementIdentity = "frozen:${replacementFrame.identity}"
        val replacementSearch = searchRecognizedText(
            manualSearchSpec("target"),
            OcrUiState(OcrStatus.TextRecognized, snapshot = replacementSnapshot),
        )
        shareOwner.beforeHandoffForTest = null
        shareOwner.updateCurrent(replacementIdentity, replacementSearch.spec.identity)
        shareOwner.shareFrozen(
            sourceOwner,
            replacementSearch,
            currentSourceIdentity = { replacementIdentity },
            currentSearchIdentity = { replacementSearch.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, launches.get())
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)
        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        shareOwner.dispose()
        sourceOwner.resume()
        source.recycle()
        secondSource.recycle()
        replacementSource.recycle()
    }

    @Test
    fun liveManualAndSelectedListMutationsSuppressCapturedProductionPng() {
        verifySessionMutationSuppressesCapturedShare(
            prepare = { controller -> controller.setManualQuery("target") },
            mutate = { controller -> controller.setManualQuery("missing") },
        )
        verifySessionMutationSuppressesCapturedShare(
            prepare = { controller ->
                assertNull(controller.saveList(null, "Selected", "target"))
                val listId = checkNotNull(controller.state.value.persisted.lists.singleOrNull()?.id)
                controller.selectList(listId)
            },
            mutate = { controller ->
                val listId = checkNotNull(controller.state.value.persisted.activeListId)
                assertNull(controller.saveList(listId, "Selected", "missing"))
            },
        )
    }

    @Test
    fun recycledOrDimensionMismatchedSourceIsRejectedBeforeWorkerAdmission() {
        val context = RuntimeEnvironment.getApplication()
        val shareOwner = startedShareOwner(context)
        val (bitmap, snapshot, search) = importedFixture()
        val mismatched = snapshot.copy(width = snapshot.width + 1)
        val importOwner = adoptedOwner(bitmap, mismatched)
        shareOwner.updateCurrent("import:${mismatched.sourceIdentity}", search.spec.identity)
        shareOwner.shareImported(
            importOwner,
            search.copy(content = mismatched),
            currentSourceIdentity = { importedIdentity(mismatched) },
            currentSearchIdentity = { search.spec.identity },
        )
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertFalse(ShareWorkGate.isBusy())
        importOwner.dispose()
        bitmap.recycle()

        val (recycled, recycledSnapshot, recycledSearch) = importedFixture()
        recycled.recycle()
        val recycledOwner = adoptedOwner(recycled, recycledSnapshot)
        shareOwner.updateCurrent(
            "import:${recycledSnapshot.sourceIdentity}",
            recycledSearch.spec.identity,
        )
        shareOwner.shareImported(
            recycledOwner,
            recycledSearch,
            currentSourceIdentity = { importedIdentity(recycledSnapshot) },
            currentSearchIdentity = { recycledSearch.spec.identity },
        )
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertFalse(ShareWorkGate.isBusy())
        shareOwner.dispose()
        recycledOwner.dispose()
    }

    @Test
    fun rejectedShareLaunchDeletesUnhandedFileAndReleasesGateForRetry() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", search.spec.identity)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        shareOwner.beforeHandoffForTest = {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareLauncherForTest = { throw IllegalStateException("No handler") }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { search.spec.identity },
        )
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        release.countDown()
        waitForGate()
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertTrue(shareFiles(context.cacheDir).isEmpty())

        shareOwner.beforeHandoffForTest = null
        var retryOpened = false
        shareOwner.shareLauncherForTest = { retryOpened = true }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { search.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(retryOpened)
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)
        assertEquals(1, shareFiles(context.cacheDir).size)
        shareOwner.dispose()
        importOwner.dispose()
        source.recycle()
    }

    @Test
    fun outOfMemoryDuringChooserLaunchDeletesFileReleasesGateAndAllowsRetry() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        val sourceIdentity = "import:${snapshot.sourceIdentity}"
        shareOwner.updateCurrent(sourceIdentity, search.spec.identity)
        shareOwner.shareLauncherForTest = { throw OutOfMemoryError("injected chooser OOM") }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { search.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(ShareWorkGate.isBusy())
        assertFalse(source.isRecycled)

        var retryOpened = false
        shareOwner.shareLauncherForTest = { retryOpened = true }
        shareOwner.shareImported(
            importOwner,
            search,
            currentSourceIdentity = { sourceIdentity },
            currentSearchIdentity = { search.spec.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(retryOpened)
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)
        assertEquals(1, shareFiles(context.cacheDir).size)
        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        shareOwner.dispose()
        importOwner.dispose()
        source.recycle()
    }

    @Test
    fun providerIsNarrowAndRejectsFilesOutsideDedicatedCachePath() {
        val context = RuntimeEnvironment.getApplication()
        val provider = context.packageManager.getProviderInfo(
            android.content.ComponentName(context, SharedImageProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals("${context.packageName}.shared-images", provider.authority)
        val paths = assertNotNull(
            provider.loadXmlMetaData(
                context.packageManager,
                "android.support.FILE_PROVIDER_PATHS",
            ),
        )
        try {
            var pathCount = 0
            var rootCount = 0
            while (paths.next() != XmlPullParser.END_DOCUMENT) {
                if (paths.eventType == XmlPullParser.START_TAG) {
                    when (paths.name) {
                        "paths" -> rootCount++
                        "cache-path" -> {
                            assertEquals("shared_images", paths.getAttributeValue(null, "name"))
                            assertEquals("shared-images/", paths.getAttributeValue(null, "path"))
                            pathCount++
                        }

                        else -> error("Unexpected provider path: ${paths.name}")
                    }
                }
            }
            assertEquals(1, rootCount)
            assertEquals(1, pathCount)
        } finally {
            paths.close()
        }

        val outside = java.io.File(context.cacheDir, "unrelated.png")
        outside.writeBytes(byteArrayOf(1, 2, 3))
        try {
            FileProvider.getUriForFile(context, provider.authority, outside)
            error("Provider exposed a file outside shared-images.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("configured root"))
        } finally {
            outside.delete()
        }
        java.io.File(context.cacheDir, "shared-images").mkdirs()
        val traversal = java.io.File(context.cacheDir, "shared-images/../unrelated.png")
        traversal.parentFile?.mkdirs()
        traversal.writeBytes(byteArrayOf(1, 2, 3))
        try {
            FileProvider.getUriForFile(context, provider.authority, traversal)
            error("Provider accepted a traversal path outside shared-images.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("configured root"))
        } finally {
            java.io.File(context.cacheDir, "unrelated.png").delete()
        }
    }

    @Test
    fun cacheRemovesExpiredAndPartialFilesButPreservesUnexpiredFilesOnQuotaRejection() {
        val root = Files.createTempDirectory("image-share-cache-test").toFile()
        val directory = java.io.File(root, "shared-images").apply { mkdirs() }
        val retained = java.io.File(directory, "retained.png").apply { writeBytes(byteArrayOf(1)) }
        val expired = java.io.File(directory, "expired.png").apply { writeBytes(byteArrayOf(1)) }
        val partial = java.io.File(directory, "abandoned.partial").apply {
            writeBytes(byteArrayOf(1))
        }
        val now = System.currentTimeMillis()
        retained.setLastModified(now)
        expired.setLastModified(now - TimeUnit.HOURS.toMillis(25))
        partial.setLastModified(now)

        ShareCache.cleanup(root)
        assertTrue(retained.exists())
        assertFalse(expired.exists())
        assertFalse(partial.exists())

        repeat(15) { index ->
            java.io.File(directory, "extra-$index.png").writeBytes(byteArrayOf(2))
        }
        assertFailsWith<ShareQuotaExceededException> { ShareCache.reserve(root) }
        assertTrue(retained.exists())
        assertEquals(16, directory.listFiles().orEmpty().size)
        directory.deleteRecursively()
        root.deleteRecursively()
    }

    @Test
    fun cacheIncludesTheTwentyMegabyteReservationBeforeEncoding() {
        val root = Files.createTempDirectory("image-share-reservation-test").toFile()
        val directory = java.io.File(root, "shared-images").apply { mkdirs() }
        repeat(6) { index ->
            java.io.File(directory, "existing-$index.png").apply {
                createNewFile()
                RandomAccessFile(this, "rw").use { file ->
                    file.setLength(20L * 1024 * 1024)
                }
            }
        }
        assertFailsWith<ShareQuotaExceededException> { ShareCache.reserve(root) }
        assertEquals(6, directory.listFiles().orEmpty().size)
        directory.deleteRecursively()
        root.deleteRecursively()
    }

    @Test
    fun encodedOutputLimitStopsWritesAtTheConfiguredByteCount() {
        val target = ByteArrayOutputStream()
        val capped = EncodedSizeLimitOutputStream(target, 4L)
        capped.write(byteArrayOf(1, 2, 3, 4))
        assertFailsWith<IllegalStateException> { capped.write(5) }
        assertContentEquals(byteArrayOf(1, 2, 3, 4), target.toByteArray())
    }

    @Test
    fun dispatchAllocationEncodingAndMainPostFailuresCleanFilesAndReleaseGate() {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, search) = importedFixture()
        val importOwner = adoptedOwner(source, snapshot)
        val shareOwner = startedShareOwner(context)
        shareOwner.updateCurrent("import:${snapshot.sourceIdentity}", search.spec.identity)
        val sourceIdentity = "import:${snapshot.sourceIdentity}"
        val currentSource = { sourceIdentity }
        val currentSearch = { search.spec.identity }

        shareOwner.dispatchWorkerForTest = { throw RejectedExecutionException() }
        shareOwner.shareImported(importOwner, search, currentSource, currentSearch)
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertFalse(ShareWorkGate.isBusy())
        assertTrue(shareFiles(context.cacheDir).isEmpty())

        shareOwner.dispatchWorkerForTest = null
        shareOwner.renderBitmapForTest = { throw OutOfMemoryError("injected") }
        shareOwner.shareImported(importOwner, search, currentSource, currentSearch)
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertTrue(shareFiles(context.cacheDir).isEmpty())

        shareOwner.renderBitmapForTest = null
        shareOwner.encodeForTest = { output, bitmap ->
            output.encode(bitmap)
            throw IllegalStateException("injected encoder failure")
        }
        shareOwner.shareImported(importOwner, search, currentSource, currentSearch)
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ImageShareStatus.Error, shareOwner.state.value.status)
        assertTrue(shareFiles(context.cacheDir).isEmpty())

        shareOwner.encodeForTest = null
        shareOwner.postToMainForTest = { false }
        shareOwner.shareImported(importOwner, search, currentSource, currentSearch)
        waitForGate()
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertEquals(ImageShareStatus.Preparing, shareOwner.state.value.status)
        shareOwner.cancel()
        assertEquals(ImageShareStatus.Idle, shareOwner.state.value.status)

        shareOwner.postToMainForTest = null
        var retryOpened = false
        shareOwner.shareLauncherForTest = { retryOpened = true }
        shareOwner.shareImported(importOwner, search, currentSource, currentSearch)
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(retryOpened)
        assertEquals(ImageShareStatus.Opened, shareOwner.state.value.status)
        shareOwner.dispose()
        importOwner.dispose()
        source.recycle()
    }

    private fun startedShareOwner(context: android.content.Context): ImageShareOwner {
        waitForGate()
        return ImageShareOwner().apply {
            attachContext(context)
            start()
            waitForGate()
        }
    }

    private fun adoptedOwner(
        bitmap: Bitmap,
        snapshot: ImportedTextSnapshot,
    ): ImportedImageOwner {
        val owner = ImportedImageOwner()
        val ticket = checkNotNull(owner.beginPicker())
        assertTrue(owner.adoptProcessedImage(ticket, ProcessedImportedImage(bitmap, snapshot)))
        return owner
    }

    private fun createFrozenOwner(
        bitmap: Bitmap,
        snapshot: OcrSnapshot,
        identity: FrameIdentity,
    ): FrozenFrameOwner {
        val owner = FrozenFrameOwner()
        owner.attachSession(
            requestCapture = { true },
            cancelCapture = { requestId -> assertTrue(requestId > 0L) },
        )
        assertTrue(owner.freeze(cameraReady = true))
        val requestId = checkNotNull(owner.state.value.requestId)
        assertTrue(
            owner.accept(
                CapturedCameraFrame(
                    requestId = requestId,
                    bitmap = bitmap,
                    snapshot = snapshot,
                    identity = identity,
                ),
            ),
        )
        return owner
    }

    private fun captureFrozenFrame(
        owner: FrozenFrameOwner,
        bitmap: Bitmap,
        snapshot: OcrSnapshot,
        identity: FrameIdentity,
    ) {
        assertTrue(owner.freeze(cameraReady = true))
        val requestId = checkNotNull(owner.state.value.requestId)
        assertTrue(
            owner.accept(
                CapturedCameraFrame(
                    requestId = requestId,
                    bitmap = bitmap,
                    snapshot = snapshot,
                    identity = identity,
                ),
            ),
        )
    }

    private fun verifySessionMutationSuppressesCapturedShare(
        prepare: (SearchSessionController) -> Unit,
        mutate: (SearchSessionController) -> Unit,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val (source, snapshot, _) = importedFixture()
        val imageOwner = adoptedOwner(source, snapshot)
        val session = inMemorySearchSessionController()
        prepare(session)
        val capturedSearch = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        assertTrue(capturedSearch.occurrences.isNotEmpty())
        val originalIdentity = session.state.value.activeSearch.identity
        val shareOwner = startedShareOwner(context)
        val heldWorker = AtomicReference<Runnable?>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val capturedBorder = AtomicInteger()
        val capturedLandmark = AtomicInteger()
        val launches = AtomicInteger()
        shareOwner.updateCurrent(importedIdentity(snapshot), originalIdentity)
        shareOwner.dispatchWorkerForTest = { worker -> heldWorker.set(worker) }
        shareOwner.beforeHandoffForTest = {
            val published = checkNotNull(BitmapFactory.decodeFile(shareFiles(context.cacheDir)
                .single().path))
            capturedBorder.set(published.getPixel(5, 10))
            capturedLandmark.set(published.getPixel(1, 1))
            published.recycle()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        shareOwner.shareLauncherForTest = { launches.incrementAndGet() }
        shareOwner.shareImported(
            imageOwner,
            capturedSearch,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
        )
        val worker = checkNotNull(heldWorker.get())
        mutate(session)
        val changedIdentity = session.state.value.activeSearch.identity
        assertTrue(changedIdentity != originalIdentity)
        val currentSearch = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        assertTrue(currentSearch.occurrences.isEmpty())
        assertTrue(
            projectImportedHighlights(currentSearch, snapshot, 40f, 24f).outlines.isEmpty(),
        )

        try {
            ShareWorkGate.executor.execute(worker)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(Color.BLACK, capturedBorder.get())
            assertEquals(Color.RED, capturedLandmark.get())
        } finally {
            release.countDown()
        }
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, launches.get())
        assertTrue(shareFiles(context.cacheDir).isEmpty())
        assertFalse(source.isRecycled)

        shareOwner.updateCurrent(importedIdentity(snapshot), changedIdentity)
        shareOwner.dispatchWorkerForTest = null
        shareOwner.beforeHandoffForTest = null
        var chooser: Intent? = null
        shareOwner.shareLauncherForTest = { intent -> chooser = intent }
        shareOwner.shareImported(
            imageOwner,
            currentSearch,
            currentSourceIdentity = { importedIdentity(snapshot) },
            currentSearchIdentity = { session.state.value.activeSearch.identity },
        )
        waitForGate()
        shadowOf(Looper.getMainLooper()).idle()
        val chooserIntent = assertNotNull(chooser)
        val sendIntent = assertNotNull(
            IntentCompat.getParcelableExtra(
                chooserIntent,
                Intent.EXTRA_INTENT,
                Intent::class.java,
            ),
        )
        val uri = assertNotNull(
            IntentCompat.getParcelableExtra(sendIntent, Intent.EXTRA_STREAM, Uri::class.java),
        )
        val decoded = context.contentResolver.openInputStream(uri)!!.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
        assertEquals(source.getPixel(5, 10), decoded.getPixel(5, 10))
        assertEquals(Color.RED, decoded.getPixel(1, 1))
        decoded.recycle()
        shareFiles(context.cacheDir).forEach { file -> file.delete() }
        shareOwner.dispose()
        imageOwner.dispose()
        source.recycle()
    }

    private fun importedFixture(): Triple<Bitmap, ImportedTextSnapshot,
        com.artkachenko.livetextfinder.search.TextSearchResult> {
        val bitmap = asymmetricBitmap(40, 24)
        val snapshot = ImportedTextSnapshot(
            text = "target",
            lines = listOf(textLine()),
            width = bitmap.width,
            height = bitmap.height,
            sourceIdentity = 73L,
        )
        return Triple(
            bitmap,
            snapshot,
            searchRecognizedContent(manualSearchSpec("target"), snapshot),
        )
    }

    private fun frozenSnapshot(width: Int, height: Int, rotation: Int) = OcrSnapshot(
        text = "target",
        lines = listOf(textLine()),
        frame = FrameInfo(
            timestampNanos = 1L,
            bufferWidth = width,
            bufferHeight = height,
            rotationDegrees = rotation,
            crop = TextBounds(0, 0, width, height),
            sensorToBufferMatrix = listOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
            ),
        ),
    )

    private fun textLine() = TextLine(
        text = "target",
        bounds = TextBounds(8, 6, 20, 15),
        corners = emptyList(),
        elements = listOf(
            TextElement(
                text = "target",
                bounds = TextBounds(8, 6, 20, 15),
                corners = listOf(
                    TextPoint(8, 6),
                    TextPoint(20, 6),
                    TextPoint(20, 15),
                    TextPoint(8, 15),
                ),
            ),
        ),
    )

    private fun asymmetricBitmap(width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(
                        x,
                        y,
                        when {
                            x < width / 2 && y < height / 2 -> Color.RED
                            x >= width / 2 && y < height / 2 -> Color.GREEN
                            x < width / 2 -> Color.BLUE
                            else -> Color.YELLOW
                        },
                    )
                }
            }
        }

    private fun shareFiles(cacheDir: java.io.File) =
        java.io.File(cacheDir, "shared-images").listFiles().orEmpty()
            .filter { file -> file.extension == "png" }

    private fun importedIdentity(snapshot: ImportedTextSnapshot) =
        "import:${snapshot.sourceIdentity}"

    private fun waitForGate() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (ShareWorkGate.isBusy() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertFalse(ShareWorkGate.isBusy())
    }
}
