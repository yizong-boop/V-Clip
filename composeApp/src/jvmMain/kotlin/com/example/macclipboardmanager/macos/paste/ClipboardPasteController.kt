package com.example.macclipboardmanager.macos.paste

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult

interface ClipboardPasteController {
    fun writePlainText(text: String): ClipboardWriteResult

    suspend fun pasteToFrontmostApp(): AutoPasteResult
}

class DefaultClipboardPasteController(
    private val clipboardMonitor: ClipboardMonitor,
    private val autoPasteService: AutoPasteService,
) : ClipboardPasteController {

    override fun writePlainText(text: String): ClipboardWriteResult =
        clipboardMonitor.writePlainText(text)

    override suspend fun pasteToFrontmostApp(): AutoPasteResult =
        autoPasteService.pasteToFrontmostApp()
}
