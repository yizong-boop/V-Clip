package com.example.macclipboardmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.example.macclipboardmanager.domain.clipboard.ClipboardRepository
import com.example.macclipboardmanager.feature.main.MainUiState
import com.example.macclipboardmanager.feature.main.MainViewModel
import com.example.macclipboardmanager.macos.MacAppActivation
import com.example.macclipboardmanager.macos.MacVisualEffectWindowStyler
import com.example.macclipboardmanager.macos.createClipboardMonitor
import com.example.macclipboardmanager.macos.createClipboardPasteController
import com.example.macclipboardmanager.macos.createClipboardStore
import com.example.macclipboardmanager.macos.createGlobalHotkeyManager
import com.example.macclipboardmanager.macos.createThemePreferencesStore
import com.example.macclipboardmanager.macos.paste.ClipboardPasteController
import com.example.macclipboardmanager.theme.AppThemePreference
import com.example.macclipboardmanager.theme.ThemeController
import kotlinx.coroutines.delay
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.Window as AwtWindow
import kotlin.math.roundToInt

private val SpotlightWindowWidth = 600.dp
private val SpotlightWindowHeight = 520.dp

@Composable
fun AppRoot(onCloseRequest: () -> Unit) {
    val repository = remember { ClipboardRepository(store = createClipboardStore()) }
    val clipboardMonitor = remember { createClipboardMonitor() }
    val clipboardPasteController = remember { createClipboardPasteController(clipboardMonitor) }
    val hotkeyManager = remember { createGlobalHotkeyManager() }
    val themeScope = rememberCoroutineScope()
    val themeController = remember {
        ThemeController(
            store = createThemePreferencesStore(),
            scope = themeScope,
        )
    }
    val viewModel = remember {
        MainViewModel(
            repository = repository,
            clipboardMonitor = clipboardMonitor,
            globalHotkeyManager = hotkeyManager,
        )
    }
    val windowState = rememberWindowState(
        width = SpotlightWindowWidth,
        height = SpotlightWindowHeight,
        position = WindowPosition(Alignment.Center),
    )
    val spotlightWindowState = remember { SpotlightWindowState() }
    val spotlightController = remember { SpotlightWindowController() }
    val visualEffectWindowStyler = remember { MacVisualEffectWindowStyler() }
    val previousApplicationFocusController = remember {
        PreviousApplicationFocusController(
            captureFocusedApplicationProcessId = MacAppActivation::captureFrontmostApplicationProcessId,
            reactivateApplication = MacAppActivation::reactivateApplication,
            onRestoreFailure = {
                System.err.println("Unable to reactivate the previously focused application.")
            },
        )
    }
    val focusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme by themeController.theme.collectAsState()
    var isFailureToastWindowVisible by remember { mutableStateOf(false) }
    var awtWindow by remember { mutableStateOf<AwtWindow?>(null) }

    LaunchedEffect(themeController) {
        themeController.load()
    }

    SelectionEffectHandler(
        viewModel = viewModel,
        clipboardPasteController = clipboardPasteController,
        spotlightWindowState = spotlightWindowState,
        spotlightController = spotlightController,
        previousApplicationFocusController = previousApplicationFocusController,
        onPrepareShowWindow = {
            val centeredLocation = windowState.centerOnDefaultScreen(
                width = SpotlightWindowWidth,
                height = SpotlightWindowHeight,
            )
            awtWindow?.setLocation(centeredLocation)
        },
        onBeforeHideWindow = {
            awtWindow?.let { visualEffectWindowStyler.dispose(it) }
        },
        onAutoPasteFailureVisibleChanged = { isFailureToastWindowVisible = it },
    )

    LaunchedEffect(uiState.toastMessage, isFailureToastWindowVisible) {
        if (isFailureToastWindowVisible &&
            uiState.toastMessage == null &&
            spotlightWindowState.isVisible
        ) {
            awtWindow?.let { visualEffectWindowStyler.dispose(it) }
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
        AppThemeMenuBar(
            currentTheme = currentTheme,
            onThemeSelected = themeController::setTheme,
        )
        AppRootWindowContent(
            uiState = uiState,
            currentTheme = currentTheme,
            focusRequester = focusRequester,
            spotlightController = spotlightController,
            previousApplicationFocusController = previousApplicationFocusController,
            listState = listState,
            viewModel = viewModel,
            windowState = windowState,
            spotlightWindowState = spotlightWindowState,
            isFailureToastWindowVisible = isFailureToastWindowVisible,
            onFailureToastVisibleChanged = { isFailureToastWindowVisible = it },
            onWindowAvailable = { awtWindow = it },
            visualEffectWindowStyler = visualEffectWindowStyler,
        )
    }
}

@Composable
private fun FrameWindowScope.AppRootWindowContent(
    uiState: MainUiState,
    currentTheme: AppThemePreference,
    focusRequester: FocusRequester,
    spotlightController: SpotlightWindowController,
    previousApplicationFocusController: PreviousApplicationFocusController,
    listState: LazyListState,
    viewModel: MainViewModel,
    windowState: WindowState,
    spotlightWindowState: SpotlightWindowState,
    isFailureToastWindowVisible: Boolean,
    onFailureToastVisibleChanged: (Boolean) -> Unit,
    onWindowAvailable: (AwtWindow) -> Unit,
    visualEffectWindowStyler: MacVisualEffectWindowStyler,
) {
    val backgroundClickInteraction = remember { MutableInteractionSource() }

    fun hideWindowContent(restorePreviousApplicationFocus: Boolean = true) {
        visualEffectWindowStyler.dispose(window)
        spotlightWindowState.hide()
        viewModel.clearSearchQuery()
        viewModel.clearToast()
        onFailureToastVisibleChanged(false)
        spotlightController.onShowComplete()
        if (restorePreviousApplicationFocus) {
            previousApplicationFocusController.restore()
        }
    }

    DisposableEffect(window) {
        onWindowAvailable(window)

        fun handleBlur() {
            val now = System.currentTimeMillis()
            if (spotlightWindowState.isVisible && spotlightController.shouldHideOnBlur(now)) {
                hideWindowContent(restorePreviousApplicationFocus = false)
            }
        }

        val windowListener = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent?) {
                DragDebugLog.log("windowGainedFocus")
                spotlightController.onWindowGainedFocus()
            }

            override fun windowLostFocus(event: WindowEvent?) {
                DragDebugLog.log("windowLostFocus")
                spotlightController.onWindowLostFocus()
                handleBlur()
            }

            override fun windowDeactivated(event: WindowEvent?) {
                DragDebugLog.log("windowDeactivated")
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

    DisposableEffect(window) {
        onDispose {
            visualEffectWindowStyler.dispose(window)
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
                hideWindowContent(restorePreviousApplicationFocus = false)
            }
        }
    }

    LaunchedEffect(currentTheme) {
        if (spotlightWindowState.isVisible) {
            visualEffectWindowStyler.apply(window, currentTheme)
        }
    }

    LaunchedEffect(spotlightWindowState.isVisible) {
        if (spotlightWindowState.isVisible) {
            DragDebugLog.log("windowShowStart")
            if (uiState.filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }

            val centeredLocation = windowState.centerOnDefaultScreen(
                width = SpotlightWindowWidth,
                height = SpotlightWindowHeight,
            )
            window.setLocation(centeredLocation)
            visualEffectWindowStyler.apply(window, currentTheme)
            window.isVisible = true
            window.focusableWindowState = true
            MacAppActivation.requestForeground()
            window.toFront()
            window.requestFocus()
            window.requestFocusInWindow()
            window.rootPane.requestFocusInWindow()
            DragDebugLog.log("windowShowFocusedRequestsIssued")

            repeat(12) {
                withFrameNanos { }
                spotlightController.forceFocusRequest()
                delay(16L)
            }
            delay(80L)
            spotlightController.forceFocusRequest()
            delay(160L)
            spotlightController.forceFocusRequest()
            DragDebugLog.log("windowShowFocusRetryBurstCompleted")
        }
    }

    LaunchedEffect(spotlightController.isWindowFocused) {
        if (spotlightController.isWindowFocused && spotlightWindowState.isVisible) {
            spotlightController.forceFocusRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            ClipboardWindowContent(
                uiState = uiState,
                focusRequester = focusRequester,
                focusRequestKey = spotlightController.focusRequestKey,
                listState = listState,
                themePreference = currentTheme,
                relativeTimeFormatter = { copiedAt ->
                    formatRelativeTime(
                        copiedAtEpochMillis = copiedAt,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                },
                onSearchQueryChange = viewModel::updateSearchQuery,
                onConfirmSelection = viewModel::confirmSelection,
                onHideRequest = { hideWindowContent() },
                onSelectPrevious = viewModel::selectPrevious,
                onSelectNext = viewModel::selectNext,
                onSelectItem = viewModel::selectItem,
                onToggleFavorite = viewModel::toggleFavorite,
                onTogglePinned = viewModel::togglePinned,
                onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
            )
        }
    }
}

@Composable
private fun FrameWindowScope.AppThemeMenuBar(
    currentTheme: AppThemePreference,
    onThemeSelected: (AppThemePreference) -> Unit,
) {
    MenuBar {
        Menu("View") {
            Menu("Theme") {
                AppThemePreference.entries.forEach { theme ->
                    RadioButtonItem(
                        text = theme.displayName,
                        selected = theme == currentTheme,
                        onClick = { onThemeSelected(theme) },
                    )
                }
            }
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

private fun WindowState.centerOnDefaultScreen(
    width: Dp,
    height: Dp,
): Point {
    val bounds = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .bounds
    val x = bounds.x + ((bounds.width - width.value.roundToInt()) / 2)
    val y = bounds.y + ((bounds.height - height.value.roundToInt()) / 2)

    position = WindowPosition(x.dp, y.dp)
    return Point(x, y)
}
