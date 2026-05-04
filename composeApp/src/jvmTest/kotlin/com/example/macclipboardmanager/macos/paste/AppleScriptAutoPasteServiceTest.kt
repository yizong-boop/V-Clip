package com.example.macclipboardmanager.macos.paste

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleScriptAutoPasteServiceTest {
    @Test
    fun cancellationTerminatesRunningOsaScriptProcess() = runTest {
        val process = BlockingProcess()
        val service = AppleScriptAutoPasteService(
            processLauncher = { process },
        )

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            service.pasteToFrontmostApp()
        }

        assertTrue(process.awaitStarted(), "process should have been launched")
        job.cancel(CancellationException("test cancel"))
        job.cancelAndJoin()

        assertTrue(process.destroyed, "process should have been terminated on cancellation")
    }

    @Test
    fun cancellationClearsRunningStateForNextPasteAttempt() = runTest {
        val first = BlockingProcess()
        val second = ImmediateSuccessProcess()
        val processes = ArrayDeque<Process>().apply {
            add(first)
            add(second)
        }
        val service = AppleScriptAutoPasteService(
            processLauncher = { processes.removeFirst() },
        )

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            service.pasteToFrontmostApp()
        }

        assertTrue(first.awaitStarted(), "first process should have been launched")
        job.cancel(CancellationException("test cancel"))
        job.cancelAndJoin()

        assertEquals(AutoPasteResult.Success, service.pasteToFrontmostApp())
    }

    private class BlockingProcess : Process() {
        private val started = CountDownLatch(1)
        private val finished = CountDownLatch(1)

        @Volatile
        var destroyed: Boolean = false

        fun awaitStarted(): Boolean = started.await(1L, TimeUnit.SECONDS)

        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int {
            started.countDown()
            finished.await()
            return exitValue()
        }

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            started.countDown()
            return finished.await(timeout, unit)
        }

        override fun exitValue(): Int = if (destroyed) 1 else 0

        override fun destroy() {
            destroyed = true
            finished.countDown()
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = !destroyed
    }

    private class ImmediateSuccessProcess : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = 0

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true

        override fun exitValue(): Int = 0

        override fun destroy() {
        }

        override fun destroyForcibly(): Process = this

        override fun isAlive(): Boolean = false
    }
}
