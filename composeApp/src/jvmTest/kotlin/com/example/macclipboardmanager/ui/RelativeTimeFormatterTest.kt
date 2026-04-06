package com.example.macclipboardmanager.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeFormatterTest {
    @Test
    fun returnsNowForRecentTimestamps() {
        assertEquals("now", formatRelativeTime(copiedAtEpochMillis = 9_500L, nowEpochMillis = 10_000L))
    }

    @Test
    fun returnsMinutesForRecentMinutes() {
        assertEquals("2m", formatRelativeTime(copiedAtEpochMillis = 0L, nowEpochMillis = 120_000L))
    }

    @Test
    fun returnsHoursForRecentHours() {
        assertEquals("3h", formatRelativeTime(copiedAtEpochMillis = 0L, nowEpochMillis = 10_800_000L))
    }

    @Test
    fun returnsDaysForOlderTimestamps() {
        assertEquals("2d", formatRelativeTime(copiedAtEpochMillis = 0L, nowEpochMillis = 172_800_000L))
    }
}
