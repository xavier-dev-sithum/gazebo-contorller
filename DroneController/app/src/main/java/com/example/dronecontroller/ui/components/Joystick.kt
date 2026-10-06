package com.example.dronecontroller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Virtual joystick that springs back to center. Reports x (right +) and y (up +), both in -1..1.
 * Also used as the gimbal pad (grey knob + chevrons).
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    size: Dp = 170.dp,
    color: Color = Color(0xFF22C7E0),
    knobColor: Color = color,
    chevrons: Boolean = false,
    enabled: Boolean = true,
    onMove: (x: Float, y: Float) -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) } // offset from center, px

    Canvas(
        modifier
            .size(size)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val radius = this.size.width / 2f
                fun report() = onMove(knob.x / radius, -knob.y / radius)
                detectDragGestures(
                    onDragStart = { start ->
                        knob = (start - Offset(radius, radius)).clamp(radius); report()
                    },
                    onDragEnd = { knob = Offset.Zero; report() },
                    onDragCancel = { knob = Offset.Zero; report() },
                ) { change, drag ->
                    change.consume()
                    knob = (knob + drag).clamp(radius); report()
                }
            }
    ) {
        val r = this.size.minDimension / 2f
        val ring = if (enabled) color else Color(0xFF2A3540)
        // Dark base so the stick stays readable over bright video.
        drawCircle(Color(0xCC0A1015), r)
        drawCircle(ring.copy(alpha = 0.06f), r)
        drawCircle(ring.copy(alpha = 0.55f), r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.18f), r * 0.78f, style = Stroke(1.dp.toPx()))
        if (chevrons) {
            for (angle in listOf(0f, 90f, 180f, 270f)) rotate(angle) { chevron(r, ring) }
        } else {
            drawLine(ring.copy(alpha = 0.18f), Offset(center.x, r * 0.25f), Offset(center.x, this.size.height - r * 0.25f))
            drawLine(ring.copy(alpha = 0.18f), Offset(r * 0.25f, center.y), Offset(this.size.width - r * 0.25f, center.y))
        }
        val k = center + knob
        drawCircle(Color.Black.copy(alpha = 0.4f), r * 0.33f, k + Offset(0f, 2.dp.toPx()))
        drawCircle(if (enabled) knobColor else Color(0xFF39434D), r * 0.31f, k)
        drawCircle(Color.White.copy(alpha = 0.18f), r * 0.31f, k, style = Stroke(1.dp.toPx()))
    }
}

/** Small "^" near the top edge; rotated for the other directions. */
private fun DrawScope.chevron(r: Float, color: Color) {
    val w = r * 0.09f
    val y = center.y - r * 0.80f
    val p = Path().apply {
        moveTo(center.x - w, y + w * 0.6f)
        lineTo(center.x, y - w * 0.4f)
        lineTo(center.x + w, y + w * 0.6f)
    }
    drawPath(p, color.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun Offset.clamp(max: Float): Offset {
    val d = getDistance()
    return if (d <= max) this else this * (max / d)
}
