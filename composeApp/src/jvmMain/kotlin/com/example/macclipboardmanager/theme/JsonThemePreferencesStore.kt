package com.example.macclipboardmanager.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

class JsonThemePreferencesStore(
    private val file: File,
    private val json: Json = defaultJson,
) : ThemePreferencesStore {
    override suspend fun loadTheme(): AppThemePreference =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                return@withContext AppThemePreference.Default
            }

            val content = runCatching { file.readText() }.getOrElse {
                return@withContext AppThemePreference.Default
            }

            try {
                val preferences = json.decodeFromString(ThemePreferencesJson.serializer(), content)
                AppThemePreference.fromStorageValue(preferences.theme) ?: AppThemePreference.Default
            } catch (_: SerializationException) {
                backupCorruptedFile()
                AppThemePreference.Default
            } catch (_: IllegalArgumentException) {
                backupCorruptedFile()
                AppThemePreference.Default
            }
        }

    override suspend fun saveTheme(theme: AppThemePreference) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    ThemePreferencesJson.serializer(),
                    ThemePreferencesJson(theme = theme.storageValue),
                ),
            )
        }
    }

    private fun backupCorruptedFile() {
        runCatching {
            file.copyTo(
                target = File(file.parentFile, "${file.name}.bak"),
                overwrite = true,
            )
        }
    }

    companion object {
        private val defaultJson = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
private data class ThemePreferencesJson(
    val theme: String = AppThemePreference.Default.storageValue,
)
