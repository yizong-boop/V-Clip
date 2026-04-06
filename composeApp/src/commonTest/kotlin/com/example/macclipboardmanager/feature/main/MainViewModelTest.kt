package com.example.macclipboardmanager.feature.main

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.clipboard.ClipboardTextEvent
import com.example.macclipboardmanager.core.clipboard.ClipboardWriteResult
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyActivation
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import com.example.macclipboardmanager.domain.clipboard.ClipboardRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test
    fun startIsIdempotentAndRegistersDefaultHotkey() = runTest {
        val monitor = FakeClipboardMonitor()
        val hotkeys = FakeGlobalHotkeyManager()
        val viewModel = createViewModel(monitor = monitor, hotkeys = hotkeys, scope = this)

        viewModel.start()
        viewModel.start()
        advanceUntilIdle()

        assertEquals(1, monitor.startCalls)
        assertEquals(1, hotkeys.registerCalls)
        assertEquals(MainViewModel.DEFAULT_HOTKEY, hotkeys.lastRegisteredHotkey)
        viewModel.close()
    }

    @Test
    fun monitorEventsAreStoredAndExposedInUiState() = runTest {
        val monitor = FakeClipboardMonitor()
        val hotkeys = FakeGlobalHotkeyManager()
        val viewModel = createViewModel(monitor = monitor, hotkeys = hotkeys, scope = this)

        viewModel.start()
        advanceUntilIdle()
        monitor.emit("hello")
        advanceUntilIdle()

        assertEquals(listOf("hello"), viewModel.uiState.value.filteredItems.map { it.text })
        assertEquals("hello", viewModel.uiState.value.selectedItem?.text)
        viewModel.close()
    }

    @Test
    fun searchUsesCaseInsensitiveContainsAndAutoSelectsFirstMatch() = runTest {
        val repository = testRepository()
        repository.add("Alpha")
        repository.add("beta")
        repository.add("alphabet soup")
        val viewModel = createViewModel(repository = repository, scope = this)

        advanceUntilIdle()
        viewModel.updateSearchQuery("alp")
        advanceUntilIdle()

        assertEquals(
            listOf("alphabet soup", "Alpha"),
            viewModel.uiState.value.filteredItems.map { it.text },
        )
        assertEquals("alphabet soup", viewModel.uiState.value.selectedItem?.text)
        viewModel.close()
    }

    @Test
    fun noSearchResultsClearSelection() = runTest {
        val repository = testRepository()
        repository.add("hello")
        val viewModel = createViewModel(repository = repository, scope = this)

        advanceUntilIdle()
        viewModel.updateSearchQuery("missing")
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.uiState.value.filteredItems)
        assertNull(viewModel.uiState.value.selectedItemId)
        viewModel.close()
    }

    @Test
    fun selectNextAndPreviousClampToListBounds() = runTest {
        val repository = testRepository()
        repository.add("one")
        repository.add("two")
        repository.add("three")
        val viewModel = createViewModel(repository = repository, scope = this)

        advanceUntilIdle()
        assertEquals("three", viewModel.uiState.value.selectedItem?.text)

        viewModel.selectNext()
        assertEquals("two", viewModel.uiState.value.selectedItem?.text)

        viewModel.selectNext()
        assertEquals("one", viewModel.uiState.value.selectedItem?.text)

        viewModel.selectNext()
        assertEquals("one", viewModel.uiState.value.selectedItem?.text)

        viewModel.selectPrevious()
        assertEquals("two", viewModel.uiState.value.selectedItem?.text)

        viewModel.selectPrevious()
        viewModel.selectPrevious()
        assertEquals("three", viewModel.uiState.value.selectedItem?.text)
        viewModel.close()
    }

    @Test
    fun hotkeyActivationsEmitShowWindowEffect() = runTest {
        val hotkeys = FakeGlobalHotkeyManager()
        val viewModel = createViewModel(hotkeys = hotkeys, scope = this)
        val effect = async { viewModel.effects.first() }

        advanceUntilIdle()
        viewModel.start()
        advanceUntilIdle()
        hotkeys.emit(MainViewModel.DEFAULT_HOTKEY)
        advanceUntilIdle()

        assertEquals(MainEffect.ShowWindow(requestedAtEpochMillis = 1L), effect.await())
        viewModel.close()
    }

    @Test
    fun confirmSelectionEmitsSelectedTextEffect() = runTest {
        val repository = testRepository()
        repository.add("hello")
        val viewModel = createViewModel(repository = repository, scope = this)
        val effect = async {
            viewModel.effects.first { it is MainEffect.ConfirmSelection }
        }

        advanceUntilIdle()
        viewModel.confirmSelection()

        assertEquals(
            MainEffect.ConfirmSelection(text = "hello"),
            effect.await(),
        )
        viewModel.close()
    }

    @Test
    fun confirmSelectionDoesNothingWhenNothingIsSelected() = runTest {
        val viewModel = createViewModel(scope = this)
        var emitted = false
        val collector = backgroundScope.async {
            viewModel.effects.collect {
                emitted = true
            }
        }

        advanceUntilIdle()
        viewModel.confirmSelection()
        advanceUntilIdle()

        assertFalse(emitted)
        collector.cancel()
        viewModel.close()
    }

    @Test
    fun clearReleasesMonitorAndHotkeys() = runTest {
        val monitor = FakeClipboardMonitor()
        val hotkeys = FakeGlobalHotkeyManager()
        val viewModel = createViewModel(monitor = monitor, hotkeys = hotkeys, scope = this)

        viewModel.start()
        advanceUntilIdle()
        viewModel.close()

        assertEquals(1, monitor.stopCalls)
        assertEquals(1, monitor.closeCalls)
        assertEquals(1, hotkeys.unregisterCalls)
        assertEquals(1, hotkeys.closeCalls)
    }

    private fun createViewModel(
        repository: ClipboardRepository = testRepository(),
        monitor: FakeClipboardMonitor = FakeClipboardMonitor(),
        hotkeys: FakeGlobalHotkeyManager = FakeGlobalHotkeyManager(),
        scope: TestScope,
    ): MainViewModel =
        run {
            var effectClock = 0L
            MainViewModel(
                repository = repository,
                clipboardMonitor = monitor,
                globalHotkeyManager = hotkeys,
                clock = { ++effectClock },
                coroutineScope = scope,
            )
        }

    private fun testRepository(): ClipboardRepository {
        var currentTime = 0L
        var nextId = 0
        return ClipboardRepository(
            clock = { ++currentTime },
            idGeneratorOverride = { "item-${nextId++}" },
        )
    }

    private class FakeClipboardMonitor : ClipboardMonitor {
        private val mutableEvents = MutableSharedFlow<ClipboardTextEvent>(extraBufferCapacity = 8)
        private var started = false
        private var closed = false

        var startCalls = 0
            private set
        var stopCalls = 0
            private set
        var closeCalls = 0
            private set

        override val events: Flow<ClipboardTextEvent> = mutableEvents.asSharedFlow()

        override fun start() {
            if (closed || started) {
                return
            }
            started = true
            startCalls++
        }

        override fun stop() {
            if (!started) {
                return
            }
            started = false
            stopCalls++
        }

        override fun writePlainText(text: String): ClipboardWriteResult {
            assertTrue(text.isNotEmpty())
            return ClipboardWriteResult.Success(changeCount = 1L)
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            started = false
            closeCalls++
        }

        fun emit(text: String) {
            if (!started || closed) {
                return
            }
            mutableEvents.tryEmit(
                ClipboardTextEvent(
                    text = text,
                    capturedAtEpochMillis = 1L,
                    changeCount = 1L,
                ),
            )
        }
    }

    private class FakeGlobalHotkeyManager : GlobalHotkeyManager {
        private val mutableActivations = MutableSharedFlow<HotkeyActivation>(
            replay = 1,
            extraBufferCapacity = 8,
        )
        private var registeredHotkey: Hotkey? = null
        private var closed = false

        var registerCalls = 0
            private set
        var unregisterCalls = 0
            private set
        var closeCalls = 0
            private set
        val lastRegisteredHotkey: Hotkey?
            get() = registeredHotkey

        override val activations: Flow<HotkeyActivation> = mutableActivations.asSharedFlow()

        override fun register(hotkey: Hotkey) {
            if (closed || registeredHotkey == hotkey) {
                return
            }
            registeredHotkey = hotkey
            registerCalls++
        }

        override fun unregister() {
            if (registeredHotkey == null) {
                return
            }
            registeredHotkey = null
            unregisterCalls++
        }

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            registeredHotkey = null
            closeCalls++
        }

        fun emit(hotkey: Hotkey = Hotkey(keyCode = 9, modifiers = setOf(HotkeyModifier.OPTION))) {
            if (closed || registeredHotkey == null) {
                return
            }
            mutableActivations.tryEmit(
                HotkeyActivation(
                    hotkey = hotkey,
                    activatedAtEpochMillis = 1L,
                ),
            )
        }
    }
}
