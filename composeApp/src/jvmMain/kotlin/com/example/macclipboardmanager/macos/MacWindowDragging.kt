package com.example.macclipboardmanager.macos

import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import com.sun.jna.Pointer

internal object MacWindowDragging {
    private val nsApplicationClass: Pointer by lazy { ObjcRuntime.getClass("NSApplication") }

    fun performDragWithCurrentEvent(): Boolean {
        if (!MacPlatform.isMacOs()) {
            return false
        }

        return runCatching {
            val sharedApplication =
                ObjcRuntime.sendPointer(nsApplicationClass, "sharedApplication")
                    ?: return@runCatching false
            val currentEvent =
                ObjcRuntime.sendPointer(sharedApplication, "currentEvent")
                    ?: return@runCatching false
            val targetWindow =
                ObjcRuntime.sendPointer(sharedApplication, "keyWindow")
                    ?: ObjcRuntime.sendPointer(sharedApplication, "mainWindow")
                    ?: return@runCatching false

            ObjcRuntime.sendVoid(targetWindow, "performWindowDragWithEvent:", currentEvent)
            true
        }.getOrDefault(false)
    }
}
