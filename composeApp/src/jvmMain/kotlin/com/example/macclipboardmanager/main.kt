package com.example.macclipboardmanager

import androidx.compose.ui.window.application
import com.example.macclipboardmanager.smoke.MacSmokeDemo
import com.example.macclipboardmanager.ui.AppRoot

fun main() {
    if (MacSmokeDemo.isEnabled()) {
        MacSmokeDemo.run()
        return
    }

    application {
        AppRoot(onCloseRequest = ::exitApplication)
    }
}
