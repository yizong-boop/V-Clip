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
        assertEquals(0L, controller.deferredBlurHideAtEpochMillis)
        // Grace period is shortened to 400 ms after focus is gained to still
        // absorb focus flutter while allowing intentional blur dismissal quickly.
        assertEquals(1_400L, controller.ignoreBlurBeforeEpochMillis)
    }

    @Test
    fun blurDuringGracePeriodDefersHideEvenAfterFocusGained() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        controller.prepareShow(ignoreBlurWindowMs = 500L)
        controller.onWindowGainedFocus()
        controller.onWindowLostFocus()

        // Blur during the shortened grace period (400 ms after focus) is deferred.
        assertFalse(controller.shouldHideOnBlur(nowEpochMillis = 1_100L))
        assertTrue(controller.shouldHideDeferredBlur(nowEpochMillis = 1_500L))
    }

    @Test
    fun blurAfterShortenedGraceHidesImmediately() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        controller.prepareShow(ignoreBlurWindowMs = 500L)
        controller.onWindowGainedFocus()
        controller.onWindowLostFocus()

        // After the shortened 400 ms grace, blur hides immediately.
        assertTrue(controller.shouldHideOnBlur(nowEpochMillis = 1_500L))
    }

    @Test
    fun focusGainedDoesNotLengthenGrace() {
        val now = 1_000L
        val controller = SpotlightWindowController(clock = { now })

        // Short original grace (100 ms) — focus-gained should not extend it.
        controller.prepareShow(ignoreBlurWindowMs = 100L)
        controller.onWindowGainedFocus()
        controller.onWindowLostFocus()

        // Blur shortly after: the original grace (1100 ms) is already past the
        // shortened cap (1400 ms), so `ignoreBlurBeforeEpochMillis` stays at 1100.
        assertTrue(controller.shouldHideOnBlur(nowEpochMillis = 1_200L))
    }
}
