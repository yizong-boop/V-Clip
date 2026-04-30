package com.example.macclipboardmanager.macos

internal object MacPlatform {
    fun isMacOs(): Boolean =
        System.getProperty("os.name").contains("mac", ignoreCase = true)

    fun requireMacOs() {
        check(isMacOs()) {
            "macOS implementations can only run on macOS."
        }
    }
}
