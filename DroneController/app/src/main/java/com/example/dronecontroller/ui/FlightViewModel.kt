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
            var out = Sticks()
            while (isActive) {
                out = slew(out, sticks.value, STICK_SLEW_PER_TICK)
                repo.sendSticks(out)
                stepGimbal(STICK_PERIOD_MS / 1000f)
                delay(STICK_PERIOD_MS)
            }
        }
    }

    // Linear on purpose: PX4 already applies a deadzone (MAN_DEADZONE) and expo (0.6) to the sticks
    // in Position mode. Shaping here as well made the sticks feel dead, then suddenly fast.
    fun setLeftStick(x: Float, y: Float) = sticks.update { it.copy(yaw = x, throttle = (y + 1f) / 2f) }
    fun setRightStick(x: Float, y: Float) = sticks.update { it.copy(roll = x, pitch = y) }

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
        /** Max change per tick: 0.27 -> full stick in ~150 ms, so touch/release never steps. */
        const val STICK_SLEW_PER_TICK = 0.27f

        fun slew(from: Sticks, to: Sticks, max: Float) = Sticks(
            pitch = from.pitch + (to.pitch - from.pitch).coerceIn(-max, max),
            roll = from.roll + (to.roll - from.roll).coerceIn(-max, max),
            throttle = from.throttle + (to.throttle - from.throttle).coerceIn(-max / 2, max / 2),
            yaw = from.yaw + (to.yaw - from.yaw).coerceIn(-max, max),
        )
    }

    override fun onCleared() {
        stickJob?.cancel()
        _video.value?.release()
        repo.disconnect()
    }
}
