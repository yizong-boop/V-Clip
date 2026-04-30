package com.example.macclipboardmanager.core.diagnostics

import java.io.PrintStream

/**
 * JVM [AppDiagnostics] implementation that writes to stderr, matching the
 * earlier ad-hoc `System.err.println` pattern.
 *
 * Replace with java.util.logging or a structured logger as the project grows.
 */
class PrintStreamDiagnostics(
    private val errorStream: PrintStream = System.err,
    private val tag: String = "V-Clip",
) : AppDiagnostics {

    override fun info(message: String) {
        errorStream.println("$tag [INFO] $message")
    }

    override fun warn(message: String) {
        errorStream.println("$tag [WARN] $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        errorStream.println("$tag [ERROR] $message")
        throwable?.printStackTrace(errorStream)
    }
}
