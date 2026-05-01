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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import com.example.macclipboardmanager.feature.main.MainUiState
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun FrameWindowScope.ClipboardWindowContent(
    uiState: MainUiState,
    focusRequester: FocusRequester,
    focusRequestKey: Int,
    listState: LazyListState,
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
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var listViewportWidthPx by remember { mutableIntStateOf(0) }
    val selectedItemTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color(0xFF111827),
        fontWeight = FontWeight.Normal,
    )
    val windowShape = RoundedCornerShape(28.dp)
    val relativeTimeTextStyle = MaterialTheme.typography.labelMedium.copy(
        color = Color(0xFF6B7280),
        fontWeight = FontWeight.Medium,
    )

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
            .width(600.dp)
            .heightIn(max = 520.dp)
            .shadow(
                elevation = 14.dp,
                shape = windowShape,
                clip = false,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x1A000000),
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
                color = Color(0x120F172A),
                shape = windowShape,
            ),
        color = Color(0xFFF6F3EC),
        shape = windowShape,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFF6F3EC))
                .padding(vertical = 14.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.Top,
            ) {
                ClipboardSearchField(
                    query = uiState.searchQuery,
                    favoritesOnly = uiState.favoritesOnly,
                    focusRequester = focusRequester,
                    focusRequestKey = focusRequestKey,
                    onValueChange = onSearchQueryChange,
                    onToggleFavoritesOnly = onToggleFavoritesOnly,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = Color(0x140F172A),
                )
                if (uiState.filteredItems.isEmpty()) {
                    EmptyClipboardState()
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
                                relativeTime = relativeTimeFormatter(item.copiedAtEpochMillis),
                                onClick = {
                                    onSelectItem(item.id)
                                    onConfirmSelection()
                                },
                                onToggleFavorite = { onToggleFavorite(item.id) },
                                onTogglePinned = { onTogglePinned(item.id) },
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
                )
            }
        }
    }
}

@Composable
private fun FrameWindowScope.ClipboardSearchField(
    query: String,
    favoritesOnly: Boolean,
    focusRequester: FocusRequester,
    focusRequestKey: Int,
    onValueChange: (String) -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmoothWindowDragArea(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            ) {
                ClipboardTitle()
            }
            ClipboardActionButton(
                type = ClipboardActionType.Favorite,
                isActive = favoritesOnly,
                isVisible = true,
                activeColor = Color(0xFFE6B84A),
                onClick = onToggleFavoritesOnly,
            )
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
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = Color(0xFF111827),
                fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(Color(0xFF111827)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = "Search clipboard history",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

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

    Box(
        modifier = modifier.pointerInput(dragHandler) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                dragHandler.onDragStarted()
            }
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

    private val dragListener = object : MouseMotionAdapter() {
        override fun mouseDragged(event: MouseEvent) {
            onDrag(event)
        }
    }

    private val releaseListener = object : MouseAdapter() {
        override fun mouseReleased(event: MouseEvent) {
            stopDrag()
        }
    }

    fun onDragStarted() {
        stopDrag()
        dragStartPoint = MouseInfo.getPointerInfo()?.location?.toComposeOffset() ?: return
        windowLocationAtDragStart = window.location.toComposeOffset()
        window.addMouseListener(releaseListener)
        window.addMouseMotionListener(dragListener)
    }

    private fun onDrag(event: MouseEvent) {
        val startLocation = windowLocationAtDragStart ?: return
        val startPoint = dragStartPoint ?: return
        val currentPoint = runCatching {
            event.locationOnScreen.toComposeOffset()
        }.getOrElse {
            MouseInfo.getPointerInfo()?.location?.toComposeOffset() ?: return
        }
        val newLocation = startLocation + (currentPoint - startPoint)
        window.setLocation(newLocation.x, newLocation.y)
    }

    private fun stopDrag() {
        window.removeMouseMotionListener(dragListener)
        window.removeMouseListener(releaseListener)
        windowLocationAtDragStart = null
        dragStartPoint = null
    }

    fun dispose() {
        stopDrag()
    }
}

private fun Point.toComposeOffset(): IntOffset = IntOffset(x = x, y = y)

@Composable
private fun ClipboardTitle() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "Clipboard",
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                color = Color(0xFF6B7280),
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
    relativeTime: String,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    val selectedAccentColor = Color(0xFFCE8381)
    val backgroundColor = if (isSelected) Color(0xFFF6EBE8) else Color.Transparent
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
                    .background(selectedAccentColor, RoundedCornerShape(3.dp)),
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
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (showActions) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ClipboardActionButton(
                        type = ClipboardActionType.Favorite,
                        isActive = item.isFavorite,
                        isVisible = true,
                        activeColor = Color(0xFFE6B84A),
                        onClick = onToggleFavorite,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    ClipboardActionButton(
                        type = ClipboardActionType.Pin,
                        isActive = item.isPinned,
                        isVisible = true,
                        activeColor = selectedAccentColor,
                        onClick = onTogglePinned,
                    )
                }
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
    onClick: () -> Unit,
) {
    if (!isVisible) {
        Spacer(modifier = Modifier.size(26.dp))
        return
    }

    val iconColor = if (isActive) activeColor else Color(0xFFB8B4AC)
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
private fun EmptyClipboardState() {
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
                color = Color(0xFF111827),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = "Try a different keyword or copy something new.",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF6B7280),
            ),
        )
    }
}

@Composable
private fun ToastBubble(message: String) {
    Surface(
        color = Color(0xE6151720),
        contentColor = Color(0xFFF8FAFC),
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
                        color = Color(0xFFF97316),
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
