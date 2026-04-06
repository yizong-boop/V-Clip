package com.example.macclipboardmanager.macos.paste

sealed interface AutoPasteResult {
    data object Success : AutoPasteResult

    data class Failure(
        val message: String,
        val permissionHint: String? = null,
    ) : AutoPasteResult
}
