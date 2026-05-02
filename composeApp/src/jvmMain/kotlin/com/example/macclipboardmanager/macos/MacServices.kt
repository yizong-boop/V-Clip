package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.diagnostics.PrintStreamDiagnostics
import com.example.macclipboardmanager.domain.clipboard.ClipboardStore
import com.example.macclipboardmanager.domain.clipboard.JsonClipboardStore
import com.example.macclipboardmanager.macos.clipboard.MacClipboardMonitor
import com.example.macclipboardmanager.macos.hotkey.MacGlobalHotkeyManager
import com.example.macclipboardmanager.macos.paste.AppleScriptAutoPasteService
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import com.example.macclipboardmanager.macos.paste.DefaultClipboardPasteController
import com.example.macclipboardmanager.theme.JsonThemePreferencesStore
import com.example.macclipboardmanager.theme.ThemePreferencesStore
import java.io.File

fun createClipboardMonitor(): ClipboardMonitor = MacClipboardMonitor(
    diagnostics = PrintStreamDiagnostics(),
)

fun createGlobalHotkeyManager(): GlobalHotkeyManager = MacGlobalHotkeyManager()

fun createClipboardStore(): ClipboardStore =
    JsonClipboardStore(
        file = File(vClipApplicationSupportDirectory(), "clipboard.json"),
    )

fun createThemePreferencesStore(): ThemePreferencesStore =
    JsonThemePreferencesStore(
        file = File(vClipApplicationSupportDirectory(), "preferences.json"),
    )

fun createClipboardPasteController(clipboardMonitor: ClipboardMonitor): ClipboardPasteController =
    DefaultClipboardPasteController(
        clipboardMonitor = clipboardMonitor,
        autoPasteService = AppleScriptAutoPasteService(),
    )

private fun vClipApplicationSupportDirectory(): File =
    File(System.getProperty("user.home"), "Library/Application Support/V-Clip")
