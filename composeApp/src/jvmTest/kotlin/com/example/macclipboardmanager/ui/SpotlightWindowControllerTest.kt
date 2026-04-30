package com.example.macclipboardmanager.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotlightWindowControllerTest {
    @Test
    fun blurDuringShowGracePeriodDefersHide() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        controller.prepareShow(ignoreBlurWindowMs = 500L)

        assertFalse(controller.shouldHideOnBlur(nowEpochMillis = 1_100L))
        assertEquals(1_500L, controller.deferredBlurHideAtEpochMillis)
        assertFalse(controller.shouldHideDeferredBlur(nowEpochMillis = 1_499L))
        assertTrue(controller.shouldHideDeferredBlur(nowEpochMillis = 1_500L))
    }

    @Test
    fun gainingFocusClearsDeferredBlurHide() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        controller.prepareShow(ignoreBlurWindowMs = 500L)
        controller.shouldHideOnBlur(nowEpochMillis = 1_100L)
        controller.onWindowGainedFocus()

        assertFalse(controller.shouldHideDeferredBlur(nowEpochMillis = 1_500L))
        assertEquals(0L, controller.ignoreBlurBeforeEpochMillis)
        assertEquals(0L, controller.deferredBlurHideAtEpochMillis)
    }

    @Test
    fun blurAfterWindowFocusedHidesImmediately() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        controller.prepareShow(ignoreBlurWindowMs = 500L)
        controller.onWindowGainedFocus()
        controller.onWindowLostFocus()

        assertTrue(controller.shouldHideOnBlur(nowEpochMillis = 1_100L))
    }
}
