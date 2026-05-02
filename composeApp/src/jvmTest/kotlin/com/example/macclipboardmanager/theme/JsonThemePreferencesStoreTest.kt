package com.example.macclipboardmanager.theme

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonThemePreferencesStoreTest {
    @Test
    fun loadFromMissingFileReturnsDefaultTheme() = runTest {
        val file = tempJsonFile().also { it.delete() }
        val store = JsonThemePreferencesStore(file)

        assertEquals(AppThemePreference.FrostedGlass, store.loadTheme())
    }

    @Test
    fun loadSavedSolidTheme() = runTest {
        val file = tempJsonFile()
        file.writeText("""{"theme":"solid"}""")
        val store = JsonThemePreferencesStore(file)

        assertEquals(AppThemePreference.Solid, store.loadTheme())
    }

    @Test
    fun saveThenLoadReturnsSavedTheme() = runTest {
        val file = tempJsonFile()
        val store = JsonThemePreferencesStore(file)

        store.saveTheme(AppThemePreference.Solid)

        assertEquals(AppThemePreference.Solid, store.loadTheme())
    }

    @Test
    fun loadFromCorruptedJsonReturnsDefaultThemeAndCreatesBackup() = runTest {
        val file = tempJsonFile()
        file.writeText("not json")
        val store = JsonThemePreferencesStore(file)

        assertEquals(AppThemePreference.FrostedGlass, store.loadTheme())

        assertTrue(File(file.parentFile, "${file.name}.bak").exists())
    }

    @Test
    fun loadUnknownThemeValueReturnsDefaultTheme() = runTest {
        val file = tempJsonFile()
        file.writeText("""{"theme":"transparent_neon"}""")
        val store = JsonThemePreferencesStore(file)

        assertEquals(AppThemePreference.FrostedGlass, store.loadTheme())
    }

    private fun tempJsonFile(): File =
        File.createTempFile("v-clip-theme-", ".json").also { file ->
            file.deleteOnExit()
            File(file.parentFile, "${file.name}.bak").deleteOnExit()
        }
}
