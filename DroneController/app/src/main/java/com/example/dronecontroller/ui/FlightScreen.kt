package com.example.dronecontroller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dronecontroller.data.TelemetryState
import com.example.dronecontroller.ui.components.Joystick
import com.example.dronecontroller.ui.components.SnapshotController
import com.example.dronecontroller.ui.components.VideoFeed
import com.example.dronecontroller.video.VideoStream
import java.util.Locale


/**
 * Ground-station HUD: status capsule, compass, full-bleed video, icon dock,
 * camera/gimbal panel and the two flight sticks.
 */
@Composable
fun FlightScreen(t: TelemetryState, busy: Boolean, video: VideoStream?, vm: FlightViewModel) {
    val snapshot = remember { SnapshotController() }
    val zoom by vm.zoom.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        if (maxWidth > maxHeight) {
            // Short windows (Samsung pop-up view): drop the compass and camera panel.
            val compact = maxHeight < 560.dp
            LandscapeHud(t, busy, video, vm, zoom, snapshot, compact,
                stick = (maxHeight * 0.26f).coerceIn(100.dp, 180.dp))
        } else {
            PortraitHud(t, busy, video, vm, zoom, snapshot,
                stick = (maxWidth * 0.26f).coerceIn(110.dp, 200.dp))
        }
    }
}

@Composable
private fun LandscapeHud(
    t: TelemetryState, busy: Boolean, video: VideoStream?, vm: FlightViewModel,
    zoom: Float, snapshot: SnapshotController, compact: Boolean, stick: Dp,
) {
    Box(Modifier.fillMaxSize()) {
        Video(video, zoom, snapshot, Modifier.fillMaxSize())

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            BackPill(vm::disconnect)
            Spacer(Modifier.weight(1f))
            GpsPill(t)
        }
        StatusCapsule(t, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

        if (!compact) Compass(t.headingDeg, Modifier.padding(start = 16.dp, top = 84.dp).size(120.dp))

        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!compact) CameraPanel(t, vm, zoom, snapshot)
            Stick("PITCH · ROLL", stick, vm::setRightStick)
        }
        Stick("THROTTLE · YAW", stick, vm::setLeftStick, Modifier.align(Alignment.BottomStart).padding(16.dp))

        Dock(t, busy, vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
    }
}

@Composable
private fun PortraitHud(
    t: TelemetryState, busy: Boolean, video: VideoStream?, vm: FlightViewModel,
    zoom: Float, snapshot: SnapshotController, stick: Dp,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackPill(vm::disconnect)
            Spacer(Modifier.weight(1f))
            GpsPill(t)
        }
        Spacer(Modifier.height(12.dp))
        StatusCapsule(t)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)).border(1.dp, Edge, RoundedCornerShape(16.dp))
        ) {
            Video(video, zoom, snapshot, Modifier.fillMaxSize())
            Compass(t.headingDeg, Modifier.padding(12.dp).size(96.dp))
        }
        Spacer(Modifier.height(16.dp))
        Dock(t, busy, vm)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Stick("THROTTLE · YAW", stick, vm::setLeftStick)
            Spacer(Modifier.weight(1f))
            CameraPanel(t, vm, zoom, snapshot)
            Spacer(Modifier.weight(1f))
            Stick("PITCH · ROLL", stick, vm::setRightStick)
        }
    }
}

@Composable
private fun Video(video: VideoStream?, zoom: Float, snapshot: SnapshotController, modifier: Modifier) {
    Box(modifier.background(Bg)) {
        if (video != null) VideoFeed(video, Modifier.fillMaxSize(), zoom, snapshot)
        else Text("Video disabled", color = TextLo, modifier = Modifier.align(Alignment.Center))
    }
}

// ---- Top bar ---------------------------------------------------------------

