package com.example.macclipboardmanager.macos.hotkey

import com.example.macclipboardmanager.macos.MacPlatform
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal object EventTapBridge {
    private val coreGraphics: CoreGraphicsLibrary = Native.load("ApplicationServices", CoreGraphicsLibrary::class.java)
    private val coreFoundation: CoreFoundationLibrary = Native.load("CoreFoundation", CoreFoundationLibrary::class.java)
    private val coreFoundationNativeLibrary: NativeLibrary = NativeLibrary.getInstance("CoreFoundation")

    private val kCFRunLoopCommonModes: Pointer by lazy {
        coreFoundationNativeLibrary.getGlobalVariableAddress("kCFRunLoopCommonModes").getPointer(0)
    }

    fun installKeyDownTap(callback: EventTapCallback): EventTapRegistration {
        MacPlatform.requireMacOs()
        val ready = CountDownLatch(1)
        val registrationRef = AtomicReference<EventTapRegistration?>()
        val failureRef = AtomicReference<Throwable?>()

        val thread = Thread {
            runCatching {
                val tap = requireNotNull(
                    coreGraphics.CGEventTapCreate(
                        kCGSessionEventTap,
                        kCGHeadInsertEventTap,
                        kCGEventTapOptionListenOnly,
                        eventMaskFor(kCGEventKeyDown, kCGEventFlagsChanged),
                        callback,
                        Pointer.NULL,
                    ),
                ) {
                    "CGEventTapCreate returned null."
                }

                val source = requireNotNull(
                    coreFoundation.CFMachPortCreateRunLoopSource(Pointer.NULL, tap, 0L),
                ) {
                    "CFMachPortCreateRunLoopSource returned null."
                }

                val runLoop = coreFoundation.CFRunLoopGetCurrent()
                coreFoundation.CFRunLoopAddSource(runLoop, source, kCFRunLoopCommonModes)
                coreGraphics.CGEventTapEnable(tap, true)
                registrationRef.set(
                    EventTapRegistration(
                        tap = tap,
                        runLoopSource = source,
                        runLoop = runLoop,
                        thread = Thread.currentThread(),
                    ),
                )
                ready.countDown()
                coreFoundation.CFRunLoopRun()
            }.onFailure { error ->
                failureRef.set(error)
                ready.countDown()
            }
        }.apply {
            name = "vclip-event-tap"
            isDaemon = true
        }

        thread.start()

        check(ready.await(2L, TimeUnit.SECONDS)) {
            "Timed out while installing event tap."
        }

        failureRef.get()?.let { throw it }
        return requireNotNull(registrationRef.get()) {
            "Event tap thread did not produce a registration."
        }
    }

    fun uninstall(registration: EventTapRegistration) {
        coreFoundation.CFRunLoopRemoveSource(registration.runLoop, registration.runLoopSource, kCFRunLoopCommonModes)
        coreFoundation.CFRunLoopSourceInvalidate(registration.runLoopSource)
        coreFoundation.CFMachPortInvalidate(registration.tap)
        coreFoundation.CFRelease(registration.runLoopSource)
        coreFoundation.CFRelease(registration.tap)
        coreFoundation.CFRunLoopStop(registration.runLoop)
        coreFoundation.CFRunLoopWakeUp(registration.runLoop)
        registration.thread.join(500L)
    }

    fun enable(tap: Pointer) {
        coreGraphics.CGEventTapEnable(tap, true)
    }

    fun readKeyCode(event: Pointer): Int =
        coreGraphics.CGEventGetIntegerValueField(event, kCGKeyboardEventKeycode).toInt()

    fun readFlags(event: Pointer): Long =
        coreGraphics.CGEventGetFlags(event)

    private fun eventMaskFor(vararg eventTypes: Int): Long =
        eventTypes.fold(0L) { acc, eventType -> acc or (1L shl eventType) }

    data class EventTapRegistration(
        val tap: Pointer,
        val runLoopSource: Pointer,
        val runLoop: Pointer,
        val thread: Thread,
    )

    fun interface EventTapCallback : Callback {
        fun callback(proxy: Pointer?, type: Int, event: Pointer?, userInfo: Pointer?): Pointer?
    }

    private interface CoreGraphicsLibrary : Library {
        fun CGEventTapCreate(
            tap: Int,
            place: Int,
            options: Int,
            eventsOfInterest: Long,
            callback: EventTapCallback,
            userInfo: Pointer?,
        ): Pointer?

        fun CGEventTapEnable(tap: Pointer, enable: Boolean)

        fun CGEventGetIntegerValueField(event: Pointer, field: Int): Long

        fun CGEventGetFlags(event: Pointer): Long
    }

    private interface CoreFoundationLibrary : Library {
        fun CFMachPortCreateRunLoopSource(allocator: Pointer?, port: Pointer, order: Long): Pointer?

        fun CFMachPortInvalidate(port: Pointer)

        fun CFRunLoopGetCurrent(): Pointer

        fun CFRunLoopAddSource(runLoop: Pointer, source: Pointer, mode: Pointer)

        fun CFRunLoopRemoveSource(runLoop: Pointer, source: Pointer, mode: Pointer)

        fun CFRunLoopRun()

        fun CFRunLoopStop(runLoop: Pointer)

        fun CFRunLoopWakeUp(runLoop: Pointer)

        fun CFRunLoopSourceInvalidate(source: Pointer)

        fun CFRelease(cfTypeRef: Pointer)
    }

    const val kCGEventTapDisabledByTimeout: Int = -2
    const val kCGEventTapDisabledByUserInput: Int = -1
    const val kCGEventKeyDown: Int = 10
    const val kCGEventFlagsChanged: Int = 12

    private const val kCGSessionEventTap = 1
    private const val kCGHeadInsertEventTap = 0
    private const val kCGEventTapOptionListenOnly = 1
    private const val kCGKeyboardEventKeycode = 9
}
