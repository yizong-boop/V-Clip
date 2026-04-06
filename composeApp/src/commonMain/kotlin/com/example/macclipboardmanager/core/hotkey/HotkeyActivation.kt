package com.example.macclipboardmanager.core.hotkey

data class HotkeyActivation(
    val hotkey: Hotkey,
    val activatedAtEpochMillis: Long,
)
