package com.example.macclipboardmanager.domain.clipboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class JsonClipboardStore(
    private val file: File,
    private val json: Json = defaultJson,
) : ClipboardStore {
    override suspend fun save(items: List<ClipboardItem>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    ListSerializer(ClipboardItem.serializer()),
                    items,
                ),
            )
        }
    }

    override suspend fun load(): List<ClipboardItem> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                return@withContext emptyList()
            }

            val content = runCatching { file.readText() }.getOrElse {
                return@withContext emptyList()
            }

            try {
                json.decodeFromString(
                    ListSerializer(ClipboardItem.serializer()),
                    content,
                )
            } catch (_: SerializationException) {
                backupCorruptedFile()
                emptyList()
            } catch (_: IllegalArgumentException) {
                backupCorruptedFile()
                emptyList()
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
