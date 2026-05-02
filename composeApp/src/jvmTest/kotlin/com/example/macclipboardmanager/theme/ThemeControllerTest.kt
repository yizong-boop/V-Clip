package com.example.macclipboardmanager.theme

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeControllerTest {
    @Test
    fun loadInitializesThemeFromStore() = runTest {
        val store = FakeThemePreferencesStore(initialTheme = AppThemePreference.Solid)
        val controller = ThemeController(store = store, scope = TestScope(testScheduler))

        controller.load()

        assertEquals(AppThemePreference.Solid, controller.theme.value)
    }

    @Test
    fun setThemeUpdatesMemoryStateImmediatelyAndPersists() = runTest {
        val store = FakeThemePreferencesStore(initialTheme = AppThemePreference.FrostedGlass)
        val controller = ThemeController(store = store, scope = TestScope(testScheduler))

        val saveJob = controller.setTheme(AppThemePreference.Solid)

        assertEquals(AppThemePreference.Solid, controller.theme.value)
        saveJob.join()
        assertEquals(AppThemePreference.Solid, store.savedTheme)
    }

    @Test
    fun userSelectionDuringLoadIsNotOverwrittenByLoadedTheme() = runTest {
        val store = FakeThemePreferencesStore(initialTheme = AppThemePreference.Solid)
        val controller = ThemeController(store = store, scope = TestScope(testScheduler))

        controller.setTheme(AppThemePreference.FrostedGlass).join()
        controller.load()

        assertEquals(AppThemePreference.FrostedGlass, controller.theme.value)
    }

    private class FakeThemePreferencesStore(
        private val initialTheme: AppThemePreference,
    ) : ThemePreferencesStore {
        var savedTheme: AppThemePreference? = null

        override suspend fun loadTheme(): AppThemePreference = initialTheme

        override suspend fun saveTheme(theme: AppThemePreference) {
            savedTheme = theme
        }
    }
}
