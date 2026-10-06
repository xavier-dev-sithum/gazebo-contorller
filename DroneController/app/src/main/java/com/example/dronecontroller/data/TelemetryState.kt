package com.example.dronecontroller.data

data class TelemetryState(
    val connected: Boolean = false,
    val armed: Boolean = false,
    val inAir: Boolean = false,
    val flightMode: String = "-",
    val relAltitudeM: Float = 0f,
    val groundSpeedMs: Float = 0f,
    val climbRateMs: Float = 0f,
    val headingDeg: Double = 0.0,
    val batteryPct: Float = Float.NaN,
    val satellites: Int = 0,
    val gpsFix: String = "-",
    val lat: Double = Double.NaN,
    val lon: Double = Double.NaN,
    val healthOk: Boolean = false,
    val gimbalId: Int? = null,      // null until PX4 reports a gimbal
    val gimbalPitchDeg: Float = 0f, // + up, - down
    val gimbalYawDeg: Float = 0f,   // relative to vehicle heading
)

/** Normalized stick values. pitch/roll/yaw in -1..1, throttle in 0..1 (0.5 = hold altitude). */
data class Sticks(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val throttle: Float = 0.5f,
    val yaw: Float = 0f,
)
