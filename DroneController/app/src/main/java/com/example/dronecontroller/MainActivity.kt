package com.example.dronecontroller

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dronecontroller.ui.Bg
import com.example.dronecontroller.ui.ConnectScreen
import com.example.dronecontroller.ui.FlightScreen
import com.example.dronecontroller.ui.FlightViewModel
import com.example.dronecontroller.ui.LinkStatus

/**
 * Single activity. Rotation and window resizing (split screen, Samsung pop-up view) are handled
 * in place (see android:configChanges) — Compose just re-lays out, and the MAVLink link and video
 * stream live in [FlightViewModel], so nothing reconnects.
 */
class MainActivity : ComponentActivity() {
    private val wifiLock by lazy {
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifi.createWifiLock(mode, "DroneController").apply { setReferenceCounted(false) }
    }

    // Keep Wi-Fi out of power-save while the app is in front (video + MAVLink stalls otherwise).
    override fun onResume() {
        super.onResume()
        wifiLock.acquire()
    }

    override fun onPause() {
        wifiLock.release()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val vm: FlightViewModel = viewModel()
                val link by vm.link.collectAsStateWithLifecycle()
                val telemetry by vm.telemetry.collectAsStateWithLifecycle()
                val busy by vm.busy.collectAsStateWithLifecycle()
                val video by vm.video.collectAsStateWithLifecycle()
                val message by vm.message.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(message) {
                    message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
                }

                val flying = link == LinkStatus.CONNECTED
                ImmersiveMode(enabled = flying)

                Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Bg) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (flying) FlightScreen(telemetry, busy, video, vm)
                        else ConnectScreen(link == LinkStatus.CONNECTING, vm::connect)
                    }
                }
            }
        }
    }
}

/** Hides the status/navigation bars while flying; a swipe from the edge shows them briefly. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as ComponentActivity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
