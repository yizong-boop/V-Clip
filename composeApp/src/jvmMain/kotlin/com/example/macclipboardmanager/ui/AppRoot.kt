package com.example.macclipboardmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.example.macclipboardmanager.domain.clipboard.ClipboardRepository
import com.example.macclipboardmanager.feature.main.MainUiState
import com.example.macclipboardmanager.feature.main.MainViewModel
import com.example.macclipboardmanager.macos.MacAppActivation
import com.example.macclipboardmanager.macos.createClipboardMonitor
import com.example.macclipboardmanager.macos.createClipboardPasteController
import com.example.macclipboardmanager.macos.createClipboardStore
import com.example.macclipboardmanager.macos.createGlobalHotkeyManager
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import kotlinx.coroutines.delay
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Root composable for the V-Clip desktop application.
 *
 * Creates dependencies, composes the Spotlight overlay window, wires up
 * the effect handler, and manages focus retry / blur handling.
 */
@Composable
fun AppRoot(onCloseRequest: () -> Unit) {
    val repository = remember { ClipboardRepository(store = createClipboardStore()) }
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
    val spotlightController = remember { SpotlightWindowController() }
    val focusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    var isFailureToastWindowVisible by remember { mutableStateOf(false) }

    SelectionEffectHandler(
        viewModel = viewModel,
        clipboardPasteController = clipboardPasteController,
        spotlightWindowState = spotlightWindowState,
        spotlightController = spotlightController,
        onPrepareShowWindow = {
            windowState.position = WindowPosition(Alignment.Center)
        },
        onAutoPasteFailureVisibleChanged = { isFailureToastWindowVisible = it },
    )

    LaunchedEffect(uiState.toastMessage, isFailureToastWindowVisible) {
        if (isFailureToastWindowVisible &&
            uiState.toastMessage == null &&
            spotlightWindowState.isVisible
        ) {
            spotlightWindowState.hide()
            isFailureToastWindowVisible = false
        }
    }

    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        visible = spotlightWindowState.isVisible,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
        title = "V-Clip",
        onPreviewKeyEvent = { false },
    ) {
        AppRootWindowContent(
            uiState = uiState,
            focusRequester = focusRequester,
            spotlightController = spotlightController,
            listState = listState,
            viewModel = viewModel,
            spotlightWindowState = spotlightWindowState,
            isFailureToastWindowVisible = isFailureToastWindowVisible,
            onFailureToastVisibleChanged = { isFailureToastWindowVisible = it },
        )
    }
}

/**
 * Window-scoped composable that manages focus retry, blur handling, and
 * renders the Spotlight content.
 */
@Composable
private fun FrameWindowScope.AppRootWindowContent(
    uiState: MainUiState,
    focusRequester: FocusRequester,
    spotlightController: SpotlightWindowController,
    listState: LazyListState,
    viewModel: MainViewModel,
    spotlightWindowState: SpotlightWindowState,
    isFailureToastWindowVisible: Boolean,
    onFailureToastVisibleChanged: (Boolean) -> Unit,
) {
    val backgroundClickInteraction = remember { MutableInteractionSource() }

    fun hideWindowContent() {
        spotlightWindowState.hide()
        viewModel.clearSearchQuery()
        viewModel.clearToast()
        onFailureToastVisibleChanged(false)
        spotlightController.onShowComplete()
    }

    DisposableEffect(window) {
        fun handleBlur() {
            val now = System.currentTimeMillis()
            if (spotlightWindowState.isVisible && spotlightController.shouldHideOnBlur(now)) {
                hideWindowContent()
            }
        }

        val windowListener = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent?) {
                spotlightController.onWindowGainedFocus()
            }

            override fun windowLostFocus(event: WindowEvent?) {
                spotlightController.onWindowLostFocus()
                handleBlur()
            }

            override fun windowDeactivated(event: WindowEvent?) {
                spotlightController.onWindowLostFocus()
                handleBlur()
            }
        }

        window.addWindowFocusListener(windowListener)
        window.addWindowListener(windowListener)
        onDispose {
            window.removeWindowFocusListener(windowListener)
            window.removeWindowListener(windowListener)
        }
    }

    LaunchedEffect(spotlightController.deferredBlurHideRequestKey) {
        if (spotlightController.deferredBlurHideRequestKey > 0) {
            val delayMillis =
                spotlightController.deferredBlurHideAtEpochMillis - System.currentTimeMillis()
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            if (spotlightWindowState.isVisible &&
                spotlightController.shouldHideDeferredBlur(System.currentTimeMillis())
            ) {
                hideWindowContent()
            }
        }
    }

    // Show → focus retry burst
    LaunchedEffect(spotlightWindowState.isVisible) {
        if (spotlightWindowState.isVisible) {
            if (uiState.filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }

            MacAppActivation.requestForeground()
            window.isVisible = true
            window.focusableWindowState = true
            window.toFront()
            window.requestFocus()
            window.requestFocusInWindow()
            window.rootPane.requestFocusInWindow()

            // Burst: immediate focus requests every frame
            repeat(12) {
                withFrameNanos { }
                spotlightController.forceFocusRequest()
                delay(16L)
            }
            // Delayed retries for late macOS focus grant
            delay(80L)
            spotlightController.forceFocusRequest()
            delay(160L)
            spotlightController.forceFocusRequest()
        }
    }

    // Window gains focus → push one more focus request
    LaunchedEffect(spotlightController.isWindowFocused) {
        if (spotlightController.isWindowFocused && spotlightWindowState.isVisible) {
            spotlightController.forceFocusRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Click-through background to dismiss
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
                focusRequestKey = spotlightController.focusRequestKey,
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

@Composable
private fun rememberWindowState(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    position: WindowPosition,
): WindowState = androidx.compose.ui.window.rememberWindowState(
    width = width,
    height = height,
    position = position,
)
