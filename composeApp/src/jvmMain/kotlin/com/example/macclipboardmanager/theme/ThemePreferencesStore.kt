package com.example.macclipboardmanager.theme

interface ThemePreferencesStore {
    suspend fun loadTheme(): AppThemePreference

    suspend fun saveTheme(theme: AppThemePreference)
}
