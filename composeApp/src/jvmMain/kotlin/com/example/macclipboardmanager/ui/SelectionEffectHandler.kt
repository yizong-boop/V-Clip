package com.example.macclipboardmanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.macclipboardmanager.core.diagnostics.PrintStreamDiagnostics
import com.example.macclipboardmanager.feature.main.MainEffect
import com.example.macclipboardmanager.feature.main.MainViewModel
import com.example.macclipboardmanager.macos.MacAppActivation
import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import com.example.macclipboardmanager.macos.paste.handleConfirmedSelection

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
    onAutoPasteFailureVisibleChanged: (Boolean) -> Unit,
) {
    var previousFrontmostAppProcessId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        bootstrapMacApp()
        viewModel.start()
        onDispose {
            viewModel.close()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainEffect.ShowWindow -> {
                    if (spotlightWindowState.isVisible) {
                        spotlightWindowState.hide()
                        viewModel.clearSearchQuery()
                        viewModel.clearToast()
                        spotlightController.onShowComplete()
                        onAutoPasteFailureVisibleChanged(false)
                        previousFrontmostAppProcessId = null
                    } else {
                        viewModel.clearToast()
                        onAutoPasteFailureVisibleChanged(false)
                        viewModel.clearSearchQuery()
                        viewModel.resetSelectionToFirst()
                        previousFrontmostAppProcessId =
                            MacAppActivation.captureFrontmostApplicationProcessId()
                        MacAppActivation.requestForeground()
                        spotlightWindowState.show()
                        spotlightController.prepareShow()
                    }
                }

                is MainEffect.ConfirmSelection -> {
                    try {
                        handleConfirmedSelection(
                            text = effect.text,
                            clipboardPasteController = clipboardPasteController,
                            onHideWindow = { spotlightWindowState.hide() },
                            onClearSearchQuery = viewModel::clearSearchQuery,
                            onRestorePreviousAppFocus = {
                                val processId = previousFrontmostAppProcessId
                                if (processId == null ||
                                    !MacAppActivation.reactivateApplication(processId)
                                ) {
                                    System.err.println(
                                        "Unable to reactivate the previously focused " +
                                            "application before auto-paste.",
                                    )
                                }
                                previousFrontmostAppProcessId = null
                            },
                            diagnostics = PrintStreamDiagnostics(),
                            onAutoPasteFailure = {
                                onAutoPasteFailureVisibleChanged(true)
                                spotlightWindowState.show()
                                spotlightController.prepareShow()
                                MacAppActivation.requestForeground()
                                viewModel.showToast(
                                    "粘贴失败：请在系统设置 -> 隐私与安全性 中开启辅助功能权限",
                                )
                            },
                        )
                    } finally {
                        viewModel.completeConfirmSelection()
                    }
                }
            }
        }
    }
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
