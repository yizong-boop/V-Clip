package com.example.macclipboardmanager.feature.main

sealed interface MainEffect {
    data class ShowWindow(
        val requestedAtEpochMillis: Long,
    ) : MainEffect

    data class ConfirmSelection(
        val text: String,
    ) : MainEffect
}
