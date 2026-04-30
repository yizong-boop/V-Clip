package com.example.macclipboardmanager.core.diagnostics

/**
 * Lightweight logging abstraction.
 *
 * Keeps platform-specific logging (System.err, java.util.logging, etc.) out of
 * shared domain code while still giving callers diagnostic visibility.
 */
interface AppDiagnostics {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
}
