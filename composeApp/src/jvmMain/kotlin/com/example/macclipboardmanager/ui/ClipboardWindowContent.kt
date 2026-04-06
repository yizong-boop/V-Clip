package com.example.macclipboardmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import com.example.macclipboardmanager.feature.main.MainUiState
import kotlin.math.max

@Composable
fun ClipboardWindowContent(
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
) {
    val selectedIndex = uiState.filteredItems.indexOfFirst { it.id == uiState.selectedItemId }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var listViewportWidthPx by remember { mutableIntStateOf(0) }
    val selectedItemTextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = Color(0xFF111827),
        fontWeight = FontWeight.SemiBold,
    )
    val windowShape = RoundedCornerShape(28.dp)
    val relativeTimeTextStyle = MaterialTheme.typography.labelMedium.copy(
        color = Color(0xFF6B7280),
        fontWeight = FontWeight.Medium,
    )

    LaunchedEffect(uiState.selectedItemId, uiState.filteredItems, listViewportWidthPx) {
        if (selectedIndex < 0) {
            return@LaunchedEffect
        }

        withFrameNanos { }
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return@LaunchedEffect
        }

        val initialTargetInfo = visibleItems.firstOrNull { it.index == selectedIndex }
        val scrollTarget = when {
            initialTargetInfo != null -> ScrollTarget.Visible
            selectedIndex < visibleItems.first().index -> ScrollTarget.AlignTop
            selectedIndex > visibleItems.last().index -> ScrollTarget.AlignBottom
            else -> return@LaunchedEffect
        }

        when (scrollTarget) {
            ScrollTarget.Visible -> {
                listState.nudgeItemIntoViewport(selectedIndex)
            }

            ScrollTarget.AlignTop -> {
                listState.animateScrollToItem(selectedIndex)
            }

            ScrollTarget.AlignBottom -> {
                if (listViewportWidthPx <= 0) {
                    return@LaunchedEffect
                }

                val selectedItem = uiState.filteredItems[selectedIndex]
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val selectedItemHeight = measureClipboardListItemHeight(
                    item = selectedItem,
                    relativeTime = relativeTimeFormatter(selectedItem.copiedAtEpochMillis),
                    viewportWidthPx = listViewportWidthPx,
                    textMeasurer = textMeasurer,
                    density = density,
                    itemTextStyle = selectedItemTextStyle,
                    relativeTimeTextStyle = relativeTimeTextStyle,
                )
                val desiredTop = (viewportHeight - selectedItemHeight).coerceAtLeast(0)

                listState.animateScrollToItem(
                    index = selectedIndex,
                    scrollOffset = -desiredTop,
                )
            }
        }
    }

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
                SearchField(
                    query = uiState.searchQuery,
                    focusRequester = focusRequester,
                    focusRequestKey = focusRequestKey,
                    onValueChange = onSearchQueryChange,
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

private suspend fun LazyListState.nudgeItemIntoViewport(index: Int) {
    val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val itemTop = targetInfo.offset
    val itemBottom = targetInfo.offset + targetInfo.size

    when {
        itemTop < viewportStart -> animateScrollBy((itemTop - viewportStart).toFloat())
        itemBottom > viewportEnd -> animateScrollBy((itemBottom - viewportEnd).toFloat())
    }
}

private enum class ScrollTarget {
    Visible,
    AlignTop,
    AlignBottom,
}

private fun measureClipboardListItemHeight(
    item: ClipboardItem,
    relativeTime: String,
    viewportWidthPx: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    itemTextStyle: TextStyle,
    relativeTimeTextStyle: TextStyle,
): Int {
    val outerHorizontalPaddingPx = with(density) { (10.dp * 2).roundToPx() }
    val innerHorizontalPaddingPx = with(density) { (14.dp * 2).roundToPx() }
    val innerVerticalPaddingPx = with(density) { (12.dp * 2).roundToPx() }
    val spacerWidthPx = with(density) { 12.dp.roundToPx() }

    val timeLayout = textMeasurer.measure(
        text = relativeTime,
        style = relativeTimeTextStyle,
        maxLines = 1,
    )

    val contentWidthPx = (viewportWidthPx - outerHorizontalPaddingPx - innerHorizontalPaddingPx).coerceAtLeast(1)
    val textMaxWidthPx = (contentWidthPx - spacerWidthPx - timeLayout.size.width).coerceAtLeast(1)

    val textLayout = textMeasurer.measure(
        text = item.text,
        style = itemTextStyle,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = textMaxWidthPx),
    )

    return max(textLayout.size.height, timeLayout.size.height) + innerVerticalPaddingPx
}

@Composable
private fun SearchField(
    query: String,
    focusRequester: FocusRequester,
    focusRequestKey: Int,
    onValueChange: (String) -> Unit,
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
        Text(
            text = "Clipboard",
            style = TextStyle(
                color = Color(0xFF6B7280),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != query) {
                    onValueChange(newValue.text)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = 6.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                color = Color(0xFF111827),
                fontWeight = FontWeight.SemiBold,
            ),
            placeholder = {
                Text(
                    text = "Search clipboard history",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Medium,
                    ),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedTextColor = Color(0xFF111827),
                unfocusedTextColor = Color(0xFF111827),
                cursorColor = Color(0xFF111827),
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
) {
    val backgroundColor = if (isSelected) Color(0xFFDED8CB) else Color.Transparent
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(backgroundColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color(0xFF111827),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium,
            ),
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
