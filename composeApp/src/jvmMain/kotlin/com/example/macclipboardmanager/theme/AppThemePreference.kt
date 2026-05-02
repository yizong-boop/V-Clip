package com.example.macclipboardmanager.theme

enum class AppThemePreference(
    val storageValue: String,
    val displayName: String,
) {
    FrostedGlass(
        storageValue = "frosted_glass",
        displayName = "Frosted Glass",
    ),
    Solid(
        storageValue = "solid",
        displayName = "Solid",
    );

    companion object {
        val Default: AppThemePreference = FrostedGlass

        fun fromStorageValue(value: String): AppThemePreference? =
            entries.firstOrNull { it.storageValue == value }
    }
}
