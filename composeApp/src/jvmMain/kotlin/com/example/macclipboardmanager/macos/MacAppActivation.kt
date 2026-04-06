package com.example.macclipboardmanager.macos

import com.sun.jna.Pointer
import com.example.macclipboardmanager.macos.foundation.CocoaInterop
import com.example.macclipboardmanager.macos.objc.ObjcRuntime

internal object MacAppActivation {
    fun captureFrontmostApplicationProcessId(): Int? =
        runCatching {
            CocoaInterop.autoreleasePool {
                val workspaceClass = ObjcRuntime.getClass("NSWorkspace")
                val sharedWorkspace = ObjcRuntime.sendPointer(workspaceClass, "sharedWorkspace")
                val frontmostApp = sharedWorkspace?.let {
                    ObjcRuntime.sendPointer(it, "frontmostApplication")
                }
                frontmostApp?.takeUnless { Pointer.nativeValue(it) == 0L }?.let {
                    ObjcRuntime.sendInt(it, "processIdentifier")
                }
            }
        }.getOrNull()

    fun reactivateApplication(processId: Int): Boolean =
        runCatching {
            CocoaInterop.autoreleasePool {
                val applicationClass = ObjcRuntime.getClass("NSRunningApplication")
                val application = ObjcRuntime.sendPointer(
                    applicationClass,
                    "runningApplicationWithProcessIdentifier:",
                    processId,
                ) ?: return@autoreleasePool false

                ObjcRuntime.sendLong(application, "activateWithOptions:", 1L) != 0L
            }
        }.getOrDefault(false)

    fun requestForeground() {
        runCatching {
            val applicationClass = ObjcRuntime.getClass("NSApplication")
            val sharedApplication = ObjcRuntime.sendPointer(applicationClass, "sharedApplication")
            if (sharedApplication != null) {
                ObjcRuntime.sendVoid(sharedApplication, "activateIgnoringOtherApps:", true)
            }
        }

        runCatching {
            val applicationClass = Class.forName("com.apple.eawt.Application")
            val getApplication = applicationClass.getMethod("getApplication")
            val application = getApplication.invoke(null)
            val requestForeground = applicationClass.getMethod("requestForeground", Boolean::class.javaPrimitiveType)
            requestForeground.invoke(application, true)
        }
    }
}
