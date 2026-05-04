package com.example.macclipboardmanager.ui

import com.example.macclipboardmanager.feature.main.MainEffect

internal class SelectionEffectCoordinator {
    private var confirmPhase: ConfirmPhase = ConfirmPhase.IDLE
    private var pendingReopenRequest: MainEffect.ShowWindow? = null

    fun onShowWindow(effect: MainEffect.ShowWindow): ShowWindowDecision =
        when (confirmPhase) {
            ConfirmPhase.IDLE -> ShowWindowDecision.ProcessNow

            ConfirmPhase.RUNNING,
            ConfirmPhase.SETTLING -> {
                pendingReopenRequest = effect
                ShowWindowDecision.QueueUntilConfirmFinishes
            }
        }

    fun onConfirmStarted() {
        confirmPhase = ConfirmPhase.RUNNING
        pendingReopenRequest = null
    }

    fun onConfirmSettling() {
        if (confirmPhase == ConfirmPhase.RUNNING) {
            confirmPhase = ConfirmPhase.SETTLING
        }
    }

    fun onConfirmFinished(): MainEffect.ShowWindow? {
        confirmPhase = ConfirmPhase.IDLE
        return pendingReopenRequest.also {
            pendingReopenRequest = null
        }
    }

    sealed interface ShowWindowDecision {
        data object ProcessNow : ShowWindowDecision

        data object QueueUntilConfirmFinishes : ShowWindowDecision
    }

    private enum class ConfirmPhase {
        IDLE,
        RUNNING,
        SETTLING,
    }
}
