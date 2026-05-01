package com.example.macclipboardmanager.ui

/**
 * Remembers the application that had focus before V-Clip was shown and restores
 * it when the overlay is intentionally dismissed.
 */
internal class PreviousApplicationFocusController(
    private val captureFocusedApplicationProcessId: () -> Int?,
    private val reactivateApplication: (Int) -> Boolean,
    private val onRestoreFailure: (Int) -> Unit = {},
) {
    private var previousProcessId: Int? = null

    fun capture() {
        previousProcessId = captureFocusedApplicationProcessId()
    }

    fun clear() {
        previousProcessId = null
    }

    fun restore() {
        val processId = previousProcessId ?: return
        previousProcessId = null

        if (!reactivateApplication(processId)) {
            onRestoreFailure(processId)
        }
    }
}
