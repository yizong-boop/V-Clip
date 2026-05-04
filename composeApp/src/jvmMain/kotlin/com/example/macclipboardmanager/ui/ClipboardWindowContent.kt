package com.example.macclipboardmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import com.example.macclipboardmanager.feature.main.MainUiState
import com.example.macclipboardmanager.theme.AppThemePreference
import java.awt.AWTEvent
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FrameWindowScope.ClipboardWindowContent(
    uiState: MainUiState,
    focusRequester: FocusRequester,
    focusRequestKey: Int,
    listState: LazyListState,
    themePreference: AppThemePreference,
    relativeTimeFormatter: (Long) -> String,
    onSearchQueryChange: (String) -> Unit,
    onConfirmSelection: () -> Unit,
    onHideRequest: () -> Unit,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onSelectItem: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
) {
    val theme = remember(themePreference) { clipboardThemeSpec(themePreference) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var listViewportWidthPx by remember { mutableIntStateOf(0) }
    val selectedItemTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = theme.primaryText,
        fontWeight = FontWeight.Normal,
    )
    val windowShape = RoundedCornerShape(28.dp)
    val relativeTimeTextStyle = MaterialTheme.typography.labelMedium.copy(
        color = theme.secondaryText,
        fontWeight = FontWeight.Medium,
    )
    val focusRestoreScope = rememberCoroutineScope()
    val restoreSearchFieldFocus = remember(focusRequester) {
        {
            focusRestoreScope.launch {
                withFrameNanos { }
                focusRequester.requestFocus()
            }
        }
    }

    ClipboardListScrollBehavior(
        listState = listState,
        filteredItems = uiState.filteredItems,
        selectedItemId = uiState.selectedItemId,
        listViewportWidthPx = listViewportWidthPx,
        relativeTimeFormatter = relativeTimeFormatter,
        textMeasurer = textMeasurer,
        density = density,
        selectedItemTextStyle = selectedItemTextStyle,
        relativeTimeTextStyle = relativeTimeTextStyle,
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 14.dp,
                shape = windowShape,
                clip = false,
                ambientColor = theme.panelShadowAmbient,
                spotColor = theme.panelShadowSpot,
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when (event.key) {
                    Key.DirectionUp -> {
                        onSelectPrevious()
                        true
                    }

                    Key.DirectionDown -> {
                        onSelectNext()
                        true
                    }

                    Key.Enter,
                    Key.NumPadEnter -> {
                        System.err.println("[V-Clip] Enter key received in Compose handler")
                        onConfirmSelection()
                        true
                    }

                    Key.Escape -> {
                        onHideRequest()
                        true
                    }

                    else -> false
                }
            }
            .border(
                width = 1.dp,
                color = theme.panelBorder,
                shape = windowShape,
            ),
        color = theme.panelFill,
        shape = windowShape,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 14.dp),
        ) {
            if (theme.isFrostedGlass) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(theme.topLightStart, theme.topLightEnd),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, theme.rim, windowShape),
                )
            }

            Column(
                verticalArrangement = Arrangement.Top,
            ) {
                ClipboardSearchField(
                    query = uiState.searchQuery,
                    favoritesOnly = uiState.favoritesOnly,
                    theme = theme,
                    focusRequester = focusRequester,
                    focusRequestKey = focusRequestKey,
                    onValueChange = onSearchQueryChange,
                    onConfirmSelection = onConfirmSelection,
                    onHideRequest = onHideRequest,
                    onSelectPrevious = onSelectPrevious,
                    onSelectNext = onSelectNext,
                    onToggleFavoritesOnly = {
                        onToggleFavoritesOnly()
                        restoreSearchFieldFocus()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = theme.divider,
                )
                if (uiState.filteredItems.isEmpty()) {
                    EmptyClipboardState(theme = theme)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .heightIn(max = 430.dp)
                            .onSizeChanged { listViewportWidthPx = it.width },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.filteredItems,
                            key = { _, item -> item.id },
                        ) { _, item ->
                            ClipboardListItem(
                                item = item,
                                isSelected = item.id == uiState.selectedItemId,
                                theme = theme,
                                relativeTime = relativeTimeFormatter(item.copiedAtEpochMillis),
                                onClick = {
                                    onSelectItem(item.id)
                                    onConfirmSelection()
                                },
                                onToggleFavorite = {
                                    onToggleFavorite(item.id)
                                    restoreSearchFieldFocus()
                                },
                                onTogglePinned = {
                                    onTogglePinned(item.id)
                                    restoreSearchFieldFocus()
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            ) {
                ToastBubble(
                    message = uiState.toastMessage.orEmpty(),
                    theme = theme,
                )
            }
        }
    }
}

@Composable
private fun FrameWindowScope.ClipboardSearchField(
    query: String,
    favoritesOnly: Boolean,
    theme: ClipboardThemeSpec,
    focusRequester: FocusRequester,
    focusRequestKey: Int,
    onValueChange: (String) -> Unit,
    onConfirmSelection: () -> Unit,
    onHideRequest: () -> Unit,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
        )
    }

    LaunchedEffect(query) {
        if (query != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            )
        }
    }

    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        SmoothWindowDragArea(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                ) {
                    ClipboardTitle(theme = theme)
                }
                ClipboardActionButton(
                    type = ClipboardActionType.Favorite,
                    isActive = favoritesOnly,
                    isVisible = true,
                    activeColor = theme.favoriteAccent,
                    inactiveColor = theme.inactiveIcon,
                    onClick = onToggleFavoritesOnly,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != query) {
                    onValueChange(newValue.text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    when (event.key) {
                        Key.DirectionUp -> {
                            System.err.println("[V-Clip] Up key received in search field handler")
                            onSelectPrevious()
                            true
                        }

                        Key.DirectionDown -> {
                            System.err.println("[V-Clip] Down key received in search field handler")
                            onSelectNext()
                            true
                        }

                        Key.Enter,
                        Key.NumPadEnter -> {
                            System.err.println("[V-Clip] Enter key received in search field handler")
                            onConfirmSelection()
                            true
                        }

                        Key.Escape -> {
                            System.err.println("[V-Clip] Escape key received in search field handler")
                            onHideRequest()
                            true
                        }

                        else -> false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = theme.primaryText,
                fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(theme.primaryText),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "Search clipboard history",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = theme.placeholderText,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 窗口拖拽实现 — 请勿随意修改
// ═══════════════════════════════════════════════════════════════════════════
//
// 背景：这是一个 macOS 无边框透明置顶窗口。拖拽经历了多轮试错才稳定。
// 完整复盘见：docs/window-dragging-technical-note.md
//
// ❌ 不要做的事（都试过，都失败了）：
//   1. 不要用 NSApplication.currentEvent + performWindowDragWithEvent:
//      → 窗口关闭重开后 currentEvent 过期，窗口瞬移。
//   2. 不要用 Compose 官方的 WindowDraggableArea：
//      → 每次 mouseDragged 都调 MouseInfo.getPointerInfo()，手感差。
//   3. 不要在 AWT mousePressed 里调 performWindowDragWithEvent:：
//      → AWT 事件分发时机下 currentEvent 仍不可靠，经常被校验逻辑拦截导致无法拖拽。
//   4. 不要删 runCatching：
//      → 窗口隐藏/销毁时 removeMouseListener 可能因窗口已 dispose 抛异常。
//
// ✅ 当前方案（SmoothWindowDragHandler）：
//   架构：Compose 只负责同步「标题区域 bounds」→ AWT mousePressed 命中后进入拖拽态 →
//         AWT mouseDragged 直接读 MouseEvent.locationOnScreen 计算位移 →
//         调 window.setLocation() → 鼠标释放时移除监听器。
//   优点：不依赖 NSApplication.currentEvent，不被 AppKit 事件生命周期影响；
//         不再依赖 Compose pointerInput 能否收到首次按下；拖动中用
//         MouseEvent.locationOnScreen 而非 MouseInfo.getPointerInfo()。
//
// ⚠️  remember(window) 的必要性：
//   V-Clip 窗口每次 show/hide 会重建 AWT Window 对象。用 window 作为 key
//   确保每次重建拿到新的 SmoothWindowDragHandler，避免操作已销毁的旧 window。
//
// 未来如果要追求原生丝滑手感，正确方向是 swizzle NSView.mouseDown: 拿到
// 真正的 NSEvent 再调 performWindowDragWithEvent:，而不是回到 currentEvent 路线。
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FrameWindowScope.SmoothWindowDragArea(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val dragHandler = remember(window) { SmoothWindowDragHandler(window) }

    DisposableEffect(dragHandler) {
        onDispose {
            dragHandler.dispose()
        }
    }

    // Compose 只负责提供标题区域 bounds，真正的按下/拖拽由 AWT 监听器处理。
    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            dragHandler.updateDragBounds(coordinates.boundsInWindow())
        },
    ) {
        content()
    }
}

private class SmoothWindowDragHandler(
    private val window: Window,
) {
    private var windowLocationAtDragStart: IntOffset? = null
    private var dragStartPoint: IntOffset? = null
    private var dragBounds: Rect = Rect.Zero

    private val mouseEventListener = AWTEventListener { awtEvent ->
        val event = awtEvent as? MouseEvent ?: return@AWTEventListener
        val belongsToWindow = belongsToWindow(event.component)
        if (event.id == MouseEvent.MOUSE_PRESSED) {
            DragDebugLog.log(
                "awtMousePressed component=${event.component?.javaClass?.name} " +
                    "point=(${event.x}, ${event.y}) belongsToWindow=$belongsToWindow",
            )
        }
        if (!belongsToWindow) {
            return@AWTEventListener
        }
        when (event.id) {
            MouseEvent.MOUSE_PRESSED -> onPress(event)
            MouseEvent.MOUSE_DRAGGED -> onDrag(event)
            MouseEvent.MOUSE_RELEASED -> stopDrag()
        }
    }

    init {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            mouseEventListener,
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
        )
    }

    fun updateDragBounds(bounds: Rect) {
        dragBounds = bounds.expandForDragHitArea(
            leftExpansion = bounds.left,
            topExpansion = bounds.top,
            rightExpansion = 0f,
            bottomExpansion = 40f,
        )
    }

    private fun onPress(event: MouseEvent) {
        if (event.button != MouseEvent.BUTTON1) {
            return
        }
        val pointInWindow = Offset(event.x.toFloat(), event.y.toFloat())
        if (!dragBounds.contains(pointInWindow)) {
            DragDebugLog.log("pressIgnoredOutsideDragBounds point=$pointInWindow bounds=$dragBounds")
            return
        }
        DragDebugLog.log("titlePointerDown point=$pointInWindow bounds=$dragBounds")
        stopDrag() // 防御：上一次拖拽可能未正常结束（如鼠标在窗口外释放）
        dragStartPoint = runCatching {
            event.locationOnScreen.toComposeOffset()
        }.getOrElse {
            MouseInfo.getPointerInfo()?.location?.toComposeOffset() ?: return
        }
        windowLocationAtDragStart = window.location.toComposeOffset()
        DragDebugLog.log(
            "dragStarted startPoint=$dragStartPoint windowLocation=$windowLocationAtDragStart",
        )
    }

    private fun onDrag(event: MouseEvent) {
        val startLocation = windowLocationAtDragStart ?: return
        val startPoint = dragStartPoint ?: return
        // 优先用 MouseEvent.locationOnScreen（比 MouseInfo.getPointerInfo() 路径更短）
        val currentPoint = runCatching {
            event.locationOnScreen.toComposeOffset()
        }.getOrElse {
            // fallback：极少数情况下 locationOnScreen 抛异常
            MouseInfo.getPointerInfo()?.location?.toComposeOffset() ?: return
        }
        val newLocation = startLocation + (currentPoint - startPoint)
        DragDebugLog.log(
            "mouseDragged currentPoint=$currentPoint newLocation=$newLocation",
        )
        window.setLocation(newLocation.x, newLocation.y)
    }

    private fun stopDrag() {
        if (windowLocationAtDragStart != null || dragStartPoint != null) {
            DragDebugLog.log("dragStopped")
        }
        windowLocationAtDragStart = null
        dragStartPoint = null
    }

    fun dispose() {
        runCatching {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseEventListener)
        }
        stopDrag()
    }

    private fun belongsToWindow(component: Component?): Boolean {
        var current = component
        while (current != null) {
            if (current === window) {
                return true
            }
            current = current.parent
        }
        return false
    }

}

