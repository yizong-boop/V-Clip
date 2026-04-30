package com.example.macclipboardmanager.domain.clipboard

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonClipboardStoreTest {
    @Test
    fun saveEmptyListThenLoadReturnsEmptyList() = runTest {
        val file = tempJsonFile()
        val store = JsonClipboardStore(file)

        store.save(emptyList())

        assertEquals(emptyList(), store.load())
    }

    @Test
    fun saveMultipleItemsThenLoadReturnsSameItems() = runTest {
        val file = tempJsonFile()
        val store = JsonClipboardStore(file)
        val items = listOf(
            ClipboardItem(
                id = "clipboard-0",
                text = "first",
                copiedAtEpochMillis = 1L,
            ),
            ClipboardItem(
                id = "clipboard-1",
                text = "second",
                copiedAtEpochMillis = 2L,
            ),
        )

        store.save(items)

        assertEquals(items, store.load())
    }

    @Test
    fun loadFromMissingFileReturnsEmptyList() = runTest {
        val file = tempJsonFile()
        file.delete()
        val store = JsonClipboardStore(file)

        assertEquals(emptyList(), store.load())
    }

    @Test
    fun loadFromCorruptedJsonReturnsEmptyListAndCreatesBackup() = runTest {
        val file = tempJsonFile()
        file.writeText("not json")
        val store = JsonClipboardStore(file)

        assertEquals(emptyList(), store.load())

        assertTrue(File(file.parentFile, "${file.name}.bak").exists())
    }

    @Test
    fun saveLoadSaveLoadRoundTripKeepsDataStable() = runTest {
        val file = tempJsonFile()
        val store = JsonClipboardStore(file)
        val items = listOf(
            ClipboardItem(
                id = "clipboard-2",
                text = "round trip",
                copiedAtEpochMillis = 10L,
            ),
        )

        store.save(items)
        val firstLoad = store.load()
        store.save(firstLoad)

        assertEquals(items, store.load())
    }

    private fun tempJsonFile(): File =
        File.createTempFile("v-clip-clipboard-", ".json").also { file ->
            file.deleteOnExit()
            File(file.parentFile, "${file.name}.bak").deleteOnExit()
        }
}
