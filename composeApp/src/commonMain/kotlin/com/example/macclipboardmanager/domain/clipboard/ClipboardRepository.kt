package com.example.macclipboardmanager.domain.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ClipboardRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGeneratorOverride: (() -> String)? = null,
) {
    private val mutableItems = MutableStateFlow<List<ClipboardItem>>(emptyList())
    private var nextGeneratedId = 0L

    private val idGenerator: () -> String = {
        "clipboard-${nextGeneratedId++}"
    }

    val items: StateFlow<List<ClipboardItem>> = mutableItems.asStateFlow()

    fun add(text: String) {
        if (text.isBlank()) {
            return
        }

        val now = clock()
        mutableItems.update { currentItems ->
            if (currentItems.firstOrNull()?.text == text) {
                return@update currentItems
            }

            val existingItem = currentItems.firstOrNull { it.text == text }
            if (existingItem != null) {
                val refreshedItem = existingItem.copy(copiedAtEpochMillis = now)
                return@update buildList(currentItems.size) {
                    add(refreshedItem)
                    currentItems.forEach { item ->
                        if (item.id != existingItem.id) {
                            add(item)
                        }
                    }
                }
            }

            buildList(currentItems.size + 1) {
                add(
                    ClipboardItem(
                        id = nextId(),
                        text = text,
                        copiedAtEpochMillis = now,
                    ),
                )
                addAll(currentItems)
            }
        }
    }

    fun bumpSelectedItem(text: String) {
        mutableItems.update { currentItems ->
            val index = currentItems.indexOfFirst { it.text == text }
            if (index <= 0) return@update currentItems

            val item = currentItems[index]
            buildList(currentItems.size) {
                add(currentItems[0])
                add(item.copy(copiedAtEpochMillis = clock()))
                for (i in 1 until currentItems.size) {
                    if (i != index) {
                        add(currentItems[i])
                    }
                }
            }
        }
    }

    fun clear() {
        mutableItems.value = emptyList()
    }

    private fun nextId(): String = idGeneratorOverride?.invoke() ?: idGenerator()
}
