package com.example.macclipboardmanager.core.clipboard

sealed interface ClipboardWriteResult {
    data class Success(
        val changeCount: Long,
    ) : ClipboardWriteResult

    data class Failure(
        val message: String,
    ) : ClipboardWriteResult
}
