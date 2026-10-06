package com.example.dronecontroller.data

import android.util.Log
import io.mavsdk.System
import io.mavsdk.gimbal.Gimbal
import io.mavsdk.mavsdkserver.MavsdkServer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.rx2.await
import kotlin.math.hypot

/** Owns mavsdk_server + the MAVSDK System. All MAVLink code lives here. */
class DroneRepository {
    private var server: MavsdkServer? = null
    private var drone: System? = null
    private val disposables = CompositeDisposable()

    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state

    /**
     * [address] is a MAVSDK connection URL, e.g.
     *  - "udpout://10.0.2.2:18570"      (Android emulator -> PX4 SITL on the host)
     *  - "udpout://192.168.1.187:18570" (real phone -> PX4 SITL on the PC over Wi-Fi)
     *  - "udpin://0.0.0.0:14551"        (listen, e.g. behind mavlink-router)
     * Blocking: call from a background thread.
     */
    fun connect(address: String) {
        disconnect()
        val srv = MavsdkServer()
        val grpcPort = srv.run(address)
        require(grpcPort > 0) { "mavsdk_server failed to start for $address" }
        server = srv
        drone = System("127.0.0.1", grpcPort).also { subscribeTelemetry(it) }
    }

    private fun subscribeTelemetry(d: System) {
        fun <T : Any> io.reactivex.Flowable<T>.collect(block: (T) -> Unit) {
            disposables.add(
                subscribeOn(Schedulers.io()).subscribe(block) { Log.w(TAG, "telemetry stream error", it) }
            )
        }
        d.core.connectionState.collect { s -> _state.update { it.copy(connected = s.isConnected) } }
        d.telemetry.armed.collect { a -> _state.update { it.copy(armed = a) } }
        d.telemetry.inAir.collect { a -> _state.update { it.copy(inAir = a) } }
        d.telemetry.flightMode.collect { m -> _state.update { it.copy(flightMode = m.name) } }
        d.telemetry.heading.collect { h -> _state.update { it.copy(headingDeg = h.headingDeg) } }
        d.telemetry.healthAllOk.collect { ok -> _state.update { it.copy(healthOk = ok) } }
        d.telemetry.battery.collect { b -> _state.update { it.copy(batteryPct = b.remainingPercent) } }
        d.telemetry.gpsInfo.collect { g ->
            _state.update { it.copy(satellites = g.numSatellites, gpsFix = g.fixType.name) }
        }
        d.telemetry.velocityNed.collect { v ->
            _state.update {
                it.copy(groundSpeedMs = hypot(v.northMS, v.eastMS), climbRateMs = -v.downMS)
            }
        }
        d.gimbal.gimbalList.collect { list ->
            _state.update { it.copy(gimbalId = list.gimbals.firstOrNull()?.gimbalId) }
        }
        // PX4 hands gimbal control to its own modes (e.g. LAND) and then releases it.
        // Track whether we still hold it so the next command takes control again.
        d.gimbal.controlStatus.collect { c ->
            gimbalControlled = c.controlMode == Gimbal.ControlMode.PRIMARY
        }
        d.gimbal.attitude.collect { a ->
            _state.update {
                it.copy(gimbalPitchDeg = a.eulerAngleForward.pitchDeg, gimbalYawDeg = a.eulerAngleForward.yawDeg)
            }
        }
        d.telemetry.position.collect { p ->
            _state.update {
                it.copy(relAltitudeM = p.relativeAltitudeM, lat = p.latitudeDeg, lon = p.longitudeDeg)
            }
        }
    }

    private fun requireDrone() = drone ?: error("Not connected")

    suspend fun arm() = requireDrone().action.arm().await()
    suspend fun disarm() = requireDrone().action.disarm().await()
    suspend fun takeoff(altM: Float = 5f) {
        requireDrone().action.setTakeoffAltitude(altM).await()
        requireDrone().action.takeoff().await()
    }
    suspend fun land() = requireDrone().action.land().await()
    suspend fun rtl() = requireDrone().action.returnToLaunch().await()
    suspend fun hold() = requireDrone().action.hold().await()

    /** Switch to Position mode. PX4 must already be receiving MANUAL_CONTROL. */
    suspend fun startPositionControl() = requireDrone().manualControl.startPositionControl().await()

    @Volatile private var gimbalControlled = false

    /** Point the gimbal (degrees, yaw relative to the vehicle). Takes control on first use. */
    suspend fun setGimbal(pitchDeg: Float, yawDeg: Float) {
        val d = requireDrone()
        val id = _state.value.gimbalId ?: error("No gimbal")
        if (!gimbalControlled) {
            d.gimbal.takeControl(id, Gimbal.ControlMode.PRIMARY).await()
            gimbalControlled = true
        }
        d.gimbal.setAngles(id, 0f, pitchDeg, yawDeg, Gimbal.GimbalMode.YAW_FOLLOW, Gimbal.SendMode.ONCE).await()
    }

    /** Fire-and-forget MANUAL_CONTROL. x=pitch(fwd+), y=roll(right+), z=throttle(0..1), r=yaw(cw+). */
    fun sendSticks(s: Sticks) {
        val d = drone ?: return
        // Not added to `disposables`: at 25 Hz that set would grow for the whole flight.
        d.manualControl.setManualControlInput(s.pitch, s.roll, s.throttle, s.yaw)
            .subscribe({}, { Log.v(TAG, "manual input failed: ${it.message}") })
    }

    fun disconnect() {
        disposables.clear()
        drone?.dispose()
        drone = null
        gimbalControlled = false
        server?.let { it.stop(); it.destroy() }
        server = null
        _state.value = TelemetryState()
    }

    private companion object {
        const val TAG = "DroneRepository"
    }
}
