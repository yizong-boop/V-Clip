package com.example.macclipboardmanager.domain.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardRepositoryTest {
    @Test
    fun addsNewTextToFront() {
        val repository = testRepository()

        repository.add("first")
        repository.add("second")

        assertEquals(listOf("second", "first"), repository.items.value.map(ClipboardItem::text))
    }

    @Test
    fun ignoresDuplicateWhenItMatchesLatestItem() {
        val repository = testRepository()

        repository.add("same")
        repository.add("same")

        assertEquals(1, repository.items.value.size)
        assertEquals("same", repository.items.value.single().text)
    }

    @Test
    fun movesExistingItemToFrontAndKeepsItsId() {
        val repository = testRepository()

        repository.add("first")
        repository.add("second")
        repository.add("first")

        val items = repository.items.value
        assertEquals(listOf("first", "second"), items.map(ClipboardItem::text))
        assertEquals("id-0", items.first().id)
        assertEquals(3L, items.first().copiedAtEpochMillis)
    }

    @Test
    fun ignoresBlankText() {
        val repository = testRepository()

        repository.add("")
        repository.add("   ")

        assertEquals(emptyList(), repository.items.value)
    }

    private fun testRepository(): ClipboardRepository {
        var currentTime = 0L
        var nextId = 0
        return ClipboardRepository(
            clock = { ++currentTime },
            idGeneratorOverride = { "id-${nextId++}" },
        )
    }
}
