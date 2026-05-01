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
    fun pinnedItemsStayAheadOfRegularItemsWhenExistingTextIsCopiedAgain() {
        val repository = testRepository()
        repository.add("regular")
        repository.add("pinned")
        repository.togglePinned("id-1")

        repository.add("regular")

        val items = repository.items.value
        assertEquals(listOf("pinned", "regular"), items.map(ClipboardItem::text))
        assertEquals(true, items.first().isPinned)
        assertEquals(3L, items[1].copiedAtEpochMillis)
    }

    @Test
    fun copiedPinnedItemMovesToFrontWithinPinnedGroup() {
        val repository = testRepository()
        repository.add("first pinned")
        repository.add("second pinned")
        repository.add("regular")
        repository.togglePinned("id-0")
        repository.togglePinned("id-1")

        repository.add("first pinned")

        assertEquals(
            listOf("first pinned", "second pinned", "regular"),
            repository.items.value.map(ClipboardItem::text),
        )
        assertEquals(
            listOf(true, true, false),
            repository.items.value.map(ClipboardItem::isPinned),
        )
    }

    @Test
    fun ignoresBlankText() {
        val repository = testRepository()

        repository.add("")
        repository.add("   ")

        assertEquals(emptyList(), repository.items.value)
    }

    @Test
    fun togglesFavoriteAndPinnedState() {
        val repository = testRepository()
        repository.add("first")

        repository.toggleFavorite("id-0")
        repository.togglePinned("id-0")

        val item = repository.items.value.single()
        assertEquals(true, item.isFavorite)
        assertEquals(true, item.isPinned)

        repository.toggleFavorite("id-0")
        repository.togglePinned("id-0")

        val updatedItem = repository.items.value.single()
        assertEquals(false, updatedItem.isFavorite)
        assertEquals(false, updatedItem.isPinned)
    }

    @Test
    fun trimProtectsFavoriteAndPinnedItems() {
        val repository = testRepository(maxHistorySize = 2)
        repository.add("favorite")
        repository.toggleFavorite("id-0")
        repository.add("pinned")
        repository.togglePinned("id-1")
        repository.add("regular")

        assertEquals(
            listOf("pinned", "favorite"),
            repository.items.value.map(ClipboardItem::text),
        )
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
        maxHistorySize: Int = 500,
    ): ClipboardRepository {
        var currentTime = 0L
        var nextId = 0
        return ClipboardRepository(
            clock = { ++currentTime },
            idGeneratorOverride = { "id-${nextId++}" },
            maxHistorySize = maxHistorySize,
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
