package com.example.macclipboardmanager.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardListItemLayoutTest {
    @Test
    fun trailingActionAreaWidthIsFixedForAllActionStates() {
        val widths = listOf(
            ClipboardListItemLayout.trailingActionAreaWidthDp(
                isSelected = false,
                isFavorite = false,
                isPinned = false,
            ),
            ClipboardListItemLayout.trailingActionAreaWidthDp(
                isSelected = true,
                isFavorite = false,
                isPinned = false,
            ),
            ClipboardListItemLayout.trailingActionAreaWidthDp(
                isSelected = false,
                isFavorite = true,
                isPinned = false,
            ),
            ClipboardListItemLayout.trailingActionAreaWidthDp(
                isSelected = false,
                isFavorite = false,
                isPinned = true,
            ),
            ClipboardListItemLayout.trailingActionAreaWidthDp(
                isSelected = true,
                isFavorite = true,
                isPinned = true,
            ),
        )

        assertEquals(
            expected = listOf(
                ClipboardListItemLayout.TrailingActionAreaWidthDp,
                ClipboardListItemLayout.TrailingActionAreaWidthDp,
                ClipboardListItemLayout.TrailingActionAreaWidthDp,
                ClipboardListItemLayout.TrailingActionAreaWidthDp,
                ClipboardListItemLayout.TrailingActionAreaWidthDp,
            ),
            actual = widths,
        )
    }
}
