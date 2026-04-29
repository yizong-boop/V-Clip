package com.example.macclipboardmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.macclipboardmanager.domain.clipboard.ClipboardRepository
import com.example.macclipboardmanager.feature.main.MainEffect
import com.example.macclipboardmanager.feature.main.MainViewModel
import com.example.macclipboardmanager.macos.MacAppActivation
import com.example.macclipboardmanager.macos.createClipboardMonitor
import com.example.macclipboardmanager.macos.createClipboardPasteController
import com.example.macclipboardmanager.macos.createGlobalHotkeyManager
import com.example.macclipboardmanager.macos.paste.handleConfirmedSelection
import com.example.macclipboardmanager.smoke.MacSmokeDemo
import com.example.macclipboardmanager.ui.ClipboardWindowContent
import com.example.macclipboardmanager.ui.SpotlightWindowState
import com.example.macclipboardmanager.macos.objc.ObjcRuntime
import com.example.macclipboardmanager.ui.formatRelativeTime
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

fun main() {
    if (MacSmokeDemo.isEnabled()) {
        MacSmokeDemo.run()
        return
    }

    application {
        val repository = remember { ClipboardRepository() }
        val clipboardMonitor = remember { createClipboardMonitor() }
        val clipboardPasteController = remember { createClipboardPasteController(clipboardMonitor) }
        val hotkeyManager = remember { createGlobalHotkeyManager() }
        val viewModel = remember {
            MainViewModel(
                repository = repository,
                clipboardMonitor = clipboardMonitor,
                globalHotkeyManager = hotkeyManager,
            )
        }
        val windowState = rememberWindowState(
            width = 640.dp,
            height = 540.dp,
            position = WindowPosition(Alignment.Center),
        )
        val spotlightWindowState = remember { SpotlightWindowState() }
        val focusRequester = remember { FocusRequester() }
        val listState = rememberLazyListState()
        val uiState by viewModel.uiState.collectAsState()
        var focusRequestKey by remember { mutableStateOf(0) }
        var pendingSearchFieldFocus by remember { mutableStateOf(false) }
        var isWindowFocused by remember { mutableStateOf(false) }
        var ignoreBlurBeforeEpochMillis by remember { mutableStateOf(0L) }
        var previousFrontmostAppProcessId by remember { mutableStateOf<Int?>(null) }
        var isFailureToastWindowVisible by remember { mutableStateOf(false) }

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
                        viewModel.clearToast()
                        isFailureToastWindowVisible = false
                        viewModel.clearSearchQuery()
                        previousFrontmostAppProcessId = MacAppActivation.captureFrontmostApplicationProcessId()
                        MacAppActivation.requestForeground()
                        spotlightWindowState.show()
                        pendingSearchFieldFocus = true
                    }

                    is MainEffect.ConfirmSelection -> {
                        handleConfirmedSelection(
                            text = effect.text,
                            clipboardPasteController = clipboardPasteController,
                            onHideWindow = { spotlightWindowState.hide() },
                            onClearSearchQuery = viewModel::clearSearchQuery,
                            onRestorePreviousAppFocus = {
                                val processId = previousFrontmostAppProcessId
                                if (processId == null || !MacAppActivation.reactivateApplication(processId)) {
                                    System.err.println(
                                        "Unable to reactivate the previously focused application before auto-paste.",
                                    )
                                }
                                previousFrontmostAppProcessId = null
                            },
                            onAutoPasteFailure = {
                                isFailureToastWindowVisible = true
                                spotlightWindowState.show()
                                MacAppActivation.requestForeground()
                                viewModel.showToast(
                                    "粘贴失败：请在系统设置 -> 隐私与安全性 中开启辅助功能权限",
                                )
                            },
                        )
                    }
                }
            }
        }

        LaunchedEffect(uiState.toastMessage, isFailureToastWindowVisible) {
            if (isFailureToastWindowVisible && uiState.toastMessage == null && spotlightWindowState.isVisible) {
                spotlightWindowState.hide()
                isFailureToastWindowVisible = false
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            visible = spotlightWindowState.isVisible,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            title = "V-Clip",
            onPreviewKeyEvent = { false },
        ) {
            fun requestSearchFieldFocusIfNeeded() {
                if (pendingSearchFieldFocus) {
                    focusRequestKey += 1
                    pendingSearchFieldFocus = false
                }
            }

            fun hideWindowContent() {
                spotlightWindowState.hide()
                viewModel.clearSearchQuery()
                viewModel.clearToast()
                isFailureToastWindowVisible = false
                isWindowFocused = false
                pendingSearchFieldFocus = false
            }

            val backgroundClickInteraction = remember { MutableInteractionSource() }

            DisposableEffect(window) {
                fun hideWindow() {
                    val now = System.currentTimeMillis()
                    if (spotlightWindowState.isVisible && now >= ignoreBlurBeforeEpochMillis) {
                        hideWindowContent()
                    }
                }

                val windowListener = object : WindowAdapter() {
                    override fun windowGainedFocus(event: WindowEvent?) {
                        isWindowFocused = true
                        requestSearchFieldFocusIfNeeded()
                    }

                    override fun windowLostFocus(event: WindowEvent?) {
                        isWindowFocused = false
                        hideWindow()
                    }

                    override fun windowDeactivated(event: WindowEvent?) {
                        isWindowFocused = false
                        hideWindow()
                    }
                }

                window.addWindowFocusListener(windowListener)
                window.addWindowListener(windowListener)
                onDispose {
                    window.removeWindowFocusListener(windowListener)
                    window.removeWindowListener(windowListener)
                }
            }

            LaunchedEffect(spotlightWindowState.isVisible) {
                if (spotlightWindowState.isVisible) {
                    isWindowFocused = false
                    ignoreBlurBeforeEpochMillis = System.currentTimeMillis() + 350L
                    if (uiState.filteredItems.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                    repeat(8) { attempt ->
                        withFrameNanos { }
                        MacAppActivation.requestForeground()
                        window.isVisible = true
                        window.focusableWindowState = true
                        window.toFront()
                        window.requestFocus()
                        window.requestFocusInWindow()
                        window.rootPane.requestFocusInWindow()
                        delay(45L)
                        if (isWindowFocused || window.isFocused) {
                            requestSearchFieldFocusIfNeeded()
                            return@LaunchedEffect
                        }
                    }
                } else {
                    isWindowFocused = false
                    ignoreBlurBeforeEpochMillis = 0L
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = backgroundClickInteraction,
                            indication = null,
                        ) {
                            hideWindowContent()
                        },
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ClipboardWindowContent(
                        uiState = uiState,
                        focusRequester = focusRequester,
                        focusRequestKey = focusRequestKey,
                        listState = listState,
                        relativeTimeFormatter = { copiedAt ->
                            formatRelativeTime(
                                copiedAtEpochMillis = copiedAt,
                                nowEpochMillis = System.currentTimeMillis(),
                            )
                        },
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onConfirmSelection = viewModel::confirmSelection,
                        onHideRequest = ::hideWindowContent,
                        onSelectPrevious = viewModel::selectPrevious,
                        onSelectNext = viewModel::selectNext,
                        onSelectItem = viewModel::selectItem,
                    )
                }
            }
        }
    }
}

/**
 * Explicitly initialize the macOS application environment before Compose Desktop starts.
 *
 * On macOS 14+ (Sonoma/Sequoia), Carbon event delivery requires the process to be recognized
 * as a proper application. Without this, RegisterEventHotKey succeeds but events are quietly
 * dropped by the system. Calling sharedApplication and setting activation policy to Accessory
 * signals to macOS that this background process should receive app events.
 */
private fun bootstrapMacApp() {
    if (!System.getProperty("os.name").contains("mac", ignoreCase = true)) {
        return
    }

    runCatching {
        val appClass = ObjcRuntime.getClass("NSApplication")
        val sharedApp = ObjcRuntime.sendPointer(appClass, "sharedApplication")
        if (sharedApp != null) {
            // NSApplicationActivationPolicyAccessory = 1
            // Background app: no Dock icon, but can receive Carbon events
            ObjcRuntime.sendVoid(sharedApp, "setActivationPolicy:", 1L)
        }
    }.onFailure { error ->
        System.err.println("V-Clip: Failed to bootstrap macOS app environment: ${error.message}")
    }
}
