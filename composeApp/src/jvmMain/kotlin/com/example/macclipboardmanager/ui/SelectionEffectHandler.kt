package com.example.macclipboardmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.macclipboardmanager.core.diagnostics.PrintStreamDiagnostics
import com.example.macclipboardmanager.feature.main.MainEffect
import com.example.macclipboardmanager.feature.main.MainViewModel
import com.example.macclipboardmanager.macos.MacAppActivation
import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import com.example.macclipboardmanager.macos.paste.AutoPasteResult
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import com.example.macclipboardmanager.macos.paste.handleConfirmedSelection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Handles view-model effect collection and dispatch.
 *
 * Keeps window-level effect orchestration (show/hide, confirm/paste, toast)
 * out of [main.kt] so the entry point stays focused on dependency wiring.
 */
@Composable
internal fun SelectionEffectHandler(
    viewModel: MainViewModel,
    clipboardPasteController: ClipboardPasteController,
    spotlightWindowState: SpotlightWindowState,
    spotlightController: SpotlightWindowController,
    previousApplicationFocusController: PreviousApplicationFocusController,
    onPrepareShowWindow: () -> Unit,
    onHideWindowAfterConfirm: () -> Unit,
    onAutoPasteFailureVisibleChanged: (Boolean) -> Unit,
) {
    DisposableEffect(Unit) {
        bootstrapMacApp()
        viewModel.start()
        onDispose {
            viewModel.close()
        }
    }

    LaunchedEffect(viewModel) {
        val coordinator = SelectionEffectCoordinator()
        val queue = Channel<SelectionQueueMessage>(capacity = Channel.UNLIMITED)
        var skipNextShowWindow = false
        var confirmSelectionJob: Job? = null

        suspend fun processShowWindow(
            effect: MainEffect.ShowWindow,
            allowStaleCoalescing: Boolean = true,
        ) {
            val age = System.currentTimeMillis() - effect.requestedAtEpochMillis

            if (allowStaleCoalescing && skipNextShowWindow) {
                System.err.println("[V-Clip] ShowWindow coalesced (skipped queued double-press, age=${age}ms)")
                skipNextShowWindow = false
                return
            }

            if (spotlightWindowState.isVisible) {
                System.err.println("[V-Clip] ShowWindow → HIDE (age=${age}ms)")
                spotlightWindowState.hide()
                viewModel.clearSearchQuery()
                viewModel.clearToast()
                spotlightController.onShowComplete()
                onAutoPasteFailureVisibleChanged(false)
                previousApplicationFocusController.restore()
            } else {
                System.err.println("[V-Clip] ShowWindow → SHOW (age=${age}ms)")
                viewModel.clearToast()
                onAutoPasteFailureVisibleChanged(false)
                viewModel.clearSearchQuery()
                viewModel.resetSelectionToFirst()
                previousApplicationFocusController.capture()
                onPrepareShowWindow()
                MacAppActivation.requestForeground()
                spotlightWindowState.show()
                spotlightController.prepareShow()
            }

            if (allowStaleCoalescing && age > 500L) {
                skipNextShowWindow = true
            }
        }

        launch {
            viewModel.effects.collect { effect ->
                queue.send(SelectionQueueMessage.EffectReceived(effect))
            }
        }

        for (message in queue) {
            when (message) {
                is SelectionQueueMessage.EffectReceived -> when (val effect = message.effect) {
                    is MainEffect.ShowWindow -> {
                        val age = System.currentTimeMillis() - effect.requestedAtEpochMillis
                        when (coordinator.onShowWindow(effect)) {
                            SelectionEffectCoordinator.ShowWindowDecision.ProcessNow ->
                                processShowWindow(effect)

                            SelectionEffectCoordinator.ShowWindowDecision.QueueUntilConfirmFinishes -> {
                                System.err.println("[V-Clip] ShowWindow queued until confirm finishes (age=${age}ms)")
                            }
                        }
                    }

                    is MainEffect.ConfirmSelection -> {
                        System.err.println("[V-Clip] ConfirmSelection processing (text len=${effect.text.length})")
                        skipNextShowWindow = false
                        coordinator.onConfirmStarted()
                        viewModel.setAllowCommandOverlayForNextActivation(true)
                        confirmSelectionJob = launch {
                            handleConfirmedSelection(
                                text = effect.text,
                                clipboardPasteController = clipboardPasteController,
                                onHideWindow = onHideWindowAfterConfirm,
                                onClearSearchQuery = viewModel::clearSearchQuery,
                                onRestorePreviousAppFocus = {
                                    previousApplicationFocusController.restore()
                                },
                                onAutoPasteStarting = {
                                    coordinator.onConfirmSettling()
                                    System.err.println("[V-Clip] ConfirmSelection entered non-interruptible auto-paste phase")
                                },
                                diagnostics = PrintStreamDiagnostics(),
                                onAutoPasteFailure = {
                                    val toast = buildAutoPasteFailureToast(it)
                                    if (toast == null) {
                                        System.err.println("[V-Clip] auto-paste failure suppressed in UI: ${it.message}")
                                        return@handleConfirmedSelection
                                    }

                                    onAutoPasteFailureVisibleChanged(true)
                                    previousApplicationFocusController.capture()
                                    onPrepareShowWindow()
                                    spotlightController.prepareShow()
                                    spotlightWindowState.show()
                                    MacAppActivation.requestForeground()
                                    viewModel.showToast(toast)
                                },
                            )
                        }.also { job ->
                            job.invokeOnCompletion { cause ->
                                queue.trySend(SelectionQueueMessage.ConfirmFinished(cause))
                            }
                        }
                    }
                }

                is SelectionQueueMessage.ConfirmFinished -> {
                    viewModel.setAllowCommandOverlayForNextActivation(false)
                    viewModel.completeConfirmSelection()
                    when (val cause = message.cause) {
                        null -> System.err.println("[V-Clip] ConfirmSelection completed")
                        is CancellationException ->
                            System.err.println("[V-Clip] ConfirmSelection cancelled: ${cause.message}")
                        else ->
                            System.err.println("[V-Clip] ConfirmSelection failed: ${cause.message}")
                    }
                    confirmSelectionJob = null
                    coordinator.onConfirmFinished()?.let { queuedEffect ->
                        processShowWindow(
                            effect = queuedEffect,
                            allowStaleCoalescing = false,
                        )
                    }
                }
            }
        }
    }
}

