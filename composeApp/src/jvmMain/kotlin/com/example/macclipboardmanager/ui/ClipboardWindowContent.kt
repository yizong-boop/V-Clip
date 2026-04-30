package com.example.macclipboardmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.macclipboardmanager.domain.clipboard.ClipboardItem
import com.example.macclipboardmanager.feature.main.MainUiState

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
private fun ClipboardSearchField(
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
