package com.example.macclipboardmanager.macos.hotkey

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import com.example.macclipboardmanager.core.hotkey.Hotkey
import com.example.macclipboardmanager.core.hotkey.HotkeyModifier
import com.example.macclipboardmanager.macos.MacPlatform

internal object CarbonBridge {
    private val carbon: CarbonLibrary = Native.load("Carbon", CarbonLibrary::class.java)

    fun installHotKeyPressedHandler(callback: EventHandlerCallback): Pointer {
        MacPlatform.requireMacOs()
        val eventTypeSpec = hotKeyPressedEventSpec()

        val outRef = PointerByReference()
        checkStatus(
            carbon.InstallEventHandler(
                carbon.GetApplicationEventTarget(),
                callback,
                eventTypeSpec.size,
                eventTypeSpec,
                null,
                outRef,
            ),
            "Unable to install Carbon hotkey event handler.",
        )

        return requireNotNull(outRef.value) { "Carbon did not return an event handler reference." }
    }

    fun removeEventHandler(handlerRef: Pointer) {
        checkStatus(
            carbon.RemoveEventHandler(handlerRef),
            "Unable to remove Carbon event handler.",
        )
    }

    fun registerHotkey(hotkey: Hotkey, registrationId: Int): Pointer {
        val outRef = PointerByReference()
        // On Apple Silicon arm64, JNA cannot reliably pass small structs by value.
        // EventHotKeyID is 8 bytes (signature:4 + id:4) which on arm64 AAPCS is
        // passed in a single register. We pack it into a Long to avoid the JNA bug.
        val packedId = packEventHotKeyId(hotkeySignature, registrationId)

        checkStatus(
            carbon.RegisterEventHotKey(
                hotkey.keyCode,
                hotkey.modifiers.toCarbonFlags(),
                packedId,
                carbon.GetApplicationEventTarget(),
                kEventHotKeyNoOptions,
                outRef,
            ),
            "Unable to register Carbon hotkey ${hotkey.describe()}.",
        )

        return requireNotNull(outRef.value) { "Carbon did not return a hotkey reference." }
    }

    fun unregisterHotkey(hotkeyRef: Pointer) {
        checkStatus(
            carbon.UnregisterEventHotKey(hotkeyRef),
            "Unable to unregister Carbon hotkey.",
        )
    }

    fun readHotkeyId(eventRef: Pointer): EventHotKeyID {
        // Read raw 8 bytes directly to avoid JNA Structure alignment issues on arm64
        val buffer = Memory(8)
        checkStatus(
            carbon.GetEventParameter(
                eventRef,
                kEventParamDirectObject,
                typeEventHotKeyID,
                null,
                8,
                null,
                buffer,
            ),
            "Unable to decode Carbon hotkey event payload.",
        )
        return unpackEventHotKeyId(buffer.getLong(0))
    }

    fun noErr(): Int = 0

    /**
     * Pack an EventHotKeyID (signature + id) into a single 64-bit Long to work
     * around JNA's arm64 struct-by-value bug. On arm64 AAPCS a struct ≤ 8 bytes
     * is loaded into one register; lower 32 bits = first field, upper 32 bits = second.
     */
    private fun packEventHotKeyId(signature: Int, id: Int): Long =
        (id.toLong() shl 32) or (signature.toLong() and 0xFFFFFFFFL)

    private fun unpackEventHotKeyId(packed: Long): EventHotKeyID =
        EventHotKeyID().apply {
            signature = (packed and 0xFFFFFFFFL).toInt()
            id = (packed shr 32).toInt()
        }

    private fun checkStatus(status: Int, message: String) {
        if (status == 0) {
            return
        }

        error("$message OSStatus=$status")
    }

    private fun Set<HotkeyModifier>.toCarbonFlags(): Int =
        fold(0) { acc, modifier ->
            acc or when (modifier) {
                HotkeyModifier.COMMAND -> cmdKey
                HotkeyModifier.OPTION -> optionKey
                HotkeyModifier.CONTROL -> controlKey
                HotkeyModifier.SHIFT -> shiftKey
            }
        }

    private fun Hotkey.describe(): String =
        "keyCode=$keyCode modifiers=${modifiers.sortedBy { it.name }}"

    private fun fourCharCode(value: String): Int {
        require(value.length == 4) { "Four-char code must contain exactly 4 characters." }
        return value.fold(0) { acc, char -> (acc shl 8) or char.code }
    }

    @Suppress("UNCHECKED_CAST")
    private fun hotKeyPressedEventSpec(): Array<EventTypeSpec> =
        EventTypeSpec().apply {
            eventClass = kEventClassKeyboard
            eventKind = kEventHotKeyPressed
        }.toArray(1) as Array<EventTypeSpec>

    private interface CarbonLibrary : Library {
        fun GetApplicationEventTarget(): Pointer

        fun InstallEventHandler(
            inTarget: Pointer,
            inHandler: EventHandlerCallback,
            inNumTypes: Int,
            inList: Array<EventTypeSpec>,
            inUserData: Pointer?,
            outRef: PointerByReference,
        ): Int

        fun RemoveEventHandler(handlerRef: Pointer): Int

        fun RegisterEventHotKey(
            inHotKeyCode: Int,
            inHotKeyModifiers: Int,
            inHotKeyID: Long,
            inTarget: Pointer,
            inOptions: Int,
            outRef: PointerByReference,
        ): Int

        fun UnregisterEventHotKey(inHotKey: Pointer): Int

        fun GetEventParameter(
            inEvent: Pointer,
            inName: Int,
            inDesiredType: Int,
            outActualType: Pointer?,
            inBufferSize: Long,
            outActualSize: Pointer?,
            outData: Pointer,
        ): Int
    }

    fun interface EventHandlerCallback : Callback {
        fun callback(nextHandler: Pointer?, eventRef: Pointer?, userData: Pointer?): Int
    }

    @Structure.FieldOrder("eventClass", "eventKind")
    internal class EventTypeSpec : Structure() {
        @JvmField
        var eventClass: Int = 0

        @JvmField
        var eventKind: Int = 0
    }

    @Structure.FieldOrder("signature", "id")
    internal class EventHotKeyID : Structure() {
        @JvmField
        var signature: Int = 0

        @JvmField
        var id: Int = 0
    }

    private const val kEventHotKeyNoOptions = 0
    private val hotkeySignature = fourCharCode("MCBM")
    private val kEventClassKeyboard = fourCharCode("keyb")
    private const val kEventHotKeyPressed = 5
    private val kEventParamDirectObject = fourCharCode("----")
    private val typeEventHotKeyID = fourCharCode("hkid")
    private const val cmdKey = 1 shl 8
    private const val shiftKey = 1 shl 9
    private const val optionKey = 1 shl 11
    private const val controlKey = 1 shl 12
}