internal fun buildAutoPasteFailureToast(failure: AutoPasteResult.Failure): String? =
    when (failure.errorType) {
        AutoPasteResult.ErrorType.PERMISSION_DENIED ->
            "粘贴失败：请在系统设置 -> 隐私与安全性 中开启辅助功能权限"

        AutoPasteResult.ErrorType.TIMEOUT ->
            "粘贴失败：自动粘贴超时"

        AutoPasteResult.ErrorType.EXECUTION_ERROR ->
            if (failure.message == "Auto-paste command is already running.") {
                null
            } else {
                "粘贴失败：${failure.message}"
            }

        AutoPasteResult.ErrorType.UNKNOWN ->
            "粘贴失败：${failure.message}"
    }

private sealed interface SelectionQueueMessage {
    data class EffectReceived(val effect: MainEffect) : SelectionQueueMessage

    data class ConfirmFinished(val cause: Throwable?) : SelectionQueueMessage
}

private fun bootstrapMacApp() {
    if (!System.getProperty("os.name").contains("mac", ignoreCase = true)) {
        return
    }

    runCatching {
        val appClass = ObjcRuntime.getClass("NSApplication")
        val sharedApp = ObjcRuntime.sendPointer(appClass, "sharedApplication")
        if (sharedApp != null) {
            // NSApplicationActivationPolicyAccessory = 1
            ObjcRuntime.sendVoid(sharedApp, "setActivationPolicy:", 1L)
        }
    }.onFailure { error ->
        System.err.println("V-Clip: Failed to bootstrap macOS app environment: ${error.message}")
    }
}
