package com.nankai.smartcane.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object SmartCaneColors {
    val Primary = Color(0xFF0F766E)
    val PrimaryBlue = Color(0xFF2563EB)
    val PrimaryPurple = Color(0xFF6D5DF6)
    val Background = Color(0xFFEEF6F7)
    val Surface = Color.White
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF64748B)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFDC2626)
    val BlindTop = Color(0xFF101A35)
    val BlindBottom = Color(0xFF0B1022)
    val BlindPanel = Color(0x1FFFFFFF)
}

object SmartCaneSpacing {
    val Page = 20.dp
    val Card = 18.dp
    val Gap = 12.dp
    val ButtonMin = 56.dp
}

object SmartCaneShapes {
    val PageCard = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(24.dp)
    val Button = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(999.dp)
}
