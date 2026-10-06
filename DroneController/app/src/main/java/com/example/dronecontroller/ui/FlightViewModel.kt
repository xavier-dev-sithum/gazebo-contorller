package com.example.dronecontroller.ui

import android.content.Context
import android.graphics.Bitmap
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dronecontroller.data.DroneRepository
import com.example.dronecontroller.data.Sticks
import com.example.dronecontroller.data.TelemetryState
import com.example.dronecontroller.util.saveSnapshot as saveSnapshotToGallery
import com.example.dronecontroller.video.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LinkStatus { DISCONNECTED, CONNECTING, CONNECTED }

class FlightViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DroneRepository()

    val telemetry: StateFlow<TelemetryState> = repo.state

    private val _link = MutableStateFlow(LinkStatus.DISCONNECTED)
    val link = _link.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    /** Camera stream; owned here so it survives rotation and layout changes. Null = no video. */
    private val _video = MutableStateFlow<VideoStream?>(null)
    val video = _video.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    // Written by the joystick composables, read by the 25 Hz loop.
    private val sticks = MutableStateFlow(Sticks())
    private var stickJob: Job? = null

    // Gimbal: the pad gives a rate, integrated here into a target angle.
    private var gimbalPad = 0f to 0f
    private var gimbalPitch = 0f
    private var gimbalYaw = 0f
    @Volatile private var gimbalBusy = false

    /** Digital zoom applied to the video in the app (the sim camera has no optical zoom). */
    private val _zoom = MutableStateFlow(1f)
    val zoom = _zoom.asStateFlow()

    fun connect(address: String, videoUrl: String) {
        _video.value?.release()
        _video.value = videoUrl.takeIf { it.isNotEmpty() }?.let { VideoStream(getApplication(), it) }
        _link.value = LinkStatus.CONNECTING
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.connect(address) }
                .onSuccess {
                    _link.value = LinkStatus.CONNECTED
                    startStickStream()
                }
                .onFailure {
                    _link.value = LinkStatus.DISCONNECTED
                    _message.value = "Connect failed: ${it.message}"
                }
        }
    }

    fun disconnect() {
        stickJob?.cancel()
        _video.value?.release()
        _video.value = null
        viewModelScope.launch(Dispatchers.IO) { repo.disconnect() }
        _link.value = LinkStatus.DISCONNECTED
    }

    /**
     * PX4 treats MANUAL_CONTROL as a virtual RC. Stream it continuously (25 Hz) for as long as we are
     * connected so PX4 never sees "RC lost". With centered sticks + throttle 0.5 the drone holds.
     */
    private fun startStickStream() {
        stickJob?.cancel()
        stickJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                repo.sendSticks(sticks.value)
                stepGimbal(STICK_PERIOD_MS / 1000f)
                delay(STICK_PERIOD_MS)
            }
        }
    }

    // Expo: fine control near center, full authority at the edge. Throttle stays linear so
    // "center = hold altitude" in Position mode is exact.
    fun setLeftStick(x: Float, y: Float) =
        sticks.update { it.copy(yaw = shape(x), throttle = (deadzone(y) + 1f) / 2f) }
    fun setRightStick(x: Float, y: Float) = sticks.update { it.copy(roll = shape(x), pitch = shape(y)) }

    fun setGimbalPad(x: Float, y: Float) { gimbalPad = x to y }

    fun gimbalPreset(pitchDeg: Float, yawDeg: Float = 0f) {
        gimbalPitch = pitchDeg
        gimbalYaw = yawDeg
        sendGimbal()
    }

    fun saveSnapshot(context: Context, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            // Aliased import: an unqualified saveSnapshot() here would call this method recursively.
            runCatching { saveSnapshotToGallery(context, bitmap) }
                .onSuccess { _message.value = "Saved Pictures/DroneController/$it" }
                .onFailure { _message.value = "Snapshot failed: ${it.message}" }
        }
    }

    fun setZoom(z: Float) { _zoom.value = z.coerceIn(1f, 4f) }

    private fun stepGimbal(dt: Float) {
        val (x, y) = gimbalPad
        if (kotlin.math.abs(x) < 0.05f && kotlin.math.abs(y) < 0.05f) return
        gimbalPitch = (gimbalPitch + y * GIMBAL_RATE_DPS * dt).coerceIn(-90f, 30f)
        gimbalYaw = (gimbalYaw + x * GIMBAL_RATE_DPS * dt).coerceIn(-170f, 170f)
        sendGimbal()
    }

    private fun sendGimbal() {
        if (gimbalBusy || telemetry.value.gimbalId == null) return
        gimbalBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.setGimbal(gimbalPitch, gimbalYaw) }
                .onFailure { _message.value = "Gimbal: ${it.message}" }
            gimbalBusy = false
        }
    }

    fun arm() = command("Arm") { repo.arm() }
    fun disarm() = command("Disarm") { repo.disarm() }
    fun takeoff() = command("Takeoff") { repo.takeoff(5f) }
    fun land() = command("Land") { repo.land() }
    fun rtl() = command("RTL") { repo.rtl() }
    fun hold() = command("Hold") { repo.hold() }
    fun manual() = command("Position mode") { repo.startPositionControl() }

    fun clearMessage() { _message.value = null }

    private fun command(name: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            runCatching { block() }
                .onSuccess { _message.value = "$name OK" }
                .onFailure { _message.value = "$name failed: ${it.message ?: it}" }
            _busy.value = false
        }
    }

    private companion object {
        const val GIMBAL_RATE_DPS = 60f
        const val STICK_PERIOD_MS = 40L // 25 Hz
        const val DEADZONE = 0.05f
        const val EXPO = 0.5f

        fun deadzone(v: Float): Float {
            val a = kotlin.math.abs(v)
            if (a < DEADZONE) return 0f
            return kotlin.math.sign(v) * ((a - DEADZONE) / (1f - DEADZONE)).coerceAtMost(1f)
        }

        fun shape(v: Float): Float {
            val d = deadzone(v)
            return (1f - EXPO) * d + EXPO * d * d * d
        }
    }

    override fun onCleared() {
        stickJob?.cancel()
        _video.value?.release()
        repo.disconnect()
    }
}
