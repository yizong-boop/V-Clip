package com.example.macclipboardmanager.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviousApplicationFocusControllerTest {
    @Test
    fun restoreReactivatesCapturedApplicationOnce() {
        val reactivatedProcessIds = mutableListOf<Int>()
        val controller = PreviousApplicationFocusController(
            captureFocusedApplicationProcessId = { 42 },
            reactivateApplication = {
                reactivatedProcessIds += it
                true
            },
        )

        controller.capture()
        controller.restore()
        controller.restore()

        assertEquals(listOf(42), reactivatedProcessIds)
    }

    @Test
    fun restoreWithoutCapturedApplicationDoesNothing() {
        val reactivatedProcessIds = mutableListOf<Int>()
        val controller = PreviousApplicationFocusController(
            captureFocusedApplicationProcessId = { null },
            reactivateApplication = {
                reactivatedProcessIds += it
                true
            },
        )

        controller.capture()
        controller.restore()

        assertEquals(emptyList(), reactivatedProcessIds)
    }

    @Test
    fun clearDropsCapturedApplication() {
        val reactivatedProcessIds = mutableListOf<Int>()
        val controller = PreviousApplicationFocusController(
            captureFocusedApplicationProcessId = { 42 },
            reactivateApplication = {
                reactivatedProcessIds += it
                true
            },
        )

        controller.capture()
        controller.clear()
        controller.restore()

        assertEquals(emptyList(), reactivatedProcessIds)
    }

    @Test
    fun failedRestoreReportsFailureAndStillClearsCapture() {
        val failedProcessIds = mutableListOf<Int>()
        val reactivatedProcessIds = mutableListOf<Int>()
        val controller = PreviousApplicationFocusController(
            captureFocusedApplicationProcessId = { 42 },
            reactivateApplication = {
                reactivatedProcessIds += it
                false
            },
            onRestoreFailure = { failedProcessIds += it },
        )

        controller.capture()
        controller.restore()
        controller.restore()

        assertEquals(listOf(42), reactivatedProcessIds)
        assertEquals(listOf(42), failedProcessIds)
    }
}
