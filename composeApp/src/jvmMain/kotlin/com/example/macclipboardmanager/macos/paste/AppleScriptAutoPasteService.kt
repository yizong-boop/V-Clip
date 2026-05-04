package com.example.macclipboardmanager.macos.paste

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class AppleScriptAutoPasteService(
    private val timeoutMillis: Long = 2_000L,
    private val failureCooldownMillis: Long = 10_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val processLauncher: () -> Process = {
        ProcessBuilder(
            "osascript",
            "-e",
            """tell application "System Events" to keystroke "v" using command down""",
        )
            .redirectErrorStream(true)
            .start()
    },
) : AutoPasteService {
    private val lock = Any()
    private var isPasteRunning = false
    private var lastPermissionOrTimeoutFailureAtMillis: Long? = null

    override suspend fun pasteToFrontmostApp(): AutoPasteResult =
        withContext(Dispatchers.IO) {
            System.err.println("[V-Clip] auto-paste service: entering pasteToFrontmostApp")
            beginPasteOrFailure()?.let { return@withContext it }

            try {
                try {
                    currentCoroutineContext().ensureActive()
                    System.err.println("[V-Clip] auto-paste service: launching osascript")
                    val process = processLauncher()
                    val cancellationWatcher = CoroutineScope(currentCoroutineContext()).launch(
                        start = CoroutineStart.UNDISPATCHED,
                    ) {
                        try {
                            awaitCancellation()
                        } finally {
                            if (currentCoroutineContext()[Job]?.isCancelled == true) {
                                System.err.println("[V-Clip] auto-paste service: cancellation received, terminating osascript")
                                terminate(process)
                            }
                        }
                    }

                    try {
                        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                            terminate(process)
                            System.err.println("[V-Clip] auto-paste service: osascript timed out after ${timeoutMillis}ms")
                            return@withContext AutoPasteResult.Failure(
                                message = "Auto-paste command timed out after ${timeoutMillis}ms.",
                                errorType = AutoPasteResult.ErrorType.TIMEOUT,
                            ).also(::rememberPermissionOrTimeoutFailure)
                        }

                        currentCoroutineContext().ensureActive()
                        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                        currentCoroutineContext().ensureActive()
                        if (process.exitValue() == 0) {
                            System.err.println("[V-Clip] auto-paste service: osascript exited successfully")
                            AutoPasteResult.Success
                        } else {
                            val isPermissionError = output.requiresPermissionHint()
                            System.err.println("[V-Clip] auto-paste service: osascript failed exit=${process.exitValue()} output=${output.ifBlank { "<empty>" }}")
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
                    } finally {
                        cancellationWatcher.cancelAndJoin()
                    }
                } catch (cancellation: CancellationException) {
                    System.err.println("[V-Clip] auto-paste service: pasteToFrontmostApp cancelled")
                    throw cancellation
                } catch (throwable: Throwable) {
                    AutoPasteResult.Failure(
                        message = "Unable to execute osascript for auto-paste: ${throwable.message}",
                        errorType = AutoPasteResult.ErrorType.UNKNOWN,
                    )
                }
            } finally {
                synchronized(lock) {
                    isPasteRunning = false
                }
                System.err.println("[V-Clip] auto-paste service: leaving pasteToFrontmostApp")
            }
        }

    private fun beginPasteOrFailure(): AutoPasteResult.Failure? =
        synchronized(lock) {
            val now = clock()
            val lastFailureAt = lastPermissionOrTimeoutFailureAtMillis
            if (lastFailureAt != null && now - lastFailureAt < failureCooldownMillis) {
                System.err.println("[V-Clip] auto-paste service: skipped due to cooldown")
                return@synchronized AutoPasteResult.Failure(
                    message = "Auto-paste skipped after a recent macOS permission or timeout failure.",
                    errorType = AutoPasteResult.ErrorType.EXECUTION_ERROR,
                )
            }

            if (isPasteRunning) {
                System.err.println("[V-Clip] auto-paste service: skipped because paste is already running")
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
