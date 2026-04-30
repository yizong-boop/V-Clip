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

    var pendingSearchFieldFocus by mutableStateOf(false)
        private set

    var focusRequestKey by mutableIntStateOf(initialFocusRequestKey)
        private set

    fun prepareShow(ignoreBlurWindowMs: Long = 1_200L) {
        isWindowFocused = false
        ignoreBlurBeforeEpochMillis = clock() + ignoreBlurWindowMs
        pendingSearchFieldFocus = true
    }

    fun onShowComplete() {
        isWindowFocused = false
        ignoreBlurBeforeEpochMillis = 0L
        pendingSearchFieldFocus = false
    }

    fun onWindowGainedFocus() {
        isWindowFocused = true
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

    fun shouldHideOnBlur(nowEpochMillis: Long): Boolean =
        nowEpochMillis >= ignoreBlurBeforeEpochMillis
}
