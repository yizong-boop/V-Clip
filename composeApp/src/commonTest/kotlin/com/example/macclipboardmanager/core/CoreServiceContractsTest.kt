package com.example.macclipboardmanager.core

import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.clipboard.ClipboardTextEvent
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyActivation
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CoreServiceContractsTest {
    @Test
    fun clipboardMonitorLifecycleIsIdempotent() = runTest {
        val monitor = FakeClipboardMonitor()

        monitor.start()
        monitor.start()
        monitor.emit("first")
        monitor.stop()
        monitor.stop()
        monitor.emit("ignored")
        monitor.close()
        monitor.close()

        assertEquals(1, monitor.startCalls)
        assertEquals(1, monitor.stopCalls)
        assertEquals(listOf("first"), monitor.deliveredTexts)
    }

    @Test
    fun globalHotkeyLifecycleIsIdempotent() = runTest {
        val manager = FakeGlobalHotkeyManager()
        val hotkey = Hotkey(keyCode = 9, modifiers = setOf(HotkeyModifier.OPTION))

        manager.register(hotkey)
        manager.register(hotkey)
        manager.emit()
        manager.unregister()
        manager.unregister()
        manager.emit()
        manager.close()
        manager.close()

        assertEquals(1, manager.registerCalls)
        assertEquals(1, manager.unregisterCalls)
        assertEquals(listOf(hotkey), manager.deliveredHotkeys)
    }

    @Test
    fun fakeServicesCanBeConsumedByUpperLayerStoreLikeCode() = runTest {
        val monitor = FakeClipboardMonitor()
        val hotkeys = FakeGlobalHotkeyManager()
        val recorder = ServiceRecorder(this, monitor, hotkeys)

        monitor.start()
        hotkeys.register(Hotkey(keyCode = 9, modifiers = setOf(HotkeyModifier.OPTION)))
        advanceUntilIdle()
        monitor.emit("hello")
        hotkeys.emit()
        advanceUntilIdle()

        assertEquals(listOf("hello"), recorder.clipboardTexts)
        assertEquals(1, recorder.hotkeyActivations.size)

        recorder.cancel()
    }

    private class ServiceRecorder(
        scope: CoroutineScope,
        monitor: ClipboardMonitor,
        hotkeys: GlobalHotkeyManager,
    ) {
        val clipboardTexts = mutableListOf<String>()
        val hotkeyActivations = mutableListOf<HotkeyActivation>()
        private val jobs = mutableListOf<Job>()

        init {
            jobs += scope.launch {
                monitor.events.collect { clipboardTexts += it.text }
            }
            jobs += scope.launch {
                hotkeys.activations.collect { hotkeyActivations += it }
            }
        }

        fun cancel() {
            jobs.forEach(Job::cancel)
        }
    }

    private class FakeClipboardMonitor : ClipboardMonitor {
        private val mutableEvents = MutableSharedFlow<ClipboardTextEvent>(extraBufferCapacity = 8)
        private var started = false
        private var closed = false

        var startCalls = 0
            private set
        var stopCalls = 0
            private set
        val deliveredTexts = mutableListOf<String>()

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

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            started = false
        }

        fun emit(text: String) {
            if (!started || closed) {
                return
            }
            deliveredTexts += text
            mutableEvents.tryEmit(
                ClipboardTextEvent(
                    text = text,
                    capturedAtEpochMillis = 1L,
                    changeCount = deliveredTexts.size.toLong(),
                ),
            )
        }
    }

    private class FakeGlobalHotkeyManager : GlobalHotkeyManager {
        private val mutableActivations = MutableSharedFlow<HotkeyActivation>(extraBufferCapacity = 8)
        private var registeredHotkey: Hotkey? = null
        private var closed = false

        var registerCalls = 0
            private set
        var unregisterCalls = 0
            private set
        val deliveredHotkeys = mutableListOf<Hotkey>()

        override val activations: Flow<HotkeyActivation> = mutableActivations.asSharedFlow()

        override fun register(hotkey: Hotkey) {
            if (closed) {
                return
            }
            if (registeredHotkey == hotkey) {
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
        }

        fun emit() {
            val hotkey = registeredHotkey ?: return
            if (closed) {
                return
            }
            deliveredHotkeys += hotkey
            mutableActivations.tryEmit(
                HotkeyActivation(
                    hotkey = hotkey,
                    activatedAtEpochMillis = deliveredHotkeys.size.toLong(),
                ),
            )
        }
    }
}
