package com.example.macclipboardmanager.macos.paste

import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult
import com.example.macclipboardmanager.core.diagnostics.AppDiagnostics
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmSelectionFlowTest {
    @Test
    fun confirmedSelectionWritesThenHidesThenPastes() = runTest {
        val events = mutableListOf<String>()
        val controller = FakeClipboardPasteController(
            onWrite = {
                events += "write:$it"
                ClipboardWriteResult.Success(changeCount = 1L)
            },
            onPaste = {
                events += "paste"
                AutoPasteResult.Success
            },
        )

        handleConfirmedSelection(
            text = "hello",
            clipboardPasteController = controller,
            onHideWindow = { events += "hide" },
            onClearSearchQuery = { events += "clear" },
            onRestorePreviousAppFocus = { events += "restore-focus" },
            delayFn = { events += "delay:$it" },
        )

        assertEquals(
            listOf("write:hello", "hide", "clear", "delay:25", "restore-focus", "delay:35", "paste"),
            events,
        )
    }

    @Test
    fun failedClipboardWriteStillHidesWindowAndSkipsPaste() = runTest {
        val events = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val diagnostics = FakeDiagnostics(logs)
        val controller = FakeClipboardPasteController(
            onWrite = {
                events += "write:$it"
                ClipboardWriteResult.Failure(message = "write failed")
            },
            onPaste = {
                events += "paste"
                AutoPasteResult.Success
            },
        )

        handleConfirmedSelection(
            text = "hello",
            clipboardPasteController = controller,
            onHideWindow = { events += "hide" },
            onClearSearchQuery = { events += "clear" },
            onRestorePreviousAppFocus = { events += "restore-focus" },
            delayFn = { events += "delay:$it" },
            diagnostics = diagnostics,
        )

        assertEquals(
            listOf("write:hello", "hide", "clear", "delay:25", "restore-focus"),
            events,
        )
        assertEquals(
            listOf("Clipboard write failed: write failed"),
            logs,
        )
    }

    @Test
    fun failedAutoPasteLogsPermissionHint() = runTest {
        val logs = mutableListOf<String>()
        val diagnostics = FakeDiagnostics(logs)
        var autoPasteStartingCalls = 0
        val controller = FakeClipboardPasteController(
            onWrite = { ClipboardWriteResult.Success(changeCount = 1L) },
            onPaste = {
                AutoPasteResult.Failure(
                    message = "not allowed",
                    errorType = AutoPasteResult.ErrorType.PERMISSION_DENIED,
                    permissionHint = "Grant permission",
                )
            },
        )

        handleConfirmedSelection(
            text = "hello",
            clipboardPasteController = controller,
            onHideWindow = {},
            onClearSearchQuery = {},
            onAutoPasteStarting = { autoPasteStartingCalls += 1 },
            onAutoPasteFailure = { logs += "callback:${it.message}" },
            delayFn = {},
            diagnostics = diagnostics,
        )

        assertEquals(1, autoPasteStartingCalls)
        assertEquals(
            listOf("callback:not allowed", "Auto-paste failed: not allowed", "Grant permission"),
            logs,
        )
    }

    @Test
    fun cancellationBeforeRestoreFocusSkipsRestoreAndPaste() = runTest {
        val events = mutableListOf<String>()
        val controller = FakeClipboardPasteController(
            onWrite = {
                events += "write:$it"
                ClipboardWriteResult.Success(changeCount = 1L)
            },
            onPaste = {
                events += "paste"
                AutoPasteResult.Success
            },
        )

        val job = launch {
            handleConfirmedSelection(
                text = "hello",
                clipboardPasteController = controller,
                onHideWindow = { events += "hide" },
                onClearSearchQuery = { events += "clear" },
                onRestorePreviousAppFocus = { events += "restore-focus" },
                delayFn = { delay(Long.MAX_VALUE) },
            )
        }

        advanceUntilIdle()
        job.cancelAndJoin()

        assertEquals(
            listOf("write:hello", "hide", "clear"),
            events,
        )
    }

    @Test
    fun cancellationBeforeAutoPasteSkipsPaste() = runTest {
        val events = mutableListOf<String>()
        val controller = FakeClipboardPasteController(
            onWrite = {
                events += "write:$it"
                ClipboardWriteResult.Success(changeCount = 1L)
            },
            onPaste = {
                events += "paste"
                AutoPasteResult.Success
            },
        )

        val job = launch {
            handleConfirmedSelection(
                text = "hello",
                clipboardPasteController = controller,
                onHideWindow = { events += "hide" },
                onClearSearchQuery = { events += "clear" },
                onRestorePreviousAppFocus = { events += "restore-focus" },
                restoreFocusDelayMillis = 0L,
                delayFn = { millis ->
                    events += "delay:$millis"
                    if (millis == 35L) {
                        delay(Long.MAX_VALUE)
                    }
                },
            )
        }

        advanceUntilIdle()
        job.cancelAndJoin()

        assertEquals(
            listOf("write:hello", "hide", "clear", "restore-focus", "delay:35"),
            events,
        )
    }

    private class FakeClipboardPasteController(
        private val onWrite: (String) -> ClipboardWriteResult,
        private val onPaste: suspend () -> AutoPasteResult,
    ) : ClipboardPasteController {
        override fun writePlainText(text: String): ClipboardWriteResult = onWrite(text)

        override suspend fun pasteToFrontmostApp(): AutoPasteResult = onPaste()
    }

    private class FakeDiagnostics(
        private val errors: MutableList<String>,
    ) : AppDiagnostics {
        override fun info(message: String) {
            errors += message
        }

        override fun warn(message: String) {
            errors += message
        }

        override fun error(message: String, throwable: Throwable?) {
            errors += message
        }
    }
}
