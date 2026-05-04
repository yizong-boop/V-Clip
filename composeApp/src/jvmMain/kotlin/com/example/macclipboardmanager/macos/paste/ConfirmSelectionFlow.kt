package com.example.macclipboardmanager.macos.paste

import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult
import com.example.macclipboardmanager.core.diagnostics.AppDiagnostics
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal suspend fun handleConfirmedSelection(
    text: String,
    clipboardPasteController: ClipboardPasteController,
    onHideWindow: () -> Unit,
    onClearSearchQuery: () -> Unit,
    onRestorePreviousAppFocus: () -> Unit = {},
    onAutoPasteStarting: () -> Unit = {},
    onAutoPasteFailure: (AutoPasteResult.Failure) -> Unit = {},
    restoreFocusDelayMillis: Long = 25L,
    delayMillis: Long = 35L,
    delayFn: suspend (Long) -> Unit = ::delay,
    diagnostics: AppDiagnostics? = null,
) {
    fun log(message: String) {
        System.err.println("[V-Clip] $message")
    }

    suspend fun ensureActiveOrThrow() {
        currentCoroutineContext().ensureActive()
    }

    log("confirm flow: writePlainText start (text len=${text.length})")
    val writeResult = clipboardPasteController.writePlainText(text)
    log("confirm flow: writePlainText result=${writeResult::class.simpleName}")

    ensureActiveOrThrow()
    log("confirm flow: hide window")
    onHideWindow()
    ensureActiveOrThrow()
    log("confirm flow: clear search query")
    onClearSearchQuery()
    if (restoreFocusDelayMillis > 0L) {
        log("confirm flow: waiting ${restoreFocusDelayMillis}ms before restoring previous app focus")
        delayFn(restoreFocusDelayMillis)
    }
    ensureActiveOrThrow()
    log("confirm flow: restore previous app focus")
    onRestorePreviousAppFocus()

    when (writeResult) {
        is ClipboardWriteResult.Success -> {
            log("confirm flow: waiting ${delayMillis}ms before auto-paste")
            delayFn(delayMillis)
            ensureActiveOrThrow()
            log("confirm flow: auto-paste start")
            onAutoPasteStarting()
            when (val pasteResult = clipboardPasteController.pasteToFrontmostApp()) {
                AutoPasteResult.Success -> log("confirm flow: auto-paste success")
                is AutoPasteResult.Failure -> {
                    log("confirm flow: auto-paste failure (${pasteResult.errorType}) ${pasteResult.message}")
                    onAutoPasteFailure(pasteResult)
                    diagnostics?.error("Auto-paste failed: ${pasteResult.message}")
                    pasteResult.permissionHint?.let { diagnostics?.warn(it) }
                }
            }
        }

        is ClipboardWriteResult.Failure -> {
            log("confirm flow: clipboard write failed ${writeResult.message}")
            diagnostics?.error("Clipboard write failed: ${writeResult.message}")
        }
    }
}
