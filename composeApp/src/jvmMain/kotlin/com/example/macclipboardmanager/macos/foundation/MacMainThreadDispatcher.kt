package com.example.macclipboardmanager.macos.foundation

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.CountDownLatch
import java.util.Collections

internal object MacMainThreadDispatcher {
    private val systemLibrary: NativeLibrary by lazy {
        NativeLibrary.getInstance("System")
    }
    private val dispatchAsync: Function by lazy {
        systemLibrary.getFunction("dispatch_async_f")
    }
    private val dispatchSync: Function by lazy {
        systemLibrary.getFunction("dispatch_sync_f")
    }
    private val pthreadMainNp: Function by lazy {
        systemLibrary.getFunction("pthread_main_np")
    }
    private val mainQueue: Pointer by lazy {
        runCatching {
            systemLibrary.getFunction("dispatch_get_main_queue").invokePointer(emptyArray())
        }.getOrElse {
            systemLibrary.getGlobalVariableAddress("_dispatch_main_q")
        }
    }
    private val pendingCallbacks = Collections.synchronizedSet(mutableSetOf<DispatchFunction>())

    fun dispatch(block: () -> Unit) {
        val callback = object : DispatchFunction {
            override fun callback(context: Pointer?) {
                try {
                    block()
                } finally {
                    pendingCallbacks.remove(this)
                }
            }
        }
        pendingCallbacks.add(callback)
        dispatchAsync.invokeVoid(arrayOf(mainQueue, Pointer.NULL, callback))
    }

    fun <T> dispatchSync(block: () -> T): T {
        if (isMainThread()) {
            return block()
        }

        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        val callback = object : DispatchFunction {
            override fun callback(context: Pointer?) {
                try {
                    result = runCatching(block)
                } finally {
                    pendingCallbacks.remove(this)
                    latch.countDown()
                }
            }
        }
        pendingCallbacks.add(callback)
        dispatchSync.invokeVoid(arrayOf(mainQueue, Pointer.NULL, callback))
        latch.await()
        return result!!.getOrThrow()
    }

    private fun isMainThread(): Boolean =
        (pthreadMainNp.invoke(Int::class.javaObjectType, emptyArray()) as Int) == 1

    private interface DispatchFunction : Callback {
        fun callback(context: Pointer?)
    }
}
