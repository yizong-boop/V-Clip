package com.example.macclipboardmanager.macos.paste

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppleScriptAutoPasteService(
    private val timeoutMillis: Long = 2_000L,
) : AutoPasteService {

    override suspend fun pasteToFrontmostApp(): AutoPasteResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(
                    "osascript",
                    "-e",
                    """tell application "System Events" to keystroke "v" using command down""",
                )
                    .redirectErrorStream(true)
                    .start()

                if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    return@withContext AutoPasteResult.Failure(
                        message = "Auto-paste command timed out.",
                        permissionHint = accessibilityPermissionHint,
                    )
                }

                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                if (process.exitValue() == 0) {
                    AutoPasteResult.Success
                } else {
                    AutoPasteResult.Failure(
                        message = buildString {
                            append("Auto-paste command failed")
                            if (output.isNotBlank()) {
                                append(": ")
                                append(output)
                            }
                        },
                        permissionHint = if (output.requiresPermissionHint()) {
                            accessibilityPermissionHint
                        } else {
                            null
                        },
                    )
                }
            }.getOrElse { throwable ->
                AutoPasteResult.Failure(
                    message = "Unable to execute osascript for auto-paste: ${throwable.message}",
                    permissionHint = accessibilityPermissionHint,
                )
            }
        }

    private fun String.requiresPermissionHint(): Boolean {
        val normalized = lowercase(Locale.US)
        return normalized.contains("not authorized") ||
            normalized.contains("not allowed") ||
            normalized.contains("accessibility") ||
            normalized.contains("system events")
    }

    private companion object {
        private const val accessibilityPermissionHint =
            "Grant V-Clip access in System Settings -> Privacy & Security -> Accessibility."
    }
}
