package com.example.macclipboardmanager.ui

internal fun formatRelativeTime(
    copiedAtEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val deltaMillis = (nowEpochMillis - copiedAtEpochMillis).coerceAtLeast(0L)
    val minutes = deltaMillis / 60_000L
    if (minutes <= 0L) {
        return "now"
    }

    if (minutes < 60L) {
        return "${minutes}m"
    }

    val hours = minutes / 60L
    if (hours < 24L) {
        return "${hours}h"
    }

    val days = hours / 24L
    return "${days}d"
}
