package com.example.macclipboardmanager.macos.paste

sealed interface AutoPasteResult {
    data object Success : AutoPasteResult

    data class Failure(
        val message: String,
        val errorType: ErrorType,
        val permissionHint: String? = null,
    ) : AutoPasteResult

    enum class ErrorType {
        /** The paste subprocess did not complete within the allotted time. */
        TIMEOUT,

        /** macOS accessibility permission was denied or missing. */
        PERMISSION_DENIED,

        /** The subprocess exited with a non-zero code for a known reason. */
        EXECUTION_ERROR,

        /** An unexpected exception was thrown while launching or monitoring the subprocess. */
        UNKNOWN,
    }
}
