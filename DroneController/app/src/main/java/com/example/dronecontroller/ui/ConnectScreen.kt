package com.example.dronecontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.example.dronecontroller.util.ConnectionPrefs
import com.example.dronecontroller.util.isEmulator

/** 10.0.2.2 is the host PC as seen from the Android emulator. On a phone, use the PC's Wi-Fi IP. */
private val defaultHost get() = if (isEmulator) "10.0.2.2" else "192.168.1.187"
fun defaultAddress() = "udpout://$defaultHost:18570"
fun defaultVideoUrl() = "rtsp://$defaultHost:8564/drone"

@Composable
fun ConnectScreen(connecting: Boolean, onConnect: (address: String, videoUrl: String) -> Unit) {
    val prefs = ConnectionPrefs(LocalContext.current)
    var address by rememberSaveable { mutableStateOf(prefs.address(defaultAddress())) }
    var videoUrl by rememberSaveable { mutableStateOf(prefs.videoUrl(defaultVideoUrl())) }

    // Scrolls when the window is short (Samsung pop-up view, split screen, keyboard open).
    Box(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(460.dp).glass(RoundedCornerShape(24.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).background(Color(0xFF0B2A30), CircleShape).border(1.dp, Teal, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.FlightTakeoff, null, tint = Cyan, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("PX4 Ground Station", color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Connect to the drone and its camera", color = TextLo, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))

            Field("MAVLink connection", address, { address = it }, Icons.Filled.Link)
            Spacer(Modifier.height(14.dp))
            Field("Camera RTSP (empty = no video)", videoUrl, { videoUrl = it }, Icons.Filled.Videocam)
            Spacer(Modifier.height(24.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (connecting) ButtonBg else Teal)
                    .clickable(enabled = !connecting) {
                        prefs.save(address.trim(), videoUrl.trim())
                        onConnect(address.trim(), videoUrl.trim())
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Waiting for drone…", color = TextHi, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Connect", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Hint("PX4 SITL", "udpout://<PC IP>:18570")
                Hint("Camera", "rtsp://<PC IP>:8564/drone")
                Hint("Emulator", "use 10.0.2.2 as the PC IP")
                Hint("USB tether", "PC's USB address, e.g. 172.29.x.x")
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, icon: ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextHi, unfocusedTextColor = TextHi,
            focusedBorderColor = Cyan, unfocusedBorderColor = Edge,
            focusedLabelColor = Cyan, unfocusedLabelColor = TextLo,
            focusedLeadingIconColor = Cyan, unfocusedLeadingIconColor = TextLo,
            cursorColor = Cyan,
            focusedContainerColor = Color(0xFF071015), unfocusedContainerColor = Color(0xFF071015),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(label: String, value: String) {
    Row {
        Text(label, color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
        Text(value, color = TextLo, fontSize = 12.sp, fontFamily = Mono)
    }
}
