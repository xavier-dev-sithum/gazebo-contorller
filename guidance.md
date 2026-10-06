# Proposal & Guidance: Drone Control Mobile App (Kotlin) for a PX4 + Gazebo Simulation

| Item | Value |
|---|---|
| Project | `gazebo-contorller` |
| Host OS | Ubuntu 24.04 LTS |
| Simulator | Gazebo Harmonic (`gz sim`) |
| Autopilot | PX4-Autopilot (SITL), v1.15+ / `main` |
| Model | `x500` quadcopter (add `x500_mono_cam` later for video) |
| Mobile app | Android, Kotlin, Jetpack Compose |
| Drone link | MAVLink v2 over UDP (Wi-Fi) through **MAVSDK-Java** |
| Date | 2026-10-05 |

---

## 1. Goal

Build a simulated drone with PX4 and Gazebo that a small Android app written in Kotlin can control.

The app must be able to:

1. Connect to the simulated drone over Wi-Fi.
2. Show live telemetry: connection, armed state, flight mode, altitude, battery, GPS position.
3. Send basic commands: **Arm, Takeoff, Land, Return-to-Launch (RTL), Hold**.
4. Fly the drone manually with **two virtual joysticks** (throttle/yaw and pitch/roll).
5. (Optional, phase 5) Show the live camera feed from the simulated drone.

Because this setup is simulation-first, the same app can later fly a real PX4 drone. Only the connection address changes, since the protocol (MAVLink) stays the same.

---

## 2. System Architecture

```
┌────────────────────────── Ubuntu 24.04 PC ──────────────────────────┐
│                                                                     │
│   ┌──────────────┐   gz-transport   ┌─────────────────────────┐     │
│   │ Gazebo       │ ◄──────────────► │ PX4 SITL (px4 binary)   │     │
│   │ Harmonic     │  sensors/motors  │ flight controller        │     │
│   │ (x500 model) │                  │ MAVLink UDP 14550/14540 │     │
│   └──────────────┘                  └───────────┬─────────────┘     │
│                                                 │ UDP (localhost)   │
│                                     ┌───────────▼─────────────┐     │
│                                     │ mavlink-router          │     │
│                                     │ (fans out MAVLink)      │     │
│                                     └──┬──────────────────┬───┘     │
│                                        │                  │         │
│                             QGroundControl (debug)        │         │
└────────────────────────────────────────┼──────────────────┼─────────┘
                                         │ UDP over Wi-Fi   │
                                         ▼                  │
                        ┌──────────────────────────────────┐│
                        │ Android phone (Kotlin app)       ││
                        │  ├─ UI: Jetpack Compose          ││
                        │  ├─ ViewModel + StateFlow        ││
                        │  ├─ DroneRepository              ││
                        │  └─ MAVSDK-Java + mavsdk_server  │◄┘
                        └──────────────────────────────────┘
```

### Why MAVLink + MAVSDK (and not ROS 2)?

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **MAVSDK-Java** (recommended) | Official PX4 SDK. High-level API (`arm()`, `takeoff()`, `manualControl`). Ships an Android `mavsdk_server`. | Large AAR (native binaries). RxJava API. | ✅ Best for a small app |
| Raw MAVLink library (e.g. `io.dronefleet.mavlink`) | Small, full control | You implement the command/ack protocol, heartbeats, and mode switching yourself | Good for learning, more work |
| ROS 2 + `rosbridge` WebSocket | Fits ROS-based stacks | Adds the ROS 2 + uXRCE-DDS layers, more latency, more parts that can break | Only if the project is ROS-centric |

**Decision:** MAVSDK-Java. A ROS 2 bridge can be added later without changing the app's UI layer.

---

## 3. Part A: Simulation Setup (PC)

### 3.1 Install PX4 + Gazebo Harmonic

```bash
cd ~
git clone https://github.com/PX4/PX4-Autopilot.git --recursive
cd PX4-Autopilot
bash ./Tools/setup/ubuntu.sh          # installs toolchain + Gazebo Harmonic on 24.04
# log out / reboot after first install
```

Build the simulation and run it:

```bash
cd ~/PX4-Autopilot
make px4_sitl gz_x500
```

After the Gazebo window opens with the quadcopter, you get a `pxh>` shell. Test it there:

```
pxh> commander arm
pxh> commander takeoff
pxh> commander land
```

Useful variants:

```bash
HEADLESS=1 make px4_sitl gz_x500           # no GUI (lighter, faster)
make px4_sitl gz_x500_mono_cam             # quad with a camera (for video phase)
PX4_GZ_WORLD=baylands make px4_sitl gz_x500 # different world
```

### 3.2 Install QGroundControl (for verification)

Download the QGroundControl AppImage from the official docs. When it runs on the same PC it connects automatically on UDP 14550. Before you trust the app, check here that the drone arms, takes off, and that telemetry looks correct.

