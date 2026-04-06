package com.example.macclipboardmanager.domain.clipboard

data class ClipboardItem(
    val id: String,
    val text: String,
    val copiedAtEpochMillis: Long,
)