private fun Point.toComposeOffset(): IntOffset = IntOffset(x, y)

private fun Rect.expandForDragHitArea(
    leftExpansion: Float,
    topExpansion: Float,
    rightExpansion: Float,
    bottomExpansion: Float,
): Rect = Rect(
    left = max(0f, left - leftExpansion),
    top = max(0f, top - topExpansion),
    right = right + rightExpansion,
    bottom = bottom + bottomExpansion,
)

// IntOffset.plus / IntOffset.minus 由 Compose 框架提供，不需要自定义运算符

@Composable
private fun ClipboardTitle(theme: ClipboardThemeSpec) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Clipboard",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = theme.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ClipboardListItem(
    item: ClipboardItem,
    isSelected: Boolean,
    theme: ClipboardThemeSpec,
    relativeTime: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    val backgroundColor = if (isSelected) theme.selectedRowFill else Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    var itemHeightPx by remember { mutableIntStateOf(0) }
    val showActions = isSelected || item.isFavorite || item.isPinned
    val indicatorHeight = remember(itemHeightPx, density) {
        if (itemHeightPx > 0) {
            with(density) { (itemHeightPx * 0.55f).toDp() }
        } else {
            16.dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .onSizeChanged { itemHeightPx = it.height }
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 6.dp, height = indicatorHeight)
                    .background(theme.selectedAccent, RoundedCornerShape(3.dp)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = item.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = theme.primaryText,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier.width(
                    ClipboardListItemLayout.trailingActionAreaWidthDp(
                        isSelected = isSelected,
                        isFavorite = item.isFavorite,
                        isPinned = item.isPinned,
                    ).dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = theme.secondaryText,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                ClipboardActionButton(
                    type = ClipboardActionType.Favorite,
                    isActive = item.isFavorite,
                    isVisible = showActions,
                    activeColor = theme.favoriteAccent,
                    inactiveColor = theme.inactiveIcon,
                    onClick = onToggleFavorite,
                )
                Spacer(modifier = Modifier.width(4.dp))
                ClipboardActionButton(
                    type = ClipboardActionType.Pin,
                    isActive = item.isPinned,
                    isVisible = showActions,
                    activeColor = theme.selectedAccent,
                    inactiveColor = theme.inactiveIcon,
                    onClick = onTogglePinned,
                )
            }
        }
    }
}