@Composable
private fun BackPill(onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).glass(CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextHi, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Back", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GpsPill(t: TelemetryState) {
    val good = t.gpsFix.contains("3D") || t.gpsFix.contains("RTK")
    Row(
        Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SatelliteAlt, null, tint = if (good) Cyan else Amber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("${t.satellites}", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(8.dp).background(if (t.healthOk) Green else Amber, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (t.healthOk) "READY" else "NOT READY", color = if (t.healthOk) Green else Amber,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** [mode · armed] [raised speed box] [battery · altitude · link]. Equal halves keep the box centered. */
@Composable
private fun StatusCapsule(t: TelemetryState, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier.padding(top = 8.dp).height(46.dp).glass(CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.width(270.dp).padding(start = 18.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(modeLabel(t.flightMode), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp, maxLines = 1)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Filled.PowerSettingsNew, null, tint = if (t.armed) Red else TextLo, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (t.armed) "ARMED" else "DISARMED", color = if (t.armed) Red else TextHi,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(120.dp))
            Row(Modifier.width(270.dp).padding(start = 16.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BatteryGauge(t.batteryPct)
                Divider()
                Icon(Icons.Filled.Terrain, null, tint = Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(fmt1(t.relAltitudeM), color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
                Text(" m", color = TextLo, fontSize = 12.sp)
                Divider()
                Icon(if (t.connected) Icons.Filled.Link else Icons.Filled.LinkOff, null,
                    tint = if (t.connected) Green else Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (t.connected) "LINK" else "NO LINK", color = if (t.connected) Green else Red,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Column(
            Modifier.width(120.dp)
                .background(Color(0xFF071015), RoundedCornerShape(14.dp))
                .border(1.dp, Edge, RoundedCornerShape(14.dp))
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(fmt1(t.groundSpeedMs), color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
            Text("M/S", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun BatteryGauge(pct: Float) {
    val color = when {
        pct.isNaN() -> TextLo
        pct < 20f -> Red
        pct < 40f -> Amber
        else -> Green
    }
    Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
        val tip = 2.5.dp.toPx()
        val body = Size(size.width - tip, size.height)
        drawRoundRect(color, size = body, style = Stroke(1.5.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
        drawRect(color, Offset(body.width, size.height * 0.3f), Size(tip, size.height * 0.4f))
        val f = if (pct.isNaN()) 0f else (pct / 100f).coerceIn(0f, 1f)
        val inset = 3.dp.toPx()
        drawRect(color, Offset(inset, inset), Size((body.width - inset * 2) * f, body.height - inset * 2))
    }
    Spacer(Modifier.width(6.dp))
    Text(if (pct.isNaN()) "--%" else "${pct.toInt()}%", color = color, fontSize = 13.sp,
        fontWeight = FontWeight.Bold, fontFamily = Mono)
}

@Composable
private fun Divider() {
    Spacer(Modifier.width(10.dp))
    Box(Modifier.width(1.dp).height(18.dp).background(Edge))
    Spacer(Modifier.width(10.dp))
}

// ---- Compass ---------------------------------------------------------------

@Composable
private fun Compass(headingDeg: Double, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(Glass, r)
        drawCircle(Edge, r, style = Stroke(1.dp.toPx()))
        drawCircle(Edge, r * 0.80f, style = Stroke(1.dp.toPx()))
        for (i in 0 until 12) rotate(i * 30f) {
            drawLine(TextLo.copy(alpha = 0.5f), Offset(center.x, center.y - r * 0.80f),
                Offset(center.x, center.y - r * (if (i % 3 == 0) 0.70f else 0.75f)), 1.dp.toPx())
        }
        val style = TextStyle(color = TextLo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        listOf("N" to Offset(0f, -1f), "E" to Offset(1f, 0f), "S" to Offset(0f, 1f), "W" to Offset(-1f, 0f))
            .forEach { (label, dir) ->
                val m = measurer.measure(label, if (label == "N") style.copy(color = TextHi) else style)
                val p = center + dir * (r * 0.56f)
                drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
            }
        rotate(headingDeg.toFloat()) {
            val a = r * 0.30f
            val path = Path().apply {
                moveTo(center.x, center.y - a)
                lineTo(center.x + a * 0.55f, center.y + a * 0.6f)
                lineTo(center.x, center.y + a * 0.25f)
                lineTo(center.x - a * 0.55f, center.y + a * 0.6f)
                close()
            }
            drawPath(path, Cyan)
        }
        val hdg = measurer.measure("%03d°".format(Locale.US, ((headingDeg.toInt() % 360) + 360) % 360),
            TextStyle(color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = Mono))
        drawText(hdg, topLeft = Offset(center.x - hdg.size.width / 2f, center.y + r * 0.92f - hdg.size.height))
    }
}

// ---- Dock ------------------------------------------------------------------

/** HOLD | RTL · LAND · MANUAL · ARM/DISARM | TAKE OFF */
@Composable
private fun Dock(t: TelemetryState, busy: Boolean, vm: FlightViewModel, modifier: Modifier = Modifier) {
    val ok = t.connected && !busy
    Row(
        modifier.glass(RoundedCornerShape(36.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DockButton(Icons.Filled.Pause, "HOLD", ok && t.inAir, Red, filled = true, onClick = vm::hold)
        DockDivider()
        DockButton(Icons.AutoMirrored.Filled.Undo, "RTL", ok && t.inAir, onClick = vm::rtl)
        DockButton(Icons.Filled.FlightLand, "LAND", ok && t.inAir, onClick = vm::land)
        DockButton(Icons.Filled.SportsEsports, "MANUAL", ok && t.armed, Cyan,
            active = t.flightMode == "POSCTL", onClick = vm::manual)
        DockButton(Icons.Filled.PowerSettingsNew, if (t.armed) "DISARM" else "ARM",
            ok && !t.inAir, if (t.armed) Red else Green, active = true,
            onClick = if (t.armed) vm::disarm else vm::arm)
        DockDivider()
        DockButton(Icons.Filled.FlightTakeoff, "TAKE OFF", ok && t.armed && !t.inAir, Teal, filled = true,
            onClick = vm::takeoff)
    }
}

@Composable
private fun DockDivider() = Box(Modifier.width(1.dp).height(36.dp).background(Edge))

@Composable
private fun DockButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    accent: Color = TextHi,
    filled: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Color(0xFF0F151B)
        filled -> accent
        else -> ButtonBg
    }
    val fg = when {
        !enabled -> TextOff
        filled -> Color.White
        active -> accent
        else -> TextHi
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, if (active && enabled && !filled) accent else Edge, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) TextLo else TextOff, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ---- Camera / gimbal panel -------------------------------------------------

@Composable
private fun CameraPanel(t: TelemetryState, vm: FlightViewModel, zoom: Float, snapshot: SnapshotController) {
    val context = LocalContext.current
    val hasGimbal = t.connected && t.gimbalId != null
    Column(
        Modifier.width(188.dp).glass(RoundedCornerShape(20.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Segment(Icons.Filled.Videocam, "Forward", t.gimbalPitchDeg > -45f, hasGimbal) { vm.gimbalPreset(0f) }
            Segment(Icons.Filled.VerticalAlignBottom, "Down", t.gimbalPitchDeg <= -45f, hasGimbal) { vm.gimbalPreset(-90f) }
        }
        PanelDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ZoomOut, "Zoom out", tint = TextLo,
                modifier = Modifier.size(18.dp).clickable { vm.setZoom(zoom - 0.5f) })
            ZoomSlider(zoom, vm::setZoom, Modifier.weight(1f).padding(horizontal = 6.dp))
            Icon(Icons.Filled.ZoomIn, "Zoom in", tint = TextLo,
                modifier = Modifier.size(18.dp).clickable { vm.setZoom(zoom + 0.5f) })
            Spacer(Modifier.width(6.dp))
            Text(fmt1(zoom), color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        }
        PanelDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).border(1.dp, Edge, CircleShape)
                    .clickable { if (hasGimbal) vm.gimbalPreset(0f, 0f); vm.setZoom(1f) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.GpsFixed, "Recenter", tint = TextHi, modifier = Modifier.size(18.dp)) }
            // Shutter: saves the current frame to Pictures/DroneController
            Box(
                Modifier.size(50.dp).border(2.dp, TextHi, CircleShape).padding(4.dp)
                    .clip(CircleShape).background(Color.White)
                    .clickable { snapshot.grab?.invoke()?.let { vm.saveSnapshot(context.applicationContext, it) } }
            )
        }
        PanelDivider()
        // Gimbal pad: up/down = tilt, left/right = pan (rate control)
        Joystick(size = 118.dp, color = Teal, knobColor = Color(0xFF5B6670), chevrons = true,
            enabled = hasGimbal, onMove = vm::setGimbalPad)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                !hasGimbal -> "no gimbal"
                // Pan is undefined when looking straight down
                t.gimbalPitchDeg < -80f -> "tilt %+d°".format(Locale.US, t.gimbalPitchDeg.toInt())
                else -> "tilt %+d°  pan %+d°".format(Locale.US, t.gimbalPitchDeg.toInt(), t.gimbalYawDeg.toInt())
            },
            color = TextLo, fontSize = 10.sp, fontFamily = Mono,
        )
    }
}

/** Thin track + small round thumb, matching the HUD rather than the default M3 slider. */
@Composable
private fun ZoomSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val range = 1f..4f
    BoxWithConstraints(
        modifier.height(24.dp).pointerInput(Unit) {
            fun set(x: Float) = onChange(range.start + (x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start))
            detectTapGestures { set(it.x) }
        }.pointerInput(Unit) {
            fun set(x: Float) = onChange(range.start + (x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start))
            detectHorizontalDragGestures { change, _ -> change.consume(); set(change.position.x) }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        val f = (value - range.start) / (range.endInclusive - range.start)
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF34424F), CircleShape))
        Box(Modifier.width(maxWidth * f).height(2.dp).background(Cyan, CircleShape))
        Box(Modifier.offset(x = (maxWidth - 12.dp) * f).size(12.dp).background(Cyan, CircleShape))
    }
}

@Composable
private fun Segment(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val on = selected && enabled
    Row(
        Modifier
            .clip(shape)
            .background(if (on) Color(0xFF0B2A30) else ButtonBg)
            .border(1.dp, if (on) Teal else Edge, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (!enabled) TextOff else if (on) Cyan else TextHi
        Icon(icon, null, tint = c, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PanelDivider() = Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Edge))

// ---- Sticks ----------------------------------------------------------------

@Composable
private fun Stick(label: String, size: Dp, onMove: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Joystick(size = size, color = Cyan, onMove = onMove)
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextLo, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.glass(CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

// ---- helpers ---------------------------------------------------------------


private fun modeLabel(mode: String) = when (mode) {
    "POSCTL" -> "POSITION"
    "ALTCTL" -> "ALTITUDE"
    "RETURN_TO_LAUNCH" -> "RETURN"
    "-" -> "—"
    else -> mode.replace('_', ' ')
}

/** One decimal, without the "-0.0" that sensor noise produces around zero. */
private fun fmt1(v: Float) = String.format(Locale.US, "%.1f", if (kotlin.math.abs(v) < 0.05f) 0f else v)
