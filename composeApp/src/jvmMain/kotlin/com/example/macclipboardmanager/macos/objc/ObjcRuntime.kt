package com.example.macclipboardmanager.macos.objc

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.example.macclipboardmanager.macos.MacPlatform

internal object ObjcRuntime {
    private val objcLibrary: NativeLibrary = NativeLibrary.getInstance("objc")
    private val objcGetClass: Function = objcLibrary.getFunction("objc_getClass")
    private val selRegisterName: Function = objcLibrary.getFunction("sel_registerName")
    private val objcMsgSend: Function = objcLibrary.getFunction("objc_msgSend")

    fun getClass(name: String): Pointer {
        MacPlatform.requireMacOs()
        return requireNotNull(objcGetClass.invokePointer(arrayOf(name))) {
            "Objective-C class '$name' was not found."
        }
    }

    fun selector(name: String): Pointer =
        requireNotNull(selRegisterName.invokePointer(arrayOf(name))) {
            "Objective-C selector '$name' was not found."
        }

    fun sendPointer(receiver: Pointer, selectorName: String, vararg args: Any?): Pointer? =
        objcMsgSend.invokePointer(messageArgs(receiver, selectorName, *args))

    fun sendLong(receiver: Pointer, selectorName: String, vararg args: Any?): Long =
        objcMsgSend.invoke(Long::class.javaObjectType, messageArgs(receiver, selectorName, *args)) as Long

    fun sendInt(receiver: Pointer, selectorName: String, vararg args: Any?): Int =
        objcMsgSend.invoke(Int::class.javaObjectType, messageArgs(receiver, selectorName, *args)) as Int

    fun sendVoid(receiver: Pointer, selectorName: String, vararg args: Any?) {
        objcMsgSend.invoke(Void::class.java, messageArgs(receiver, selectorName, *args))
    }

    private fun messageArgs(receiver: Pointer, selectorName: String, vararg args: Any?): Array<Any?> =
        arrayOf(receiver, selector(selectorName), *args)
}
