package com.example.macclipboardmanager.macos.clipboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardChangeTrackerTest {
    @Test
    fun selfWrittenChangeCountIsIgnored() {
        val tracker = ClipboardChangeTracker()

        tracker.markObserved(changeCount = 1L)
        tracker.markSelfWritten(changeCount = 2L)

        assertFalse(tracker.shouldRead(changeCount = 2L))
        assertTrue(tracker.shouldRead(changeCount = 3L))
    }

    @Test
    fun unchangedChangeCountIsIgnored() {
        val tracker = ClipboardChangeTracker()

        tracker.markObserved(changeCount = 5L)

        assertFalse(tracker.shouldRead(changeCount = 5L))
        assertTrue(tracker.shouldRead(changeCount = 6L))
    }
}