private enum class ClipboardActionType {
    Favorite,
    Pin,
}

@Composable
private fun ClipboardActionButton(
    type: ClipboardActionType,
    isActive: Boolean,
    isVisible: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
) {
    if (!isVisible) {
        Spacer(modifier = Modifier.size(26.dp))
        return
    }

    val iconColor = if (isActive) activeColor else inactiveColor
    val backgroundColor = if (isActive) iconColor.copy(alpha = 0.14f) else Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(26.dp)
            .background(backgroundColor, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            when (type) {
                ClipboardActionType.Favorite -> drawStarIcon(
                    color = iconColor,
                    filled = isActive,
                )

                ClipboardActionType.Pin -> drawPinIcon(
                    color = iconColor,
                    filled = isActive,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarIcon(
    color: Color,
    filled: Boolean,
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val outerRadius = min(size.width, size.height) * 0.46f
    val innerRadius = outerRadius * 0.44f
    val path = Path()

    repeat(10) { index ->
        val angle = -PI / 2.0 + index * PI / 5.0
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        val x = centerX + cos(angle).toFloat() * radius
        val y = centerY + sin(angle).toFloat() * radius
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    if (filled) {
        drawPath(path = path, color = color)
    } else {
        drawPath(path = path, color = color, style = Stroke(width = 1.6.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPinIcon(
    color: Color,
    filled: Boolean,
) {
    val strokeWidth = 1.7.dp.toPx()
    val headRadius = min(size.width, size.height) * 0.24f
    val centerX = size.width / 2f
    val headCenterY = size.height * 0.30f
    val tipY = size.height * 0.90f

    if (filled) {
        drawCircle(color = color, radius = headRadius, center = Offset(centerX, headCenterY))
        drawLine(
            color = color,
            start = Offset(centerX, headCenterY + headRadius * 0.6f),
            end = Offset(centerX, tipY),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX - headRadius * 1.35f, size.height * 0.52f),
            end = Offset(centerX + headRadius * 1.35f, size.height * 0.52f),
            strokeWidth = strokeWidth,
        )
    } else {
        drawCircle(
            color = color,
            radius = headRadius,
            center = Offset(centerX, headCenterY),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(centerX, headCenterY + headRadius),
            end = Offset(centerX, tipY),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(centerX - headRadius * 1.35f, size.height * 0.52f),
            end = Offset(centerX + headRadius * 1.35f, size.height * 0.52f),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
private fun EmptyClipboardState(theme: ClipboardThemeSpec) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No matches found",
            style = MaterialTheme.typography.titleMedium.copy(
                color = theme.emptyStateText,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = "Try a different keyword or copy something new.",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = theme.secondaryText,
            ),
        )
    }
}

@Composable
private fun ToastBubble(
    message: String,
    theme: ClipboardThemeSpec,
) {
    Surface(
        color = theme.toastFill,
        contentColor = theme.toastText,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = theme.toastAccent,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = theme.toastText,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
