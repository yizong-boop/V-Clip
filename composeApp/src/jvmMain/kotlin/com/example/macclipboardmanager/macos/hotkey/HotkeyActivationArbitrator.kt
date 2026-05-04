package com.example.macclipboardmanager.macos.hotkey

import com.example.macclipboardmanager.core.hotkey.Hotkey

internal class HotkeyActivationArbitrator(
    private val nowNano: () -> Long = System::nanoTime,
) {
    private var lastEventTapHotkey: Hotkey? = null
    private var lastEventTapAtNano: Long = Long.MIN_VALUE
    private var lastCarbonHotkey: Hotkey? = null
    private var lastCarbonAtNano: Long = Long.MIN_VALUE

    fun shouldEmit(
        hotkey: Hotkey,
        source: HotkeyBackendSource,
        isEventTapInstalled: Boolean,
    ): ArbitrationDecision {
        val now = nowNano()
        return when (source) {
            HotkeyBackendSource.EVENT_TAP -> {
                lastEventTapHotkey = hotkey
                lastEventTapAtNano = now
                ArbitrationDecision.Emit
            }

            HotkeyBackendSource.CARBON -> {
                if (
                    isEventTapInstalled &&
                    lastEventTapHotkey == hotkey &&
                    lastEventTapAtNano != Long.MIN_VALUE &&
                    now - lastEventTapAtNano <= CarbonSuppressionAfterEventTapNanos
                ) {
                    ArbitrationDecision.Ignore("paired-with-event-tap")
                } else if (
                    lastCarbonHotkey == hotkey &&
                    lastCarbonAtNano != Long.MIN_VALUE &&
                    now - lastCarbonAtNano <= CarbonSelfDedupWindowNanos
                ) {
                    ArbitrationDecision.Ignore("carbon-duplicate")
                } else {
                    lastCarbonHotkey = hotkey
                    lastCarbonAtNano = now
                    ArbitrationDecision.Emit
                }
            }
        }
    }

    sealed interface ArbitrationDecision {
        data object Emit : ArbitrationDecision

        data class Ignore(val reason: String) : ArbitrationDecision
    }

    enum class HotkeyBackendSource {
        EVENT_TAP,
        CARBON,
    }

    private companion object {
        private const val CarbonSelfDedupWindowNanos = 12_000_000L
        private const val CarbonSuppressionAfterEventTapNanos = 250_000_000L
    }
}