### 3.3 Expose MAVLink to the phone

By default PX4 SITL only talks MAVLink to `localhost`. The phone is on a different machine, so the MAVLink traffic has to be forwarded to it.

**Option 1 (recommended): `mavlink-router`**

```bash
sudo apt install git meson ninja-build pkg-config gcc g++ systemd
git clone https://github.com/mavlink-router/mavlink-router.git --recursive
cd mavlink-router && meson setup build . && ninja -C build && sudo ninja -C build install
```

`~/mavlink-router.conf`:

```ini
[General]
TcpServerPort = 5760

# Input: PX4 SITL GCS link (PX4 sends to 14550 on localhost)
[UdpEndpoint px4]
Mode = Server
Address = 0.0.0.0
Port = 14550

# Output: Android phone (replace with the phone's IP)
[UdpEndpoint phone]
Mode = Normal
Address = 192.168.1.50
Port = 14551

# Output: QGroundControl on the same PC
[UdpEndpoint qgc]
Mode = Normal
Address = 127.0.0.1
Port = 14552
```

Run:

```bash
mavlink-routerd -c ~/mavlink-router.conf
```

Note: if 14550 is taken (QGC also binds it), close QGC or set QGC to listen on 14552 (*Application Settings → Comm Links*).

**Option 2 (quick test): extra MAVLink instance in the PX4 shell**

```
pxh> mavlink start -u 14560 -o 14551 -t 192.168.1.50 -r 4000000 -m onboard
```

This makes PX4 send MAVLink directly to the phone at `192.168.1.50:14551`. The command has to be run again on every SITL start. You can put it in `ROMFS/px4fmu_common/init.d-posix/px4-rc.mavlink` to make it permanent.

### 3.4 Network checklist

- PC and phone are on the **same Wi-Fi / subnet**.
- Firewall allows UDP: `sudo ufw allow 14550:14560/udp`
- PC IP: `ip -4 addr` · Phone IP: *Settings → About → Status*.
- **Android Emulator:** the host PC is reachable at `10.0.2.2`, but UDP *into* the emulator needs `adb emu redir add udp:14551:14551`. A real phone is easier.

### 3.5 PX4 parameters for app control (SITL)

Set these in QGC or with `pxh> param set ...`:

| Param | Value | Why |
|---|---|---|
| `COM_RC_IN_MODE` | `1` (MAVLink/joystick only) | No physical RC in the sim. The app's virtual sticks act as RC. |
| `NAV_RCL_ACT` | `0` or `2` (RTL) | Decide what happens when the app's stick input stops |
| `COM_RCL_EXCEPT` | `4` (Offboard) | Optional: ignore RC loss while in Offboard |
| `NAV_DLL_ACT` | `2` (RTL) | Data-link loss action. Keep it on for safety habits. |
| `COM_DISARM_PRFLT` | `10` | Auto-disarm after 10 s on the ground without takeoff |

---

## 4. Part B: Kotlin Android App

