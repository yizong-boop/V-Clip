package com.example.macclipboardmanager.core.hotkey

import kotlinx.coroutines.flow.Flow

interface GlobalHotkeyManager {
    val activations: Flow<HotkeyActivation>

    fun register(hotkey: Hotkey)

    fun unregister()

    fun close()
}
