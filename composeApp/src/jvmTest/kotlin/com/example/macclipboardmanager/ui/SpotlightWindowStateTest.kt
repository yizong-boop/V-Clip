package com.example.macclipboardmanager.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotlightWindowStateTest {
    @Test
    fun showMakesWindowVisible() {
        val state = SpotlightWindowState()

        state.show()

        assertTrue(state.isVisible)
    }

    @Test
    fun hideMakesWindowInvisible() {
        val state = SpotlightWindowState(isVisible = true)

        state.hide()

        assertFalse(state.isVisible)
    }
}
