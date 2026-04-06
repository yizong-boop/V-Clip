package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.macos.objc.ObjcRuntime

internal object MacAppActivation {
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
