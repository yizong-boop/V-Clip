package com.example.macclipboardmanager.macos.paste

interface AutoPasteService {
    suspend fun pasteToFrontmostApp(): AutoPasteResult
}
