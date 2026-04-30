package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.diagnostics.PrintStreamDiagnostics
import com.example.macclipboardmanager.macos.clipboard.MacClipboardMonitor
import com.example.macclipboardmanager.macos.hotkey.MacGlobalHotkeyManager
import com.example.macclipboardmanager.macos.paste.AppleScriptAutoPasteService
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import com.example.macclipboardmanager.macos.paste.DefaultClipboardPasteController

fun createClipboardMonitor(): ClipboardMonitor = MacClipboardMonitor(
    diagnostics = PrintStreamDiagnostics(),
)

fun createGlobalHotkeyManager(): GlobalHotkeyManager = MacGlobalHotkeyManager()

fun createClipboardPasteController(clipboardMonitor: ClipboardMonitor): ClipboardPasteController =
    DefaultClipboardPasteController(
        clipboardMonitor = clipboardMonitor,
        autoPasteService = AppleScriptAutoPasteService(),
    )
