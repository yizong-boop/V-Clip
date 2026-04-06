package com.example.macclipboardmanager.smoke

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import com.example.macclipboardmanager.macos.clipboard.MacClipboardMonitor
import com.example.macclipboardmanager.macos.hotkey.MacGlobalHotkeyManager
import kotlinx.coroutines.launch

object MacSmokeDemo {
    private const val enabledValue = "1"
    private const val optionVKeyCode = 9

    fun isEnabled(): Boolean = System.getenv("MAC_CLIPBOARD_SMOKE") == enabledValue

    fun run() = application {
        val clipboardMonitor = remember { MacClipboardMonitor() }
        val hotkeyManager = remember { MacGlobalHotkeyManager() }

        LaunchedEffect(Unit) {
            println("Smoke demo started.")
            println("Copy plain text to test the clipboard monitor.")
            println("Press Option+V to test the global hotkey bridge.")

            clipboardMonitor.start()
            hotkeyManager.register(
                Hotkey(
                    keyCode = optionVKeyCode,
                    modifiers = setOf(HotkeyModifier.OPTION),
                ),
            )

            launch {
                clipboardMonitor.events.collect { event ->
                    println("Clipboard event: changeCount=${event.changeCount} text=${event.text}")
                }
            }

            launch {
                hotkeyManager.activations.collect { activation ->
                    println("Hotkey activation: ${activation.hotkey} at ${activation.activatedAtEpochMillis}")
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                hotkeyManager.close()
                clipboardMonitor.close()
            }
        }

        Window(
            visible = false,
            onCloseRequest = ::exitApplication,
            title = "V-Clip Smoke Demo",
        ) {
        }
    }
}
