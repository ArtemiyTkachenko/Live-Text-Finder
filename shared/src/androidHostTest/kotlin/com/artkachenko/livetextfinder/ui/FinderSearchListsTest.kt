package com.artkachenko.livetextfinder.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.AppContentColumn
import com.artkachenko.livetextfinder.search.PersistedSearchState
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.SearchSessionStore
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinderSearchListsTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null
    private var contentScrollState: ScrollState? = null

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun productionListRowsExposeLocalizedCountsSelectionAndIndependentActions() {
        val first = SavedSearchList("one", "Parts", listOf("bolt"))
        val second = SavedSearchList("two", "Monthly review", listOf("invoice", "receipt"))
        val selected = mutableStateOf<String?>(first.id)
        val selectCalls = mutableListOf<String>()
        val editCalls = mutableListOf<SavedSearchList>()
        val deleteCalls = mutableListOf<SavedSearchList>()
        val editor = mutableStateOf(SearchListEditorState())
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchLists(
                        lists = listOf(first, second),
                        activeListId = selected.value,
                        editor = editor.value,
                        canSave = true,
                        onNew = { editor.value = editor.value.startNew() },
                        onSelect = { id ->
                            selectCalls += id
                            selected.value = id
                        },
                        onEdit = { list -> editCalls += list },
                        onDelete = { list -> deleteCalls += list },
                        onNameChange = ::ignoreText,
                        onTermsChange = ::ignoreText,
                        onSave = ::ignoreAction,
                        onCancel = ::ignoreAction,
                    )
                }
            }
        }

        var nodes = semanticsNodes()
        assertTrue(nodes.any { it.textValues().contains("1 term") })
        assertTrue(nodes.any { it.textValues().contains("2 terms") })
        assertTrue(nodes.any { it.textValues().contains("Selected") })
        val selectedRow = nodeWithDescription(nodes, "Select Parts")
        assertEquals(true, selectedRow.config.getOrNull(SemanticsProperties.Selected))
        assertTouchBounds(selectedRow)
        val editParts = nodeWithDescription(nodes, "Edit Parts")
        val deleteParts = nodeWithDescription(nodes, "Delete Parts")
        assertTouchBounds(editParts)
        assertTouchBounds(deleteParts)
        assertFalse(intersects(selectedRow.touchBoundsInRoot, editParts.touchBoundsInRoot))
        assertFalse(intersects(selectedRow.touchBoundsInRoot, deleteParts.touchBoundsInRoot))
        assertFalse(intersects(editParts.touchBoundsInRoot, deleteParts.touchBoundsInRoot))
        assertTouchBounds(nodeWithText(nodes, "New list"))
        assertRendered("lists-light-populated.png")

        click(nodeWithDescription(nodes, "Select Monthly review"))
        assertEquals("two", selected.value)
        assertEquals(listOf("two"), selectCalls)
        nodes = semanticsNodes()
        click(nodeWithDescription(nodes, "Edit Parts"))
        assertEquals(listOf(first), editCalls)
        assertEquals(listOf("two"), selectCalls)
        assertEquals("two", selected.value)
        click(nodeWithDescription(semanticsNodes(), "Delete Monthly review"))
        assertEquals(listOf(second), deleteCalls)
        assertEquals(listOf("two"), selectCalls)
        assertEquals("two", selected.value)
    }

    @Test
    fun editorUsesRealResourcesAndKeepsCancelUsableWhenSavingIsGated() {
        val editor = mutableStateOf(
            SearchListEditorState(
                isOpen = true,
                editingId = "list-1",
                name = "A very long search list name that remains fully readable",
                terms = "first\nsecond",
                error = "Enter a list name.",
            ),
        )
        var changedName = ""
        var changedTerms = ""
        var cancelCount = 0
        mount(widthDp = 320, heightDp = 640, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchLists(
                        lists = emptyList(),
                        activeListId = null,
                        editor = editor.value,
                        canSave = false,
                        onNew = ::ignoreAction,
                        onSelect = ::ignoreText,
                        onEdit = ::ignoreList,
                        onDelete = ::ignoreList,
                        onNameChange = { changedName = it },
                        onTermsChange = { changedTerms = it },
                        onSave = ::ignoreAction,
                        onCancel = { cancelCount++ },
                    )
                }
            }
        }

        val nodes = semanticsNodes()
        val newList = nodeWithText(nodes, "New list")
        assertTrue(newList.config.contains(SemanticsProperties.Disabled))
        assertTouchBounds(newList)
        assertTrue(nodes.any { it.textValues().contains("Edit list") })
        assertTrue(nodes.any { it.textValues().contains("List name") })
        assertTrue(nodes.any { it.textValues().contains("Terms or phrases · one per line") })
        assertTrue(nodes.any { it.textValues().contains("Enter a list name.") })
        val fields = nodes.filter { it.config.getOrNull(SemanticsProperties.EditableText) != null }
        assertEquals(2, fields.size)
        val nameField = fields.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text ==
                "A very long search list name that remains fully readable"
        }
        val nameSetText = checkNotNull(nameField.config.getOrNull(SemanticsActions.SetText)?.action)
        assertTrue(nameSetText(AnnotatedString("Edited name")))
        val termsField = fields.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "first\nsecond"
        }
        val termsSetText = checkNotNull(
            termsField.config.getOrNull(SemanticsActions.SetText)?.action,
        )
        assertTrue(termsSetText(AnnotatedString("updated term")))
        assertEquals("Edited name", changedName)
        assertEquals("updated term", changedTerms)
        val save = nodeWithText(semanticsNodes(), "Save list")
        assertTrue(save.config.contains(SemanticsProperties.Disabled))
        assertTouchBounds(save)
        val cancel = nodeWithText(semanticsNodes(), "Cancel")
        assertFalse(cancel.config.contains(SemanticsProperties.Disabled))
        assertTouchBounds(cancel)
        assertFalse(intersects(save.touchBoundsInRoot, cancel.touchBoundsInRoot))
        assertRendered("lists-dark-editor-error.png")
        click(cancel)
        assertEquals(1, cancelCount)
    }

    @Test
    fun compactListScreenScrollsToEditorActionsAndBackToTop() {
        val lists = (1..50).map { index ->
            SavedSearchList("id-$index", "Saved list $index", listOf("term $index"))
        }
        val editor = SearchListEditorState(
            isOpen = true,
            name = "Final search list",
            terms = "alpha\nbeta",
        )
        val scrollState = ScrollState(0)
        contentScrollState = scrollState
        mount(widthDp = 640, heightDp = 320) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = scrollState,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    ) {
                        FinderSearchControls(
                            showTitle = false,
                            query = "invoice",
                            onQueryChange = ::ignoreText,
                            searchListsLabel = "Close lists",
                            onSearchLists = ::ignoreAction,
                            searchContext = "Monthly review · 2 terms",
                            highlightLabel = "Highlight color: Yellow",
                            highlightEnabled = true,
                            onHighlight = ::ignoreAction,
                            onHelp = ::ignoreAction,
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            liveAreaLabel = null,
                            onLiveArea = ::ignoreAction,
                            secondaryActionLabel = "Import image",
                            onSecondaryAction = ::ignoreAction,
                        )
                        FinderSearchLists(
                            lists = lists,
                            activeListId = "id-1",
                            editor = editor,
                            canSave = true,
                            onNew = ::ignoreAction,
                            onSelect = ::ignoreText,
                            onEdit = ::ignoreList,
                            onDelete = ::ignoreList,
                            onNameChange = ::ignoreText,
                            onTermsChange = ::ignoreText,
                            onSave = ::ignoreAction,
                            onCancel = ::ignoreAction,
                        )
                    }
                }
            }
        }

        assertTrue(scrollState.maxValue > 0)
        assertRendered("combined-landscape-many-lists-top.png")
        runBlockingScroll(scrollState.maxValue)
        var nodes = semanticsNodes()
        val save = nodeWithText(nodes, "Save list")
        val cancel = nodeWithText(nodes, "Cancel")
        assertTouchBounds(save)
        assertTouchBounds(cancel)
        assertWithinViewport(save)
        assertWithinViewport(cancel)
        assertRendered("combined-landscape-many-lists-bottom.png")
        runBlockingScroll(0)
        nodes = semanticsNodes()
        val query = nodes.single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == "invoice"
        }
        val closeLists = nodeWithText(nodes, "Close lists")
        assertWithinViewport(query)
        assertWithinViewport(closeLists)
        assertTrue(closeLists.config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun selectedSixtyCharacterListNameWrapsAtLargeFontWithCombinedSearchHeader() {
        val longName = "Alpha bravo charlie delta echo foxtrot golf hotel india".padEnd(60, 'x')
        assertEquals(60, longName.length)
        val scrollState = ScrollState(0)
        contentScrollState = scrollState
        mount(widthDp = 320, heightDp = 560, fontScale = 1.5f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    AppContentColumn(
                        scrollState = scrollState,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        FinderSearchControls(
                            showTitle = false,
                            query = "invoice",
                            onQueryChange = ::ignoreText,
                            searchListsLabel = "Close lists",
                            onSearchLists = ::ignoreAction,
                            searchContext = "Monthly review · 2 terms",
                            highlightLabel = "Highlight color: Yellow",
                            highlightEnabled = true,
                            onHighlight = ::ignoreAction,
                            onHelp = ::ignoreAction,
                            captureLabel = null,
                            captureEnabled = false,
                            onCapture = ::ignoreAction,
                            pendingCaptureLabel = null,
                            onCancelCapture = ::ignoreAction,
                            liveAreaLabel = null,
                            onLiveArea = ::ignoreAction,
                            secondaryActionLabel = "Import image",
                            onSecondaryAction = ::ignoreAction,
                        )
                        FinderSearchLists(
                            lists = listOf(
                                SavedSearchList("long", longName, listOf("invoice")),
                            ),
                            activeListId = "long",
                            editor = SearchListEditorState(),
                            canSave = true,
                            onNew = ::ignoreAction,
                            onSelect = ::ignoreText,
                            onEdit = ::ignoreList,
                            onDelete = ::ignoreList,
                            onNameChange = ::ignoreText,
                            onTermsChange = ::ignoreText,
                            onSave = ::ignoreAction,
                            onCancel = ::ignoreAction,
                        )
                    }
                }
            }
        }

        assertTrue(scrollState.maxValue > 0)
        runBlockingScroll(scrollState.maxValue)
        val nodes = semanticsNodes()
        val nameNode = nodes.single { it.textValues().contains(longName) }
        val selectionNode = nodeWithDescription(nodes, "Select $longName")
        assertWithinViewport(selectionNode)
        val results = mutableListOf<TextLayoutResult>()
        val getTextLayoutResult = checkNotNull(
            nameNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action,
        )
        assertTrue(getTextLayoutResult(results))
        assertEquals(1, results.size)
        assertTrue(results.single().lineCount > 1)
        assertFalse(results.single().hasVisualOverflow)
        assertRendered("combined-dark-long-selected-name.png")
    }

    @Test
    fun controllerBackedEditorRetainsDraftOnSelectAndInvalidSaveThenClosesOnSaveOrCancel() {
        val lists = listOf(
            SavedSearchList("existing-a", "Parts", listOf("bolt")),
            SavedSearchList("existing-b", "Invoices", listOf("invoice")),
        )
        val controller = SearchSessionController(
            ImmediateSearchSessionStore(
                PersistedSearchState("manual query", lists, "existing-a"),
            ),
        )
        val editor = mutableStateOf(
            SearchListEditorState().startNew().copy(name = "Draft list", terms = ""),
        )
        mount(widthDp = 360, heightDp = 800) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    FinderSearchLists(
                        lists = controller.state.value.persisted.lists,
                        activeListId = controller.state.value.persisted.activeListId,
                        editor = editor.value,
                        canSave = controller.state.value.loaded &&
                            !controller.state.value.recoveryRequired,
                        onNew = { editor.value = editor.value.startNew() },
                        onSelect = controller::selectList,
                        onEdit = { list -> editor.value = editor.value.edit(list) },
                        onDelete = ::ignoreList,
                        onNameChange = { editor.value = editor.value.copy(name = it) },
                        onTermsChange = { editor.value = editor.value.copy(terms = it) },
                        onSave = {
                            val error = controller.saveList(
                                editor.value.editingId,
                                editor.value.name,
                                editor.value.terms,
                            )
                            editor.value = if (error == null) {
                                editor.value.close()
                            } else {
                                editor.value.copy(error = error)
                            }
                        },
                        onCancel = { editor.value = editor.value.close() },
                    )
                }
            }
        }
        assertRendered("lists-light-new-editor.png")

        click(nodeWithDescription(semanticsNodes(), "Select Invoices"))
        assertEquals("existing-b", controller.state.value.persisted.activeListId)
        assertEquals("Draft list", editor.value.name)
        assertTrue(editor.value.isOpen)

        click(nodeWithText(semanticsNodes(), "Save list"))
        assertEquals(
            "Remove blank term lines. Each line must contain a term or phrase.",
            editor.value.error,
        )
        assertEquals("Draft list", editor.value.name)
        assertEquals("", editor.value.terms)
        assertTrue(editor.value.isOpen)

        val terms = semanticsNodes().single {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
        }
        val setTerms = checkNotNull(terms.config.getOrNull(SemanticsActions.SetText)?.action)
        assertTrue(setTerms(AnnotatedString("receipt")))
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        assertEquals("receipt", editor.value.terms)
        click(nodeWithText(semanticsNodes(), "Save list"))
        assertFalse(editor.value.isOpen)
        assertEquals(3, controller.state.value.persisted.lists.size)
        assertEquals("manual query", controller.state.value.persisted.manualQuery)

        val persistedBeforeCancel = controller.state.value.persisted
        editor.value = SearchListEditorState().startNew().copy(name = "Cancelled", terms = "draft")
        Snapshot.sendApplyNotifications()
        measureCurrentComposition()
        click(nodeWithText(semanticsNodes(), "Cancel"))
        assertFalse(editor.value.isOpen)
        assertEquals(persistedBeforeCancel, controller.state.value.persisted)
    }

    private fun mount(
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        composeView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        this.activity = activity
        this.composeView = composeView
        composeView.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    activity.resources.displayMetrics.density,
                    fontScale,
                ),
            ) {
                content()
            }
        }
        activity.setContentView(composeView)
        measureAt(composeView, activity, widthDp, heightDp)
        shadowOf(Looper.getMainLooper()).idle()
        measureAt(composeView, activity, widthDp, heightDp)
        (composeView.getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
    }

    private fun measureAt(view: ComposeView, activity: Activity, widthDp: Int, heightDp: Int) {
        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        view.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
    }

    private fun semanticsNodes(): List<SemanticsNode> {
        val root = (checkNotNull(composeView).getChildAt(0) as ViewRootForTest)
            .semanticsOwner.rootSemanticsNode
        val result = mutableListOf<SemanticsNode>()

        fun collect(node: SemanticsNode) {
            result += node
            node.children.forEach(::collect)
        }

        collect(root)
        return result
    }

    private fun nodeWithDescription(
        nodes: List<SemanticsNode>,
        description: String,
    ): SemanticsNode =
        checkNotNull(
            nodes.firstOrNull {
                it.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.contains(description) == true
            },
        ) {
            "No semantics node with description '$description'"
        }

    private fun nodeWithText(
        nodes: List<SemanticsNode>,
        text: String,
    ): SemanticsNode = checkNotNull(
        nodes.firstOrNull { it.textValues().contains(text) },
    ) {
        "No semantics node with text '$text'"
    }

    private fun SemanticsNode.textValues(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private fun intersects(first: Rect, second: Rect): Boolean =
        first.left < second.right && first.right > second.left &&
            first.top < second.bottom && first.bottom > second.top

    private fun assertTouchBounds(node: SemanticsNode) {
        val density = checkNotNull(composeView).resources.displayMetrics.density
        assertTrue(node.touchBoundsInRoot.width / density >= 48f)
        assertTrue(node.touchBoundsInRoot.height / density >= 48f)
    }

    private fun SemanticsNode.unclippedBoundsInRoot(): Rect {
        val position = positionInRoot
        return Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }

    private fun assertWithinViewport(bounds: Rect) {
        val view = checkNotNull(composeView)
        assertTrue(bounds.left >= 0f && bounds.top >= 0f)
        assertTrue(bounds.right <= view.width && bounds.bottom <= view.height)
    }

    private fun assertWithinViewport(node: SemanticsNode) {
        assertWithinViewport(node.unclippedBoundsInRoot())
    }

    private fun click(node: SemanticsNode) {
        assertTrue(checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action)())
        measureCurrentComposition()
    }

    private fun measureCurrentComposition() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
        (checkNotNull(composeView).getChildAt(0) as ViewRootForTest).measureAndLayoutForTest()
    }

    private fun runBlockingScroll(value: Int) {
        runBlocking {
            checkNotNull(contentScrollState).scrollTo(value)
        }
        measureCurrentComposition()
    }

    private fun assertRendered(fileName: String) {
        val view = checkNotNull(composeView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val colors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                colors += bitmap.getPixel(x, y)
                if (colors.size > 16) {
                    break
                }
            }
            if (colors.size > 16) {
                break
            }
        }
        assertTrue(colors.size > 16, "Expected visible rendered manager content")
        System.getenv("LTF_VISUAL_OUTPUT_DIR")?.let { directory ->
            val output = File(directory, fileName)
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
        }
        bitmap.recycle()
    }

    private fun ignoreAction() = Unit

    private fun ignoreList(list: SavedSearchList) = Unit

    private fun ignoreText(value: String) = Unit

    private class ImmediateSearchSessionStore(
        private val initialValue: PersistedSearchState,
    ) : SearchSessionStore {
        override fun load(callback: (Result<PersistedSearchState?>) -> Unit) {
            callback(Result.success(initialValue))
        }

        override fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit) {
            callback(Result.success(Unit))
        }

        override fun reset(callback: (Result<Unit>) -> Unit) {
            callback(Result.success(Unit))
        }
    }
}
