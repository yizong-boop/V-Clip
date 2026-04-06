package com.example.macclipboardmanager.core.clipboard

import kotlinx.coroutines.flow.Flow

interface ClipboardMonitor {
    val events: Flow<ClipboardTextEvent>

    fun start()

    fun stop()

    fun writePlainText(text: String): ClipboardWriteResult

    fun close()
}
