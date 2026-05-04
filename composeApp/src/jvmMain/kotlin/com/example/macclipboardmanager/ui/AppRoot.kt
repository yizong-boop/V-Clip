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
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
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
    val hotkeyRefreshController = remember { HotkeyRefreshController() }

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
        onHideWindowAfterConfirm = {
            spotlightWindowState.hide()
            viewModel.clearSearchQuery()
            viewModel.clearToast()
            isFailureToastWindowVisible = false
            spotlightController.onShowComplete()
            hotkeyRefreshController.requestRefreshAfterBlur()
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
            hotkeyRefreshController = hotkeyRefreshController,
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
    hotkeyRefreshController: HotkeyRefreshController,
) {
    val backgroundClickInteraction = remember { MutableInteractionSource() }

    fun hideWindowContent(restorePreviousApplicationFocus: Boolean = true) {
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
        val rootPane = window.rootPane
        val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actionMap = rootPane.actionMap

        val enterActionKey = "vclip.confirmSelection"
        val escapeActionKey = "vclip.hideWindow"
        val upActionKey = "vclip.selectPrevious"
        val downActionKey = "vclip.selectNext"

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), enterActionKey)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), escapeActionKey)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), upActionKey)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), downActionKey)

        actionMap.put(
            enterActionKey,
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) {
                    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                    System.err.println("[V-Clip] Enter key received in rootPane fallback handler (focusOwner=${focusOwner?.javaClass?.name})")
                    viewModel.confirmSelection()
                }
            },
        )
        actionMap.put(
            escapeActionKey,
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) {
                    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                    System.err.println("[V-Clip] Escape key received in rootPane fallback handler (focusOwner=${focusOwner?.javaClass?.name})")
                    hideWindowContent()
                }
            },
        )
        actionMap.put(
            upActionKey,
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) {
                    System.err.println("[V-Clip] Up key received in rootPane fallback handler")
                    viewModel.selectPrevious()
                }
            },
        )
        actionMap.put(
            downActionKey,
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) {
                    System.err.println("[V-Clip] Down key received in rootPane fallback handler")
                    viewModel.selectNext()
                }
            },
        )

        fun refreshHotkeyRegistrationIfPending(trigger: String) {
            if (!hotkeyRefreshController.consumePendingRefreshAfterBlur()) {
                return
            }

            System.err.println("[V-Clip] refreshing hotkey registration after blur ($trigger)")
            viewModel.refreshHotkeyRegistration()
        }

        fun handleBlur() {
            val now = System.currentTimeMillis()
            val shouldHide = spotlightWindowState.isVisible && spotlightController.shouldHideOnBlur(now)
            if (shouldHide) {
                System.err.println("[V-Clip] blur → hiding window (now=$now ignoreBefore=${spotlightController.ignoreBlurBeforeEpochMillis})")
                hideWindowContent(restorePreviousApplicationFocus = false)
            } else if (spotlightWindowState.isVisible) {
                System.err.println("[V-Clip] blur → deferred (now=$now ignoreBefore=${spotlightController.ignoreBlurBeforeEpochMillis})")
            }
        }

        val windowListener = object : WindowAdapter() {
            override fun windowGainedFocus(event: WindowEvent?) {
                val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                System.err.println("[V-Clip] windowGainedFocus (focusOwner=${focusOwner?.javaClass?.name})")
                spotlightController.onWindowGainedFocus()
            }

            override fun windowLostFocus(event: WindowEvent?) {
                System.err.println("[V-Clip] windowLostFocus")
                spotlightController.onWindowLostFocus()
                refreshHotkeyRegistrationIfPending("windowLostFocus")
                handleBlur()
            }

            override fun windowDeactivated(event: WindowEvent?) {
                System.err.println("[V-Clip] windowDeactivated")
                spotlightController.onWindowLostFocus()
                refreshHotkeyRegistrationIfPending("windowDeactivated")
                handleBlur()
            }
        }

        window.addWindowFocusListener(windowListener)
        window.addWindowListener(windowListener)
        onDispose {
            inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
            inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0))
            inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0))
            inputMap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0))
            actionMap.remove(enterActionKey)
            actionMap.remove(escapeActionKey)
            actionMap.remove(upActionKey)
            actionMap.remove(downActionKey)
            window.removeWindowFocusListener(windowListener)
            window.removeWindowListener(windowListener)
        }
    }

    DisposableEffect(window) {
        onDispose {
            visualEffectWindowStyler.release(window)
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
            System.err.println("[V-Clip] windowShowStart (isVisible=${spotlightWindowState.isVisible})")
            if (uiState.filteredItems.isNotEmpty()) {
                listState.scrollToItem(0)
            }

            val centeredLocation = windowState.centerOnDefaultScreen(
                width = SpotlightWindowWidth,
                height = SpotlightWindowHeight,
            )
            window.setLocation(centeredLocation)
            val visualOk = visualEffectWindowStyler.apply(window, currentTheme)
            if (!visualOk) {
                System.err.println("[V-Clip] visualEffect apply returned false — window may appear transparent")
            }
            window.isVisible = true
            window.focusableWindowState = true
            MacAppActivation.requestForeground()
            window.toFront()
            window.requestFocus()
            window.requestFocusInWindow()
            window.rootPane.requestFocusInWindow()
            System.err.println("[V-Clip] window show: focus requests issued (visualOk=$visualOk)")

            repeat(12) {
                withFrameNanos { }
                spotlightController.forceFocusRequest()
                delay(16L)
            }
            delay(80L)
            spotlightController.forceFocusRequest()
            delay(160L)
            spotlightController.forceFocusRequest()
            System.err.println("[V-Clip] windowShowFocusRetryBurstCompleted (isFocused=${spotlightController.isWindowFocused})")
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

private class HotkeyRefreshController {
    private var pendingRefreshAfterBlur = false

    fun requestRefreshAfterBlur() {
        pendingRefreshAfterBlur = true
    }

    fun consumePendingRefreshAfterBlur(): Boolean {
        if (!pendingRefreshAfterBlur) {
            return false
        }
        pendingRefreshAfterBlur = false
        return true
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
