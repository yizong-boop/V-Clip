package com.example.macclipboardmanager.core.clipboard

data class ClipboardTextEvent(
    val text: String,
    val capturedAtEpochMillis: Long,
    val changeCount: Long,
)
