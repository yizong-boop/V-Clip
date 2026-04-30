package com.example.macclipboardmanager.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import kotlin.math.max

/**
 * Drives minimal-scroll behavior so the selected item stays in the viewport.
 *
 * Extracted from [ClipboardWindowContent] so the scroll algorithm can be
 * tested independently of UI rendering and keyboard-handling code.
 */
@Composable
fun ClipboardListScrollBehavior(
    listState: LazyListState,
    filteredItems: List<ClipboardItem>,
    selectedItemId: String?,
    listViewportWidthPx: Int,
    relativeTimeFormatter: (Long) -> String,
    textMeasurer: TextMeasurer,
    density: Density,
    selectedItemTextStyle: TextStyle,
    relativeTimeTextStyle: TextStyle,
) {
    val selectedIndex = filteredItems.indexOfFirst { it.id == selectedItemId }

    LaunchedEffect(selectedItemId, filteredItems, listViewportWidthPx) {
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

                val selectedItem = filteredItems[selectedIndex]
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
}

internal suspend fun LazyListState.nudgeItemIntoViewport(index: Int) {
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

internal enum class ScrollTarget {
    Visible,
    AlignTop,
    AlignBottom,
}

internal fun measureClipboardListItemHeight(
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

    val contentWidthPx = (viewportWidthPx - outerHorizontalPaddingPx - innerHorizontalPaddingPx)
        .coerceAtLeast(1)
    val textMaxWidthPx = (contentWidthPx - spacerWidthPx - timeLayout.size.width)
        .coerceAtLeast(1)

    val textLayout = textMeasurer.measure(
        text = item.text,
        style = itemTextStyle,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = textMaxWidthPx),
    )

    return max(textLayout.size.height, timeLayout.size.height) + innerVerticalPaddingPx
}
