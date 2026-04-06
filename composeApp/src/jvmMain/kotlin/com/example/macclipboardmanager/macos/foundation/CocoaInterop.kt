package com.example.macclipboardmanager.macos.foundation

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.example.macclipboardmanager.macos.MacPlatform
import com.example.macclipboardmanager.macos.objc.ObjcRuntime

internal object CocoaInterop {
    private val appKitLibrary: NativeLibrary = NativeLibrary.getInstance("AppKit")
    private val nsStringClass: Pointer = ObjcRuntime.getClass("NSString")
    private val nsArrayClass: Pointer = ObjcRuntime.getClass("NSArray")
    private val nsAutoreleasePoolClass: Pointer = ObjcRuntime.getClass("NSAutoreleasePool")

    val nsPasteboardClass: Pointer = ObjcRuntime.getClass("NSPasteboard")

    val nsPasteboardTypeString: Pointer by lazy {
        globalNSString("NSPasteboardTypeString") ?: nsString("public.utf8-plain-text")
    }

    fun <T> autoreleasePool(block: () -> T): T {
        MacPlatform.requireMacOs()
        val pool = requireNotNull(ObjcRuntime.sendPointer(nsAutoreleasePoolClass, "new")) {
            "Unable to create NSAutoreleasePool."
        }
        return try {
            block()
        } finally {
            ObjcRuntime.sendVoid(pool, "drain")
        }
    }

    fun nsString(value: String): Pointer =
        requireNotNull(ObjcRuntime.sendPointer(nsStringClass, "stringWithUTF8String:", value)) {
            "Unable to create NSString from JVM string."
        }

    fun toJavaString(nsString: Pointer?): String? {
        if (nsString == null || Pointer.nativeValue(nsString) == 0L) {
            return null
        }

        val utf8Pointer = ObjcRuntime.sendPointer(nsString, "UTF8String") ?: return null
        return utf8Pointer.getString(0, Charsets.UTF_8.name())
    }

    fun nsArrayToStrings(arrayPointer: Pointer?): List<String> {
        if (arrayPointer == null || Pointer.nativeValue(arrayPointer) == 0L) {
            return emptyList()
        }

        val count = ObjcRuntime.sendLong(arrayPointer, "count")
        if (count <= 0L) {
            return emptyList()
        }

        return buildList(count.toInt()) {
            repeat(count.toInt()) { index ->
                val item = ObjcRuntime.sendPointer(arrayPointer, "objectAtIndex:", index.toLong())
                toJavaString(item)?.let(::add)
            }
        }
    }

    private fun globalNSString(symbolName: String): Pointer? {
        val globalAddress = runCatching { appKitLibrary.getGlobalVariableAddress(symbolName) }.getOrNull()
            ?: return null
        return globalAddress.getPointer(0)
    }
}
