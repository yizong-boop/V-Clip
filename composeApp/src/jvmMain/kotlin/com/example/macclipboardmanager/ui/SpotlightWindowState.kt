package com.example.macclipboardmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class SpotlightWindowState(
    isVisible: Boolean = false,
) {
    var isVisible: Boolean by mutableStateOf(isVisible)
        private set

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}
