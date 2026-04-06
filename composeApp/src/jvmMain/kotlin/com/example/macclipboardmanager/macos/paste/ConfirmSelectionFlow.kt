package com.example.macclipboardmanager.macos.paste

import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult
import kotlinx.coroutines.delay

internal suspend fun handleConfirmedSelection(
    text: String,
    clipboardPasteController: ClipboardPasteController,
    onHideWindow: () -> Unit,
    onClearSearchQuery: () -> Unit,
    onRestorePreviousAppFocus: () -> Unit = {},
    delayMillis: Long = 100L,
    delayFn: suspend (Long) -> Unit = ::delay,
    logger: (String) -> Unit = { message -> System.err.println(message) },
) {
    val writeResult = clipboardPasteController.writePlainText(text)
    onHideWindow()
    onClearSearchQuery()
    onRestorePreviousAppFocus()

    when (writeResult) {
        is ClipboardWriteResult.Success -> {
            delayFn(delayMillis)
            when (val pasteResult = clipboardPasteController.pasteToFrontmostApp()) {
                AutoPasteResult.Success -> Unit
                is AutoPasteResult.Failure -> {
                    logger("Auto-paste failed: ${pasteResult.message}")
                    pasteResult.permissionHint?.let(logger)
                }
            }
        }

        is ClipboardWriteResult.Failure -> {
            logger("Clipboard write failed: ${writeResult.message}")
        }
    }
}