### 4.1 Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM: `ViewModel` + `StateFlow` |
| Async | Kotlin Coroutines (bridged from MAVSDK's RxJava) |
| Drone SDK | `io.mavsdk:mavsdk` + `io.mavsdk:mavsdk-server` |
| DI (optional) | Hilt |
| Min SDK | 26 · Target SDK latest |

### 4.2 Project structure

```
app/src/main/java/com/example/dronecontroller/
├── MainActivity.kt
├── data/
│   ├── DroneRepository.kt        # owns MAVSDK + mavsdk_server lifecycle
│   └── TelemetryState.kt         # data class for UI state
├── ui/
│   ├── ConnectScreen.kt          # enter port / connect button
│   ├── FlightScreen.kt           # telemetry HUD + buttons + joysticks
│   ├── components/Joystick.kt    # custom Compose virtual joystick
│   └── FlightViewModel.kt
└── util/RxExt.kt                 # Rx → Flow/suspend helpers
```

### 4.3 Gradle setup

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.mavsdk:mavsdk:<latest>")          // Java API
    implementation("io.mavsdk:mavsdk-server:<latest>")   // native server (.so) for Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<latest>")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:<latest>") // match MAVSDK's RxJava version
    implementation(platform("androidx.compose:compose-bom:<latest>"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<latest>")
}

android {
    packaging { jniLibs { useLegacyPackaging = true } }   // mavsdk_server native libs
}
```

> Look up the current versions on Maven Central / the `mavlink/MAVSDK-Java` GitHub releases. Check which RxJava version MAVSDK-Java uses (`io.reactivex` = Rx2, `io.reactivex.rxjava3` = Rx3) and pick the matching `kotlinx-coroutines-rxN` artifact.

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

### 4.4 Core code sketch: `DroneRepository`

```kotlin
class DroneRepository {
    private var server: MavsdkServer? = null
    private var drone: System? = null
    private val disposables = CompositeDisposable()

    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state

    /** Listen for PX4 on the phone's UDP port (what mavlink-router sends to). */
    fun connect(udpPort: Int = 14551) {
        val srv = MavsdkServer()
        // MAVSDK v2: "udp://:14551"   |   MAVSDK v3+: "udpin://0.0.0.0:14551"
        val grpcPort = srv.run("udpin://0.0.0.0:$udpPort")
        server = srv
        drone = System("127.0.0.1", grpcPort).also { subscribeTelemetry(it) }
    }

    private fun subscribeTelemetry(d: System) {
        disposables += d.core.connectionState
            .subscribe { s -> _state.update { it.copy(connected = s.isConnected) } }
        disposables += d.telemetry.armed
            .subscribe { a -> _state.update { it.copy(armed = a) } }
        disposables += d.telemetry.flightMode
            .subscribe { m -> _state.update { it.copy(flightMode = m.name) } }
        disposables += d.telemetry.position
            .subscribe { p -> _state.update { it.copy(
                relAltitude = p.relativeAltitudeM, lat = p.latitudeDeg, lon = p.longitudeDeg) } }
        disposables += d.telemetry.battery
            .subscribe { b -> _state.update { it.copy(batteryPct = b.remainingPercent) } }
    }

    // ---- Commands (Completable → suspend via kotlinx-coroutines-rx) ----
    suspend fun arm()     = drone!!.action.arm().await()
    suspend fun takeoff(altM: Float = 5f) {
        drone!!.action.setTakeoffAltitude(altM).await()
        drone!!.action.takeoff().await()
    }
    suspend fun land()    = drone!!.action.land().await()
    suspend fun rtl()     = drone!!.action.returnToLaunch().await()
    suspend fun hold()    = drone!!.action.hold().await()

    // ---- Manual flight (virtual joysticks) ----
    // x = pitch (fwd +), y = roll (right +), z = throttle (0..1), r = yaw (cw +); range -1..1
    fun sendSticks(x: Float, y: Float, z: Float, r: Float) {
        drone?.manualControl?.setManualControlInput(x, y, z, r)?.subscribe({}, {})
    }
    suspend fun startPositionControl() = drone!!.manualControl.startPositionControl().await()

    fun disconnect() {
        disposables.clear(); drone?.dispose(); server?.stop(); server?.destroy()
    }
}
```

### 4.5 Manual control loop (important)

PX4 needs stick input **continuously**, at roughly 10–50 Hz. If the input stops, PX4 treats it as RC loss and runs `NAV_RCL_ACT`. Run the loop from the ViewModel:

```kotlin
private var stickJob: Job? = null

fun startManual() {
    stickJob = viewModelScope.launch(Dispatchers.Default) {
        // 1. send neutral sticks for a moment so PX4 sees "RC present"
        repeat(10) { repo.sendSticks(0f, 0f, 0.5f, 0f); delay(50) }
        // 2. switch to Position mode (holds altitude/position when sticks centered)
        repo.startPositionControl()
        // 3. stream stick values at 20 Hz
        while (isActive) {
            val s = sticks.value          // updated by the Joystick composables
            repo.sendSticks(s.pitch, s.roll, s.throttle, s.yaw)
            delay(50)
        }
    }
}
fun stopManual() { stickJob?.cancel() }
```

Notes:

- In **Position** mode a throttle of `0.5` means "hold altitude". Make the throttle stick spring back to the middle.
- **Alternative:** use the `offboard` plugin with `VelocityBodyYawspeed` for velocity-based control. This also needs a continuous stream, and you must send a setpoint *before* calling `offboard.start()`.

### 4.6 UI layout (FlightScreen)

```
┌──────────────────────────────────────────────┐
│ ● Connected   ARMED   POSCTL   🔋 87%   ALT 5.2m │  ← status bar
├──────────────────────────────────────────────┤
│                                              │
│        (video feed / map placeholder)        │
│                                              │
├──────────────────────────────────────────────┤
│ [ARM] [TAKEOFF] [LAND] [RTL] [HOLD] [MANUAL] │
│                                              │
│    ( ◎ )                          ( ◎ )      │
│  throttle/yaw                   pitch/roll   │
└──────────────────────────────────────────────┘
```

- Lock the screen to landscape. Keep the screen on with `FLAG_KEEP_SCREEN_ON`.
- Build the joystick as a Compose `Canvas` with `pointerInput { detectDragGestures }` and normalize its output to −1..1.
- Disable buttons that don't fit the current state (e.g. TAKEOFF while disarmed).
- Show command errors (`ActionException.result`) in a Snackbar. Common causes: "not ready to arm", "no GPS".

---

## 5. Part C (Optional): Live Camera Video

1. Launch `make px4_sitl gz_x500_mono_cam`.
2. List camera topics with `gz topic -l | grep image`.
3. Encode the stream and send it out as RTSP. One option is a GStreamer pipeline (gz image → `x264enc` → `rtspclientsink`) that pushes into **MediaMTX**, which is already installed in `~/tools/mediamtx`.
4. On Android, play `rtsp://<PC_IP>:8554/drone` with **Media3 ExoPlayer** (`media3-exoplayer-rtsp`). Configure low latency.

Leave this phase until control works reliably.

---

## 6. Implementation Plan / Milestones

| Phase | Deliverable | Done when… | Est. |
|---|---|---|---|
| **1. Sim setup** | PX4 + Gazebo Harmonic running `gz_x500` | `commander takeoff` works in `pxh>` | 1 day |
| **2. Ground link** | QGC + mavlink-router | QGC shows telemetry, phone IP receives UDP (check with a UDP test app) | 0.5 day |
| **3. App skeleton** | Compose app, MAVSDK connected | App shows "Connected" + live altitude/battery | 1–2 days |
| **4. Commands** | Arm / Takeoff / Land / RTL / Hold | Full takeoff→land cycle from the phone | 1 day |
| **5. Manual flight** | Virtual joysticks + 20 Hz loop | Fly a square in Position mode from the phone | 2 days |
| **6. Polish & safety** | Error handling, state-aware buttons, reconnect | App recovers after Wi-Fi drop, failsafe (RTL) triggers on link loss | 1–2 days |
| **7. Video (optional)** | RTSP feed in app | < 500 ms visible latency | 2 days |

**Total:** ~1.5–2 weeks for one developer (phases 1–6).

---

## 7. Testing Strategy

- **Sim-in-the-loop:** every feature is tested against PX4 SITL first. Watch QGC side-by-side to check that the commands land.
- **MAVLink inspection:** `mavlink-routerd` logs, or QGC *Analyze Tools → MAVLink Inspector*, show the `MANUAL_CONTROL` messages arriving at the expected rate.
- **Failure tests:**
  - Kill Wi-Fi mid-flight. Expect PX4 to RTL (`NAV_DLL_ACT`).
  - Stop the joystick loop. Expect the RC-loss action.
  - Arm without GPS lock (start the sim, then arm right away). Expect the app to show a clear error.
- **Unit tests:** `FlightViewModel` with a fake `DroneRepository` interface.

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| UDP packets don't reach the phone | Same subnet, open firewall, verify with `tcpdump -i any udp port 14551` |
| Port 14550 conflict (QGC vs router) | Give each consumer its own port in `mavlink-router.conf` |
| Drone drops out of manual mode | Keep sending sticks ≥ 10 Hz. Set `COM_RC_IN_MODE=1`. |
| Large APK from `mavsdk-server` native libs | Use ABI splits (`arm64-v8a` only for real devices) |
| MAVSDK API changes between versions | Pin versions. Check the connection URL format (`udp://` vs `udpin://`). |
| Slow Gazebo on weak PC | `HEADLESS=1`, simpler world |
| Moving to a real drone later | Keep all MAVLink code in `DroneRepository`. Only the connection string changes (telemetry radio / companion computer Wi-Fi). |

---

## 9. Future Extensions

- Mission planning: tap waypoints on a map (MapLibre / Google Maps) and upload them with MAVSDK `mission`.
- Geofence display.
- A ROS 2 bridge (`uXRCE-DDS` + `px4_msgs`) for autonomy features such as AprilTag landing, while the app stays the operator UI.
- Gamepad support (Bluetooth controller → same `sendSticks()` API).
- Multi-drone: run several SITL instances (`-i 1`, `-i 2`) and select a drone in the app.

---

## 10. Quick Start Cheat-Sheet

```bash
# Terminal 1 – simulation
cd ~/PX4-Autopilot && make px4_sitl gz_x500

# Terminal 2 – forward MAVLink to the phone
mavlink-routerd -c ~/mavlink-router.conf

# Terminal 3 – (optional) QGroundControl
./QGroundControl.AppImage

# Phone – open app → port 14551 → Connect → ARM → TAKEOFF → MANUAL
```

## 11. References

- PX4 Gazebo simulation: https://docs.px4.io/main/en/sim_gazebo_gz/
- PX4 Ubuntu dev setup: https://docs.px4.io/main/en/dev_setup/dev_env_linux_ubuntu.html
- MAVSDK-Java: https://github.com/mavlink/MAVSDK-Java (see `examples/android-client`)
- MAVSDK docs: https://mavsdk.mavlink.io/
- mavlink-router: https://github.com/mavlink-router/mavlink-router
- PX4 parameter reference: https://docs.px4.io/main/en/advanced_config/parameter_reference.html
- QGroundControl: https://docs.qgroundcontrol.com/
