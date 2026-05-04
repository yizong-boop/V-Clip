package com.example.macclipboardmanager.ui

import com.example.macclipboardmanager.feature.main.MainEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SelectionEffectCoordinatorTest {
    @Test
    fun `show window during confirm running is queued until confirm finishes`() {
        val coordinator = SelectionEffectCoordinator()
        val first = MainEffect.ShowWindow(requestedAtEpochMillis = 1L)

        coordinator.onConfirmStarted()

        assertIs<SelectionEffectCoordinator.ShowWindowDecision.QueueUntilConfirmFinishes>(
            coordinator.onShowWindow(first),
        )
        assertEquals(first, coordinator.onConfirmFinished())
        assertNull(coordinator.onConfirmFinished())
    }

    @Test
    fun `additional show window while settling is coalesced into one pending reopen`() {
        val coordinator = SelectionEffectCoordinator()
        val first = MainEffect.ShowWindow(requestedAtEpochMillis = 1L)
        val second = MainEffect.ShowWindow(requestedAtEpochMillis = 2L)

        coordinator.onConfirmStarted()

        assertIs<SelectionEffectCoordinator.ShowWindowDecision.QueueUntilConfirmFinishes>(
            coordinator.onShowWindow(first),
        )
        assertIs<SelectionEffectCoordinator.ShowWindowDecision.QueueUntilConfirmFinishes>(
            coordinator.onShowWindow(second),
        )

        assertEquals(second, coordinator.onConfirmFinished())
    }

    @Test
    fun `show window during settling auto paste is only queued`() {
        val coordinator = SelectionEffectCoordinator()
        val reopen = MainEffect.ShowWindow(requestedAtEpochMillis = 3L)

        coordinator.onConfirmStarted()
        coordinator.onConfirmSettling()

        assertIs<SelectionEffectCoordinator.ShowWindowDecision.QueueUntilConfirmFinishes>(
            coordinator.onShowWindow(reopen),
        )
        assertEquals(reopen, coordinator.onConfirmFinished())
    }

    @Test
    fun `show window processes immediately while idle`() {
        val coordinator = SelectionEffectCoordinator()

        assertIs<SelectionEffectCoordinator.ShowWindowDecision.ProcessNow>(
            coordinator.onShowWindow(MainEffect.ShowWindow(requestedAtEpochMillis = 1L)),
        )
    }
}
