package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.macos.clipboard.MacClipboardMonitor
import com.example.macclipboardmanager.macos.hotkey.MacGlobalHotkeyManager

fun createClipboardMonitor(): ClipboardMonitor = MacClipboardMonitor()

fun createGlobalHotkeyManager(): GlobalHotkeyManager = MacGlobalHotkeyManager()
