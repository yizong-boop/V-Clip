package com.example.macclipboardmanager.macos.clipboard

import com.sun.jna.Pointer
import com.example.macclipboardmanager.core.clipboard.ClipboardMonitor
import com.example.macclipboardmanager.core.clipboard.ClipboardTextEvent
import com.example.macclipboardmanager.macos.MacPlatform
import com.example.macclipboardmanager.macos.foundation.CocoaInterop
import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MacClipboardMonitor(
    private val pollIntervalMillis: Long = 250L,
    dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ClipboardMonitor {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableEvents = MutableSharedFlow<ClipboardTextEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    private val lock = Any()

    private var monitorJob: Job? = null
    private var lastObservedChangeCount: Long? = null
    private var closed = false

    override val events: Flow<ClipboardTextEvent> = mutableEvents.asSharedFlow()

    init {
        MacPlatform.requireMacOs()
        require(pollIntervalMillis > 0L) { "pollIntervalMillis must be greater than 0." }
    }

    override fun start() {
        synchronized(lock) {
            check(!closed) { "Clipboard monitor is already closed." }
            if (monitorJob != null) {
                return
            }

            lastObservedChangeCount = runCatching { readChangeCount() }.getOrNull()
            monitorJob = scope.launch {
                while (isActive) {
                    pollClipboard()
                    delay(pollIntervalMillis)
                }
            }
        }
    }

    override fun stop() {
        val jobToCancel = synchronized(lock) {
            val currentJob = monitorJob
            monitorJob = null
            currentJob
        }

        jobToCancel?.cancel()
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
        }

        stop()
        scope.cancel()
    }

    private suspend fun pollClipboard() {
        runCatching {
            val changeCount = readChangeCount()
            val shouldReadText = synchronized(lock) {
                if (changeCount == lastObservedChangeCount) {
                    false
                } else {
                    lastObservedChangeCount = changeCount
                    true
                }
            }

            if (!shouldReadText) {
                return
            }

            val text = readPureText() ?: return
            mutableEvents.emit(
                ClipboardTextEvent(
                    text = text,
                    capturedAtEpochMillis = nowEpochMillis(),
                    changeCount = changeCount,
                ),
            )
        }.onFailure { throwable ->
            System.err.println("Clipboard polling failed: ${throwable.message}")
        }
    }

    private fun readChangeCount(): Long =
        CocoaInterop.autoreleasePool {
            val pasteboard = generalPasteboard()
            ObjcRuntime.sendLong(pasteboard, "changeCount")
        }

    private fun readPureText(): String? =
        CocoaInterop.autoreleasePool {
            val pasteboard = generalPasteboard()
            if (!hasReadablePlainText(pasteboard)) {
                return@autoreleasePool null
            }

            val nsString = ObjcRuntime.sendPointer(
                pasteboard,
                "stringForType:",
                CocoaInterop.nsPasteboardTypeString,
            )
            CocoaInterop.toJavaString(nsString)
        }

    private fun generalPasteboard(): Pointer =
        requireNotNull(ObjcRuntime.sendPointer(CocoaInterop.nsPasteboardClass, "generalPasteboard")) {
            "Unable to obtain NSPasteboard.generalPasteboard."
        }

    private fun hasReadablePlainText(pasteboard: Pointer): Boolean {
        val items = ObjcRuntime.sendPointer(pasteboard, "pasteboardItems") ?: return false
        val itemCount = ObjcRuntime.sendLong(items, "count")
        if (itemCount <= 0L) {
            return false
        }

        val firstItem = ObjcRuntime.sendPointer(items, "objectAtIndex:", 0L) ?: return false
        val typeNames = CocoaInterop.nsArrayToStrings(ObjcRuntime.sendPointer(firstItem, "types"))
        if (typeNames.isEmpty()) {
            return false
        }

        return typeNames.any(::isTextType)
    }

    private fun isTextType(typeName: String): Boolean =
        typeName == "NSStringPboardType" ||
            typeName == "public.text" ||
            typeName.startsWith("public.utf") ||
            typeName.endsWith("plain-text") ||
            typeName.contains("plain-text")
}
