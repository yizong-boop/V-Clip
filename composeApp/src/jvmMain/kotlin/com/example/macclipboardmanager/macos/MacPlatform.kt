package com.example.macclipboardmanager.macos

internal object MacPlatform {
    fun requireMacOs() {
        check(System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            "macOS implementations can only run on macOS."
        }
    }
}
