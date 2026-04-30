package com.example.macclipboardmanager.domain.clipboard

import kotlinx.serialization.Serializable

@Serializable
data class ClipboardItem(
    val id: String,
    val text: String,
    val copiedAtEpochMillis: Long,
)
