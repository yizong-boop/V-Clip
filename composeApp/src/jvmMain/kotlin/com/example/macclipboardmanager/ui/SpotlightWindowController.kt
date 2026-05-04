package com.example.macclipboardmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages window-level focus state for the Spotlight overlay.
 *
 * Encapsulates the blur-ignore window, pending focus requests, and the
 * focus-request key that drives [androidx.compose.ui.focus.FocusRequester]
 * retries. Kept as a plain class so the focus strategy can be tested
 * independently of Compose LaunchedEffects.
 */
class SpotlightWindowController(
    initialFocusRequestKey: Int = 0,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    var isWindowFocused by mutableStateOf(false)
        private set

    var ignoreBlurBeforeEpochMillis by mutableStateOf(0L)
        private set

    var deferredBlurHideAtEpochMillis by mutableStateOf(0L)
        private set

    var deferredBlurHideRequestKey by mutableIntStateOf(0)
        private set

    var pendingSearchFieldFocus by mutableStateOf(false)
        private set

    var focusRequestKey by mutableIntStateOf(initialFocusRequestKey)
        private set

    private var hasDeferredBlurHide by mutableStateOf(false)

    fun prepareShow(ignoreBlurWindowMs: Long = 1_200L) {
        isWindowFocused = false
        ignoreBlurBeforeEpochMillis = clock() + ignoreBlurWindowMs
        deferredBlurHideAtEpochMillis = 0L
        hasDeferredBlurHide = false
        pendingSearchFieldFocus = true
    }

    fun onShowComplete() {
        isWindowFocused = false
        ignoreBlurBeforeEpochMillis = 0L
        deferredBlurHideAtEpochMillis = 0L
        hasDeferredBlurHide = false
        pendingSearchFieldFocus = false
    }

    fun onWindowGainedFocus() {
        isWindowFocused = true
        deferredBlurHideAtEpochMillis = 0L
        hasDeferredBlurHide = false
        deferredBlurHideRequestKey += 1

        // Shorten the blur grace period to a post-focus window long enough to
        // absorb focus flutter during the show-phase retry burst (~624 ms),
        // but short enough that intentional blur dismissal (clicking away,
        // Cmd+Tab) takes effect quickly instead of waiting the full 1 200 ms.
        val shortGrace = clock() + 400L
        if (ignoreBlurBeforeEpochMillis > shortGrace) {
            ignoreBlurBeforeEpochMillis = shortGrace
        }
    }

    fun onWindowLostFocus() {
        isWindowFocused = false
    }

    fun requestSearchFieldFocus() {
        if (pendingSearchFieldFocus) {
            focusRequestKey += 1
            pendingSearchFieldFocus = false
        }
    }

    fun forceFocusRequest() {
        focusRequestKey += 1
        pendingSearchFieldFocus = false
    }

    fun shouldHideOnBlur(nowEpochMillis: Long): Boolean {
        if (nowEpochMillis >= ignoreBlurBeforeEpochMillis) {
            return true
        }

        hasDeferredBlurHide = true
        deferredBlurHideAtEpochMillis = ignoreBlurBeforeEpochMillis
        deferredBlurHideRequestKey += 1
        return false
    }

    fun shouldHideDeferredBlur(nowEpochMillis: Long): Boolean =
        hasDeferredBlurHide &&
            !isWindowFocused &&
            deferredBlurHideAtEpochMillis > 0L &&
            nowEpochMillis >= deferredBlurHideAtEpochMillis
}
