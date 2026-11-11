package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.camera.ImageShareState
import com.artkachenko.livetextfinder.camera.ImageShareStatus
import com.artkachenko.livetextfinder.search.SearchListTransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinderFeedbackTest {
    @Test
    fun shareOutcome_isAnnouncedOnlyWhenOpenedOrFailed() {
        assertNull(ImageShareState().outcomeMessage())
        assertNull(ImageShareState(ImageShareStatus.Preparing, "Preparing").outcomeMessage())
        assertEquals("Opened", ImageShareState(ImageShareStatus.Opened, "Opened").outcomeMessage())
        assertEquals("Failed", ImageShareState(ImageShareStatus.Error, "Failed").outcomeMessage())
    }

    @Test
    fun transferOutcome_isAnnouncedOnlyAfterTheTransferFinishes() {
        assertNull(SearchListTransferState().outcomeMessage())
        assertNull(SearchListTransferState(busy = true, message = "Reading").outcomeMessage())
        assertEquals("Done", SearchListTransferState(message = "Done").outcomeMessage())
        assertEquals(
            "Failed",
            SearchListTransferState(message = "Failed", isError = true).outcomeMessage(),
        )
    }

    @Test
    fun copyFeedbackCount_advancesForRepeatedOutcomeButNotForClearedMessage() {
        val session = StillMatchCopySession()
        session.updateDialogOpen(true)
        val opening = session.openingToken

        session.fail(opening, "Failed")
        session.fail(opening, "Failed")

        assertEquals("Failed", session.message)
        assertEquals(2, session.feedbackCount)

        session.updateDialogOpen(false)

        assertNull(session.message)
        assertEquals(2, session.feedbackCount)
    }
}
