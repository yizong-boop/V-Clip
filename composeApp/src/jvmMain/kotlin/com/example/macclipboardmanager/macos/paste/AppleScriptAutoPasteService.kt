package com.example.macclipboardmanager.macos.paste

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppleScriptAutoPasteService(
    private val timeoutMillis: Long = 2_000L,
    private val failureCooldownMillis: Long = 10_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AutoPasteService {
    private val lock = Any()
    private var isPasteRunning = false
    private var lastPermissionOrTimeoutFailureAtMillis: Long? = null

    override suspend fun pasteToFrontmostApp(): AutoPasteResult =
        withContext(Dispatchers.IO) {
            beginPasteOrFailure()?.let { return@withContext it }

            runCatching {
                val process = ProcessBuilder(
                    "osascript",
                    "-e",
                    """tell application "System Events" to keystroke "v" using command down""",
                )
                    .redirectErrorStream(true)
                    .start()

                if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    terminate(process)
                    return@withContext AutoPasteResult.Failure(
                        message = "Auto-paste command timed out after ${timeoutMillis}ms.",
                        errorType = AutoPasteResult.ErrorType.TIMEOUT,
                        permissionHint = accessibilityPermissionHint,
                    ).also(::rememberPermissionOrTimeoutFailure)
                }

                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                if (process.exitValue() == 0) {
                    AutoPasteResult.Success
                } else {
                    val isPermissionError = output.requiresPermissionHint()
                    AutoPasteResult.Failure(
                        message = buildErrorMessage(output),
                        errorType = if (isPermissionError) {
                            AutoPasteResult.ErrorType.PERMISSION_DENIED
                        } else {
                            AutoPasteResult.ErrorType.EXECUTION_ERROR
                        },
                        permissionHint = if (isPermissionError) {
                            accessibilityPermissionHint
                        } else {
                            null
                        },
                    ).also { failure ->
                        if (failure.errorType == AutoPasteResult.ErrorType.PERMISSION_DENIED) {
                            rememberPermissionOrTimeoutFailure(failure)
                        }
                    }
                }
            }.getOrElse { throwable ->
                AutoPasteResult.Failure(
                    message = "Unable to execute osascript for auto-paste: ${throwable.message}",
                    errorType = AutoPasteResult.ErrorType.UNKNOWN,
                    permissionHint = accessibilityPermissionHint,
                )
            }.also {
                synchronized(lock) {
                    isPasteRunning = false
                }
            }
        }

    private fun beginPasteOrFailure(): AutoPasteResult.Failure? =
        synchronized(lock) {
            val now = clock()
            val lastFailureAt = lastPermissionOrTimeoutFailureAtMillis
            if (lastFailureAt != null && now - lastFailureAt < failureCooldownMillis) {
                return@synchronized AutoPasteResult.Failure(
                    message = "Auto-paste skipped after a recent macOS permission or timeout failure.",
                    errorType = AutoPasteResult.ErrorType.PERMISSION_DENIED,
                    permissionHint = accessibilityPermissionHint,
                )
            }

            if (isPasteRunning) {
                return@synchronized AutoPasteResult.Failure(
                    message = "Auto-paste command is already running.",
                    errorType = AutoPasteResult.ErrorType.EXECUTION_ERROR,
                )
            }

            isPasteRunning = true
            null
        }

    private fun rememberPermissionOrTimeoutFailure(failure: AutoPasteResult.Failure) {
        if (failure.errorType == AutoPasteResult.ErrorType.PERMISSION_DENIED ||
            failure.errorType == AutoPasteResult.ErrorType.TIMEOUT
        ) {
            synchronized(lock) {
                lastPermissionOrTimeoutFailureAtMillis = clock()
            }
        }
    }

    private fun terminate(process: Process) {
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
        runCatching { process.waitFor(500L, TimeUnit.MILLISECONDS) }
    }

    private fun buildErrorMessage(output: String): String = buildString {
        append("Auto-paste command failed")
        if (output.isNotBlank()) {
            append(": ")
            append(output)
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
