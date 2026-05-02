package com.example.macclipboardmanager.ui

import androidx.compose.ui.graphics.Color
import com.example.macclipboardmanager.theme.AppThemePreference

internal data class ClipboardThemeSpec(
    val preference: AppThemePreference,
    val panelFill: Color,
    val panelBorder: Color,
    val panelShadowAmbient: Color,
    val panelShadowSpot: Color,
    val topLightStart: Color,
    val topLightEnd: Color,
    val rim: Color,
    val divider: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val placeholderText: Color,
    val selectedRowFill: Color,
    val selectedAccent: Color,
    val favoriteAccent: Color,
    val inactiveIcon: Color,
    val emptyStateText: Color,
    val toastFill: Color,
    val toastText: Color,
    val toastAccent: Color,
) {
    val isFrostedGlass: Boolean = preference == AppThemePreference.FrostedGlass
}

internal fun clipboardThemeSpec(preference: AppThemePreference): ClipboardThemeSpec =
    when (preference) {
        AppThemePreference.FrostedGlass -> ClipboardThemeSpec(
            preference = preference,
            panelFill = Color(0x18F8FAFC),
            panelBorder = Color(0x28FFFFFF),
            panelShadowAmbient = Color(0x0D000000),
            panelShadowSpot = Color(0x14000000),
            topLightStart = Color(0x38FFFFFF),
            topLightEnd = Color(0x00FFFFFF),
            rim = Color.Transparent,
            divider = Color(0x14334155),
            primaryText = Color(0xFF0F172A),
            secondaryText = Color(0xFF64748B),
            placeholderText = Color(0x7A64748B),
            selectedRowFill = Color(0x22FFFFFF),
            selectedAccent = Color(0xCCB56B73),
            favoriteAccent = Color(0xFFE0AA3E),
            inactiveIcon = Color(0xFF94A3B8),
            emptyStateText = Color(0xFF475569),
            toastFill = Color(0xE6151720),
            toastText = Color(0xFFF8FAFC),
            toastAccent = Color(0xFFF97316),
        )

        AppThemePreference.Solid -> ClipboardThemeSpec(
            preference = preference,
            panelFill = Color(0xFFF6F3EC),
            panelBorder = Color(0x120F172A),
            panelShadowAmbient = Color(0x14000000),
            panelShadowSpot = Color(0x1A000000),
            topLightStart = Color.Transparent,
            topLightEnd = Color.Transparent,
            rim = Color.Transparent,
            divider = Color(0x140F172A),
            primaryText = Color(0xFF111827),
            secondaryText = Color(0xFF6B7280),
            placeholderText = Color(0xFF9CA3AF),
            selectedRowFill = Color(0xFFF6EBE8),
            selectedAccent = Color(0xFFCE8381),
            favoriteAccent = Color(0xFFE6B84A),
            inactiveIcon = Color(0xFFB8B4AC),
            emptyStateText = Color(0xFF111827),
            toastFill = Color(0xE6151720),
            toastText = Color(0xFFF8FAFC),
            toastAccent = Color(0xFFF97316),
        )
    }

internal object ClipboardListItemLayout {
    const val TrailingActionAreaWidthDp = 142

    @Suppress("UNUSED_PARAMETER")
    fun trailingActionAreaWidthDp(
        isSelected: Boolean,
        isFavorite: Boolean,
        isPinned: Boolean,
    ): Int = TrailingActionAreaWidthDp
}
