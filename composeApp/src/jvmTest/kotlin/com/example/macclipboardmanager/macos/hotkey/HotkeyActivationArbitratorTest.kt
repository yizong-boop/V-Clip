package com.example.macclipboardmanager.macos.hotkey

import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals

class HotkeyActivationArbitratorTest {
    private val hotkey = Hotkey(
        keyCode = 9,
        modifiers = setOf(HotkeyModifier.OPTION),
    )

    @Test
    fun `carbon activation is suppressed after recent event tap activation`() {
        val clock = TestNanoClock()
        val arbitrator = HotkeyActivationArbitrator(nowNano = clock::now)

        assertEquals(
            HotkeyActivationArbitrator.ArbitrationDecision.Emit,
            arbitrator.shouldEmit(
                hotkey = hotkey,
                source = HotkeyActivationArbitrator.HotkeyBackendSource.EVENT_TAP,
                isEventTapInstalled = true,
            ),
        )

        clock.advanceBy(52_000_000L)

        assertEquals(
            HotkeyActivationArbitrator.ArbitrationDecision.Ignore("paired-with-event-tap"),
            arbitrator.shouldEmit(
                hotkey = hotkey,
                source = HotkeyActivationArbitrator.HotkeyBackendSource.CARBON,
                isEventTapInstalled = true,
            ),
        )
    }

    @Test
    fun `second physical press can still emit from event tap during carbon suppression window`() {
        val clock = TestNanoClock()
        val arbitrator = HotkeyActivationArbitrator(nowNano = clock::now)

        assertEquals(
            HotkeyActivationArbitrator.ArbitrationDecision.Emit,
            arbitrator.shouldEmit(
                hotkey = hotkey,
                source = HotkeyActivationArbitrator.HotkeyBackendSource.EVENT_TAP,
                isEventTapInstalled = true,
            ),
        )

        clock.advanceBy(35_000_000L)

        assertEquals(
            HotkeyActivationArbitrator.ArbitrationDecision.Emit,
            arbitrator.shouldEmit(
                hotkey = hotkey,
                source = HotkeyActivationArbitrator.HotkeyBackendSource.EVENT_TAP,
                isEventTapInstalled = true,
            ),
        )
    }

    @Test
    fun `carbon still emits when event tap is unavailable`() {
        val clock = TestNanoClock()
        val arbitrator = HotkeyActivationArbitrator(nowNano = clock::now)

        assertEquals(
            HotkeyActivationArbitrator.ArbitrationDecision.Emit,
            arbitrator.shouldEmit(
                hotkey = hotkey,
                source = HotkeyActivationArbitrator.HotkeyBackendSource.CARBON,
                isEventTapInstalled = false,
            ),
        )
    }

    private class TestNanoClock {
        private var now: Long = 0L

        fun now(): Long = now

        fun advanceBy(deltaNanos: Long) {
            now += deltaNanos
        }
    }
}
