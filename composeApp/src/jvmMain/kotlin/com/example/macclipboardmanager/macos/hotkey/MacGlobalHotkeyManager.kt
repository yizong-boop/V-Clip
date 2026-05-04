package com.example.macclipboardmanager.macos.hotkey

import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyActivation
import com.example.macclipboardmanager.macos.MacPlatform
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MacGlobalHotkeyManager(
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : GlobalHotkeyManager {

    private val mutableActivations = MutableSharedFlow<HotkeyActivation>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    private val lock = Any()
    private val activationArbitrator = HotkeyActivationArbitrator()
    private val eventHandlerCallback = CarbonBridge.EventHandlerCallback { _, eventRef, _ ->
        handleCarbonEvent(eventRef)
    }
    private val eventTapCallback = EventTapBridge.EventTapCallback { _, type, eventRef, _ ->
        handleEventTapEvent(type, eventRef)
    }

    private var eventHandlerRef: Pointer? = null
    private var registeredHotkeyRef: Pointer? = null
    private var eventTapRegistration: EventTapBridge.EventTapRegistration? = null
    private var registeredHotkey: Hotkey? = null
    private var registeredHotkeyId: Int? = null
    private var nextRegistrationId = 1
    private var closed = false
    private var allowCommandOverlayForNextActivation = false

    override val activations: Flow<HotkeyActivation> = mutableActivations.asSharedFlow()

    init {
        MacPlatform.requireMacOs()
    }

    override fun register(hotkey: Hotkey) {
        synchronized(lock) {
            check(!closed) { "Global hotkey manager is already closed." }
            ensureEventHandlerInstalled()
            ensureEventTapInstalled()
            unregisterHotkeyLocked()

            val registrationId = nextRegistrationId++
            registeredHotkeyRef = CarbonBridge.registerHotkey(hotkey, registrationId)
            registeredHotkey = hotkey
            registeredHotkeyId = registrationId
            System.err.println("[V-Clip] Carbon hotkey registered (keyCode=${hotkey.keyCode}, registrationId=$registrationId)")
        }
    }

    override fun unregister() {
        synchronized(lock) {
            System.err.println("[V-Clip] Carbon hotkey unregister requested")
            unregisterHotkeyLocked()
        }
    }

    override fun setAllowCommandOverlayForNextActivation(enabled: Boolean) {
        synchronized(lock) {
            allowCommandOverlayForNextActivation = enabled
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true

            unregisterHotkeyLocked()
            removeEventHandlerLocked()
            removeEventTapLocked()
        }
    }

    private fun handleCarbonEvent(eventRef: Pointer?): Int {
        if (eventRef == null || Pointer.nativeValue(eventRef) == 0L) {
            return CarbonBridge.noErr()
        }

        return runCatching {
            val hotkeyId = CarbonBridge.readHotkeyId(eventRef)
            val hotkey = synchronized(lock) {
                if (hotkeyId.id != registeredHotkeyId) {
                    null
                } else {
                    registeredHotkey
                }
            } ?: return CarbonBridge.noErr()

            System.err.println("[V-Clip] Carbon hotkey event received (keyCode=${hotkey.keyCode})")
            emitActivation(hotkey, source = HotkeyActivationArbitrator.HotkeyBackendSource.CARBON)
            CarbonBridge.noErr()
        }.getOrElse { throwable ->
            System.err.println("Global hotkey callback failed: ${throwable.message}")
            CarbonBridge.noErr()
        }
    }

    private fun handleEventTapEvent(type: Int, eventRef: Pointer?): Pointer? {
        if (type == EventTapBridge.kCGEventTapDisabledByTimeout ||
            type == EventTapBridge.kCGEventTapDisabledByUserInput
        ) {
            val reason = if (type == EventTapBridge.kCGEventTapDisabledByTimeout) {
                "timeout"
            } else {
                "user-input"
            }
            System.err.println("[V-Clip] Event tap disabled ($reason), re-enabling")
            synchronized(lock) {
                eventTapRegistration?.tap
            }?.let(EventTapBridge::enable)
            return eventRef
        }

        if (eventRef == null || Pointer.nativeValue(eventRef) == 0L) {
            return eventRef
        }

        return runCatching {
            val hotkey = synchronized(lock) { registeredHotkey } ?: return eventRef
            val keyCode = EventTapBridge.readKeyCode(eventRef)
            val flags = EventTapBridge.readFlags(eventRef)
            traceRawKeyEvent(
                type = type,
                keyCode = keyCode,
                flags = flags,
                hotkey = hotkey,
            )

            if (type != EventTapBridge.kCGEventKeyDown) {
                return eventRef
            }

            if (keyCode != hotkey.keyCode) {
                return eventRef
            }

            val allowCommandOverlay = synchronized(lock) { allowCommandOverlayForNextActivation }
            val matchesModifiers = flags.matchesHotkeyModifiers(
                modifiers = hotkey.modifiers,
                allowCommandOverlay = allowCommandOverlay,
            )
            if (!matchesModifiers) {
                return eventRef
            }

            System.err.println("[V-Clip] Event tap hotkey event received (keyCode=$keyCode)")
            if (allowCommandOverlay && (flags and CommandFlagMask != 0L)) {
                System.err.println("[V-Clip] Event tap hotkey matched with command-overlay allowance")
            }
            emitActivation(hotkey, source = HotkeyActivationArbitrator.HotkeyBackendSource.EVENT_TAP)
            eventRef
        }.getOrElse { throwable ->
            System.err.println("Event tap callback failed: ${throwable.message}")
            eventRef
        }
    }

    private fun traceRawKeyEvent(
        type: Int,
        keyCode: Int,
        flags: Long,
        hotkey: Hotkey,
    ) {
        if (!shouldTraceRawKeyEvent(type = type, keyCode = keyCode, flags = flags, hotkey = hotkey)) {
            return
        }

        val typeName = when (type) {
            EventTapBridge.kCGEventKeyDown -> "keyDown"
            EventTapBridge.kCGEventFlagsChanged -> "flagsChanged"
            else -> "type=$type"
        }
        val relevantFlags = flags and RelevantModifierFlagsMask
        val allowCommandOverlay = synchronized(lock) { allowCommandOverlayForNextActivation }
        val matchesHotkey = type == EventTapBridge.kCGEventKeyDown &&
            keyCode == hotkey.keyCode &&
            flags.matchesHotkeyModifiers(
                modifiers = hotkey.modifiers,
                allowCommandOverlay = allowCommandOverlay,
            )

        System.err.println(
            "[V-Clip] raw key event " +
                "(type=$typeName keyCode=$keyCode flags=0x${relevantFlags.toString(16)} " +
                "modifiers=${relevantFlags.describeModifierFlags()} matchesHotkey=$matchesHotkey " +
                "commandOverlayAllowed=$allowCommandOverlay)",
        )
    }

    private fun shouldTraceRawKeyEvent(
        type: Int,
        keyCode: Int,
        flags: Long,
        hotkey: Hotkey,
    ): Boolean {
        if (type != EventTapBridge.kCGEventKeyDown && type != EventTapBridge.kCGEventFlagsChanged) {
            return false
        }

        val relevantFlags = flags and RelevantModifierFlagsMask
        return keyCode == hotkey.keyCode ||
            keyCode == LeftOptionKeyCode ||
            keyCode == RightOptionKeyCode ||
            relevantFlags != 0L
    }

    private fun ensureEventHandlerInstalled() {
        if (eventHandlerRef != null) {
            return
        }

        eventHandlerRef = CarbonBridge.installHotKeyPressedHandler(eventHandlerCallback)
    }

    private fun ensureEventTapInstalled() {
        if (eventTapRegistration != null) {
            return
        }

        eventTapRegistration = runCatching {
            EventTapBridge.installKeyDownTap(eventTapCallback)
        }.onFailure { error ->
            System.err.println("[V-Clip] Event tap install failed: ${error.message}")
        }.getOrNull()
    }

    private fun emitActivation(hotkey: Hotkey, source: HotkeyActivationArbitrator.HotkeyBackendSource) {
        val result = synchronized(lock) {
            val decision = activationArbitrator.shouldEmit(
                hotkey = hotkey,
                source = source,
                isEventTapInstalled = eventTapRegistration != null,
            )
            when (decision) {
                HotkeyActivationArbitrator.ArbitrationDecision.Emit -> {
                    val emitted = mutableActivations.tryEmit(
                        HotkeyActivation(
                            hotkey = hotkey,
                            activatedAtEpochMillis = nowEpochMillis(),
                        ),
                    )
                    if (emitted) {
                        null
                    } else {
                        "buffer-full"
                    }
                }

                is HotkeyActivationArbitrator.ArbitrationDecision.Ignore -> decision.reason
            }
        }

        if (result != null) {
            System.err.println("[V-Clip] Hotkey activation ignored (${source.name.lowercase()} $result).")
        }
    }

    private fun unregisterHotkeyLocked() {
        val currentHotkeyRef = registeredHotkeyRef ?: run {
            registeredHotkey = null
            registeredHotkeyId = null
            return
        }

        CarbonBridge.unregisterHotkey(currentHotkeyRef)
        System.err.println("[V-Clip] Carbon hotkey unregistered (registrationId=$registeredHotkeyId)")
        registeredHotkeyRef = null
        registeredHotkey = null
        registeredHotkeyId = null
    }

    private fun removeEventHandlerLocked() {
        val currentEventHandlerRef = eventHandlerRef ?: return
        CarbonBridge.removeEventHandler(currentEventHandlerRef)
        eventHandlerRef = null
    }

    private fun removeEventTapLocked() {
        val currentRegistration = eventTapRegistration ?: return
        EventTapBridge.uninstall(currentRegistration)
        eventTapRegistration = null
    }

    private fun Long.matchesHotkeyModifiers(
        modifiers: Set<com.example.macclipboardmanager.core.hotkey.HotkeyModifier>,
        allowCommandOverlay: Boolean = false,
    ): Boolean {
        val relevantFlags = this and RelevantModifierFlagsMask
        val expectedFlags = modifiers.toEventTapFlags()
        return relevantFlags == expectedFlags ||
            (
                allowCommandOverlay &&
                    relevantFlags == (expectedFlags or CommandFlagMask) &&
                    expectedFlags and CommandFlagMask == 0L
                )
    }

    private fun Long.describeModifierFlags(): String {
        if (this == 0L) {
            return "none"
        }

        val names = buildList {
            if (this@describeModifierFlags and ShiftFlagMask != 0L) add("shift")
            if (this@describeModifierFlags and ControlFlagMask != 0L) add("control")
            if (this@describeModifierFlags and OptionFlagMask != 0L) add("option")
            if (this@describeModifierFlags and CommandFlagMask != 0L) add("command")
        }
        return names.joinToString(separator = "+")
    }

    private fun Set<com.example.macclipboardmanager.core.hotkey.HotkeyModifier>.toEventTapFlags(): Long =
        fold(0L) { acc, modifier ->
            acc or when (modifier) {
                com.example.macclipboardmanager.core.hotkey.HotkeyModifier.COMMAND -> CommandFlagMask
                com.example.macclipboardmanager.core.hotkey.HotkeyModifier.OPTION -> OptionFlagMask
                com.example.macclipboardmanager.core.hotkey.HotkeyModifier.CONTROL -> ControlFlagMask
                com.example.macclipboardmanager.core.hotkey.HotkeyModifier.SHIFT -> ShiftFlagMask
            }
        }
    private companion object {
        private const val LeftOptionKeyCode = 58
        private const val RightOptionKeyCode = 61
        private const val ShiftFlagMask = 0x0002_0000L
        private const val ControlFlagMask = 0x0004_0000L
        private const val OptionFlagMask = 0x0008_0000L
        private const val CommandFlagMask = 0x0010_0000L
        private const val RelevantModifierFlagsMask =
            ShiftFlagMask or ControlFlagMask or OptionFlagMask or CommandFlagMask
    }
}
