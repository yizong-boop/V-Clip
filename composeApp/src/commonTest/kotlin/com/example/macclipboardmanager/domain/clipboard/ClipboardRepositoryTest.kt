package com.example.macclipboardmanager.domain.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
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

    @Test
    fun flushSavesCurrentItemsToStore() = runTest {
        val store = FakeClipboardStore()
        val repository = testRepository(store = store, persistenceScope = this)

        repository.add("first")
        repository.add("second")
        repository.flush()

        assertEquals(listOf("second", "first"), store.lastSavedItems.map(ClipboardItem::text))
    }

    private fun testRepository(
        store: ClipboardStore? = null,
        persistenceScope: CoroutineScope? = null,
    ): ClipboardRepository {
        var currentTime = 0L
        var nextId = 0
        return ClipboardRepository(
            clock = { ++currentTime },
            idGeneratorOverride = { "id-${nextId++}" },
            store = store,
            persistenceScope = persistenceScope ?: CoroutineScope(
                SupervisorJob() + Dispatchers.Default,
            ),
        )
    }

    private class FakeClipboardStore : ClipboardStore {
        var lastSavedItems: List<ClipboardItem> = emptyList()
            private set

        override suspend fun save(items: List<ClipboardItem>) {
            lastSavedItems = items
        }

        override suspend fun load(): List<ClipboardItem> = emptyList()
    }
}
