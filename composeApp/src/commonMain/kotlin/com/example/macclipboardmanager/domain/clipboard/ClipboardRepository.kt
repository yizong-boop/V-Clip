package com.example.macclipboardmanager.domain.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClipboardRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGeneratorOverride: (() -> String)? = null,
    private val maxHistorySize: Int = 500,
    // TODO: Revisit long clipboard text handling. Truncating before dedupe can
    // collapse distinct long entries that share the same prefix.
    private val maxTextLength: Int = 10_000,
    private val store: ClipboardStore? = null,
    private val persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutableItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    private val saveMutex = Mutex()
    private var nextGeneratedId = 0L

    private val idGenerator: () -> String = {
        "clipboard-${nextGeneratedId++}"
    }

    val items: StateFlow<List<ClipboardItem>> = mutableItems.asStateFlow()

    fun add(text: String) {
        if (text.isBlank()) {
            return
        }

        val truncated = if (text.length > maxTextLength) {
            text.take(maxTextLength)
        } else {
            text
        }

        val now = clock()
        mutableItems.update { currentItems ->
            if (currentItems.firstOrNull()?.text == truncated) {
                return@update currentItems
            }

            val existingItem = currentItems.firstOrNull { it.text == truncated }
            val newList = if (existingItem != null) {
                val refreshedItem = existingItem.copy(copiedAtEpochMillis = now)
                buildList(currentItems.size) {
                    add(refreshedItem)
                    currentItems.forEach { item ->
                        if (item.id != existingItem.id) {
                            add(item)
                        }
                    }
                }
            } else {
                buildList(currentItems.size + 1) {
                    add(
                        ClipboardItem(
                            id = nextId(),
                            text = truncated,
                            copiedAtEpochMillis = now,
                        ),
                    )
                    addAll(currentItems)
                }
            }

            trimAndSort(newList)
        }
        persistIfNeeded(mutableItems.value)
    }

    fun bumpSelectedItem(text: String) {
        mutableItems.update { currentItems ->
            val index = currentItems.indexOfFirst { it.text == text }
            if (index <= 0) return@update currentItems

            val item = currentItems[index]
            val newList = buildList(currentItems.size) {
                add(item.copy(copiedAtEpochMillis = clock()))
                for (i in currentItems.indices) {
                    if (i != index) {
                        add(currentItems[i])
                    }
                }
            }

            trimAndSort(newList)
        }
        persistIfNeeded(mutableItems.value)
    }

    fun toggleFavorite(itemId: String) {
        updateItem(itemId) { item ->
            item.copy(isFavorite = !item.isFavorite)
        }
    }

    fun togglePinned(itemId: String) {
        updateItem(itemId) { item ->
            item.copy(isPinned = !item.isPinned)
        }
    }

    fun clear() {
        mutableItems.value = emptyList()
        persistIfNeeded(emptyList())
    }

    suspend fun flush() {
        val currentStore = store ?: return
        saveMutex.withLock {
            @Suppress("TooGenericExceptionCaught")
            try {
                currentStore.save(mutableItems.value)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun loadFromStore() {
        val stored = store?.load() ?: return
        if (stored.isEmpty()) return
        mutableItems.value = trimAndSort(stored)
        seedIdCounterFrom(stored)
    }

    private fun updateItem(
        itemId: String,
        transform: (ClipboardItem) -> ClipboardItem,
    ) {
        var changed = false
        mutableItems.update { currentItems ->
            val updatedItems = currentItems.map { item ->
                if (item.id == itemId) {
                    changed = true
                    transform(item)
                } else {
                    item
                }
            }

            if (changed) {
                trimAndSort(updatedItems)
            } else {
                currentItems
            }
        }

        if (changed) {
            persistIfNeeded(mutableItems.value)
        }
    }

    private fun seedIdCounterFrom(items: List<ClipboardItem>) {
        val maxId = items.mapNotNull { extractIdCounter(it.id) }.maxOrNull() ?: return
        if (maxId >= nextGeneratedId) {
            nextGeneratedId = maxId + 1
        }
    }

    private fun extractIdCounter(id: String): Long? {
        if (!id.startsWith("clipboard-")) return null
        return id.removePrefix("clipboard-").toLongOrNull()
    }

    private fun trimAndSort(items: List<ClipboardItem>): List<ClipboardItem> {
        val sortedItems = sortItems(items)
        if (sortedItems.size <= maxHistorySize) {
            return sortedItems
        }

        val protectedItems = sortedItems.filter { it.isFavorite || it.isPinned }
        val removableItems = sortedItems.filterNot { it.isFavorite || it.isPinned }
        val remainingCapacity = maxHistorySize - protectedItems.size
        if (remainingCapacity <= 0) {
            return protectedItems
        }

        return sortItems(protectedItems + removableItems.take(remainingCapacity))
    }

    private fun sortItems(items: List<ClipboardItem>): List<ClipboardItem> =
        items.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<ClipboardItem>> { it.value.isPinned }
                    .thenByDescending { it.value.copiedAtEpochMillis }
                    .thenBy { it.index },
            )
            .map { it.value }

    private fun persistIfNeeded(items: List<ClipboardItem>) {
        val currentStore = store ?: return
        persistenceScope.launch {
            saveMutex.withLock {
                @Suppress("TooGenericExceptionCaught")
                try {
                    currentStore.save(items)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun nextId(): String = idGeneratorOverride?.invoke() ?: idGenerator()
}
