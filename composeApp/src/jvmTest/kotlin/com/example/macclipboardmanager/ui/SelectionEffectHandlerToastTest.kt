package com.example.macclipboardmanager.ui

import com.example.macclipboardmanager.macos.paste.AutoPasteResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionEffectHandlerToastTest {
    @Test
    fun `permission failure keeps permission toast`() {
        assertEquals(
            "粘贴失败：请在系统设置 -> 隐私与安全性 中开启辅助功能权限",
            buildAutoPasteFailureToast(
                AutoPasteResult.Failure(
                    message = "not allowed",
                    errorType = AutoPasteResult.ErrorType.PERMISSION_DENIED,
                ),
            ),
        )
    }

    @Test
    fun `timeout failure shows timeout toast`() {
        assertEquals(
            "粘贴失败：自动粘贴超时",
            buildAutoPasteFailureToast(
                AutoPasteResult.Failure(
                    message = "timed out",
                    errorType = AutoPasteResult.ErrorType.TIMEOUT,
                ),
            ),
        )
    }

    @Test
    fun `execution failure for already running paste is suppressed`() {
        assertEquals(
            null,
            buildAutoPasteFailureToast(
                AutoPasteResult.Failure(
                    message = "Auto-paste command is already running.",
                    errorType = AutoPasteResult.ErrorType.EXECUTION_ERROR,
                ),
            ),
        )
    }

    @Test
    fun `other execution failure shows real error message`() {
        assertEquals(
            "粘贴失败：Auto-paste command failed: boom",
            buildAutoPasteFailureToast(
                AutoPasteResult.Failure(
                    message = "Auto-paste command failed: boom",
                    errorType = AutoPasteResult.ErrorType.EXECUTION_ERROR,
                ),
            ),
        )
    }
}
