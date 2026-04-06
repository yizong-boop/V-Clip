package com.example.macclipboardmanager.macos.paste

import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult
import kotlinx.coroutines.test.runTest
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
            listOf("write:hello", "hide", "clear", "restore-focus", "delay:100", "paste"),
            events,
        )
    }

    @Test
    fun failedClipboardWriteStillHidesWindowAndSkipsPaste() = runTest {
        val events = mutableListOf<String>()
        val logs = mutableListOf<String>()
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
            logger = logs::add,
        )

        assertEquals(
            listOf("write:hello", "hide", "clear", "restore-focus"),
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
        val controller = FakeClipboardPasteController(
            onWrite = { ClipboardWriteResult.Success(changeCount = 1L) },
            onPaste = {
                AutoPasteResult.Failure(
                    message = "not allowed",
                    permissionHint = "Grant permission",
                )
            },
        )

        handleConfirmedSelection(
            text = "hello",
            clipboardPasteController = controller,
            onHideWindow = {},
            onClearSearchQuery = {},
            delayFn = {},
            logger = logs::add,
        )

        assertEquals(
            listOf("Auto-paste failed: not allowed", "Grant permission"),
            logs,
        )
    }

    private class FakeClipboardPasteController(
        private val onWrite: (String) -> ClipboardWriteResult,
        private val onPaste: suspend () -> AutoPasteResult,
    ) : ClipboardPasteController {
        override fun writePlainText(text: String): ClipboardWriteResult = onWrite(text)

        override suspend fun pasteToFrontmostApp(): AutoPasteResult = onPaste()
    }
}
