package com.example.macclipboardmanager.core.hotkey

data class Hotkey(
    val keyCode: Int,
    val modifiers: Set<HotkeyModifier>,
)

enum class HotkeyModifier {
    COMMAND,
    OPTION,
    CONTROL,
    SHIFT,
}
