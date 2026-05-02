package com.example.macclipboardmanager.ui

internal object DragDebugLog {
    private const val enabled = true
    private const val prefix = "[DEBUG-drag]"

    fun log(message: String) {
        if (!enabled) {
            return
        }
        System.err.println("$prefix ${System.currentTimeMillis()} $message")
    }
}
