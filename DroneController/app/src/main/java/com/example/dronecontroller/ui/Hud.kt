package com.example.dronecontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Ground-station palette shared by the connect and flight screens. */
internal val Bg = Color(0xFF05080B)
internal val Glass = Color(0xE60A1015)
internal val Edge = Color(0xFF1C2731)
internal val TextHi = Color(0xFFE6EDF3)
internal val TextLo = Color(0xFF7D8B97)
internal val TextOff = Color(0xFF3A4652)
internal val Cyan = Color(0xFF22C7E0)
internal val Teal = Color(0xFF0FA3B1)
internal val Red = Color(0xFFE53935)
internal val Amber = Color(0xFFFFB300)
internal val Green = Color(0xFF43A047)
internal val ButtonBg = Color(0xFF1A232C)
internal val Mono = FontFamily.Monospace

/** Dark translucent panel with a hairline border. */
internal fun Modifier.glass(shape: Shape) = background(Glass, shape).border(1.dp, Edge, shape)
