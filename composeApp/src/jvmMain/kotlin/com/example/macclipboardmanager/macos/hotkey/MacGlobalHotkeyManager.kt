package com.example.macclipboardmanager.macos.hotkey

import com.sun.jna.Pointer
import com.example.macclipboardmanager.core.hotkey.GlobalHotkeyManager
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyActivation
import com.example.macclipboardmanager.macos.MacPlatform
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
    private val eventHandlerCallback = CarbonBridge.EventHandlerCallback { _, eventRef, _ ->
        handleCarbonEvent(eventRef)
    }

    private var eventHandlerRef: Pointer? = null
    private var registeredHotkeyRef: Pointer? = null
    private var registeredHotkey: Hotkey? = null
    private var registeredHotkeyId: Int? = null
    private var nextRegistrationId = 1
    private var closed = false

    override val activations: Flow<HotkeyActivation> = mutableActivations.asSharedFlow()

    init {
        MacPlatform.requireMacOs()
    }

    override fun register(hotkey: Hotkey) {
        synchronized(lock) {
            check(!closed) { "Global hotkey manager is already closed." }
            ensureEventHandlerInstalled()
            unregisterHotkeyLocked()

            val registrationId = nextRegistrationId++
            registeredHotkeyRef = CarbonBridge.registerHotkey(hotkey, registrationId)
            registeredHotkey = hotkey
            registeredHotkeyId = registrationId
        }
    }

    override fun unregister() {
        synchronized(lock) {
            unregisterHotkeyLocked()
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

            mutableActivations.tryEmit(
                HotkeyActivation(
                    hotkey = hotkey,
                    activatedAtEpochMillis = nowEpochMillis(),
                ),
            )
            CarbonBridge.noErr()
        }.getOrElse { throwable ->
            System.err.println("Global hotkey callback failed: ${throwable.message}")
            CarbonBridge.noErr()
        }
    }

    private fun ensureEventHandlerInstalled() {
        if (eventHandlerRef != null) {
            return
        }

        eventHandlerRef = CarbonBridge.installHotKeyPressedHandler(eventHandlerCallback)
    }

    private fun unregisterHotkeyLocked() {
        val currentHotkeyRef = registeredHotkeyRef ?: run {
            registeredHotkey = null
            registeredHotkeyId = null
            return
        }

        CarbonBridge.unregisterHotkey(currentHotkeyRef)
        registeredHotkeyRef = null
        registeredHotkey = null
        registeredHotkeyId = null
    }

    private fun removeEventHandlerLocked() {
        val currentEventHandlerRef = eventHandlerRef ?: return
        CarbonBridge.removeEventHandler(currentEventHandlerRef)
        eventHandlerRef = null
    }
}
