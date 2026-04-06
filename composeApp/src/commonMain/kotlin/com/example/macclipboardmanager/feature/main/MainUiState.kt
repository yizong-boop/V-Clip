package com.example.macclipboardmanager.feature.main

import com.example.macclipboardmanager.domain.clipboard.ClipboardItem

data class MainUiState(
    val searchQuery: String = "",
    val filteredItems: List<ClipboardItem> = emptyList(),
    val selectedItemId: String? = null,
) {
    val selectedItem: ClipboardItem?
        get() = filteredItems.firstOrNull { it.id == selectedItemId }
}
