package com.example.macclipboardmanager.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeController(
    private val store: ThemePreferencesStore,
    private val scope: CoroutineScope,
    initialTheme: AppThemePreference = AppThemePreference.Default,
) {
    private val mutableTheme = MutableStateFlow(initialTheme)
    private var userSelectionVersion = 0

    val theme: StateFlow<AppThemePreference> = mutableTheme.asStateFlow()

    suspend fun load() {
        val versionAtStart = userSelectionVersion
        val loadedTheme = store.loadTheme()
        if (versionAtStart == userSelectionVersion && userSelectionVersion == 0) {
            mutableTheme.value = loadedTheme
        }
    }

    fun setTheme(theme: AppThemePreference): Job {
        userSelectionVersion += 1
        mutableTheme.value = theme
        return scope.launch {
            runCatching {
                store.saveTheme(theme)
            }.onFailure { error ->
                System.err.println("Unable to save theme preference: ${error.message}")
            }
        }
    }
}
