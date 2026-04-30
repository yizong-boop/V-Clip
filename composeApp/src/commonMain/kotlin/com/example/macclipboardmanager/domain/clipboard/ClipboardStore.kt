package com.example.macclipboardmanager.domain.clipboard

/**
 * Optional persistence interface for clipboard history.
 *
 * Implementations can save/load items across app restarts (e.g. to a local
 * JSON file, SQLite database, or platform-specific storage). The repository
 * calls [save] after each mutation when a store is attached.
 */
interface ClipboardStore {
    suspend fun save(items: List<ClipboardItem>)

    suspend fun load(): List<ClipboardItem>
}
