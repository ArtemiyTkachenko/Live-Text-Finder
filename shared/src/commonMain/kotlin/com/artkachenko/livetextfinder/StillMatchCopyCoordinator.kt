package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.StillMatchRow
import com.artkachenko.livetextfinder.search.StillMatchSourceState
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.copyStillMatch
import kotlinx.coroutines.CancellationException

internal enum class StillMatchCopyOutcome {
    Copied,
    Stale,
    Failed,
}

/** Reads the live still owners and search state before writing one selected match. */
internal class StillMatchCopyCoordinator(
    private val importedOwner: ImportedImageOwner,
    private val frozenOwner: FrozenFrameOwner,
    private val session: SearchSessionController,
    private val selectedIndex: () -> Int?,
    private val writeText: suspend (String) -> Unit,
    private val contextIsCurrent: () -> Boolean = { true },
) {
    suspend fun copy(
        row: StillMatchRow,
        result: TextSearchResult,
        sourceIdentity: String,
        searchIdentity: String,
        selectedMatchIndex: Int,
        ownerToken: Any,
    ): StillMatchCopyOutcome {
        return try {
            val copied = copyStillMatch(
                row = row,
                result = result,
                expectedSourceIdentity = sourceIdentity,
                expectedSearchIdentity = searchIdentity,
                selectedIndex = selectedMatchIndex,
                expectedOwnerToken = ownerToken,
                currentSource = {
                    when {
                        importedOwner.state.value.phase == ImportedImagePhase.Picking ||
                            importedOwner.state.value.phase == ImportedImagePhase.Reading ||
                            importedOwner.state.value.phase == ImportedImagePhase.Recognizing ->
                            StillMatchSourceState(null, null, null)
                        importedOwner.state.value.phase == ImportedImagePhase.Imported -> {
                            val snapshot = importedOwner.state.value.snapshot
                            StillMatchSourceState(
                                sourceIdentity = snapshot?.let { "import:${it.sourceIdentity}" },
                                content = snapshot,
                                ownerToken = snapshot,
                            )
                        }
                        frozenOwner.state.value.mode == FreezeMode.Frozen -> {
                            val frame = frozenOwner.state.value.frame
                            StillMatchSourceState(
                                sourceIdentity = frame?.let { "frozen:${it.identity}" },
                                content = frame?.snapshot,
                                ownerToken = frame,
                            )
                        }
                        else -> StillMatchSourceState(null, null, null)
                    }
                },
                currentSearchIdentity = { session.state.value.activeSearch.identity },
                currentSelectedIndex = selectedIndex,
                writeText = writeText,
                currentContextCurrent = contextIsCurrent,
            )
            if (copied) StillMatchCopyOutcome.Copied else StillMatchCopyOutcome.Stale
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            StillMatchCopyOutcome.Failed
        }
    }
}
