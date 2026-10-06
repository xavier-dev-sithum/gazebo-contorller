# PX4 + Gazebo + Kotlin drone controller

```
Android app (MAVSDK-Java 4.0.4, Compose) ──UDP──► PX4 SITL :18570 ◄──gz──► Gazebo Harmonic (x500_gimbal, Baylands)
        ▲                                                                    │ GstCameraSystem plugin
        └──── RTSP :8564/drone ◄── MediaMTX + ffmpeg (re-encode) ◄── RTP/H.264 udp :5600
```

## 1. Start the simulation and video
```bash
./start_sim.sh                 # terminal 1: Baylands world + x500_gimbal (PX4_GZ_WORLD=default for the empty world)
./video/start_video.sh         # terminal 2: serves rtsp://<PC>:8564/drone
```
Wait until `pxh>` shows. PX4 SITL already defaults `COM_RC_IN_MODE=1`, so the app's MAVLink joystick acts as the RC.

## 2. Build + install the app
```bash
cd DroneController
./gradlew installDebug         # or open the folder in Android Studio
```

## 3. Connect
| Device | MAVLink URL | Camera URL |
|---|---|---|
| Android emulator (same PC) | `udpout://10.0.2.2:18570` | `rtsp://10.0.2.2:8564/drone` |
| Real phone on same Wi-Fi | `udpout://<PC IP>:18570` | `rtsp://<PC IP>:8564/drone` |

Both default correctly on the emulator. Leave the camera URL empty to fly without video.

The app sends first. PX4 then adopts the phone as its GCS partner, so mavlink-router isn't needed.
Real phone: open the firewall if it's enabled: `sudo ufw allow 18570/udp && sudo ufw allow 8564/tcp && sudo ufw allow 8064:8065/udp`.

### USB tethering (recommended: no Wi-Fi freezes)
Wi-Fi on the Tab S11 pauses about 1.5 s roughly every minute, during Samsung roaming/Nearby scans on 2.4 GHz. Over USB the link is about 0.2 ms with no loss.
1. Tablet: Settings → Connections → Mobile Hotspot and Tethering → **USB tethering** (or `adb shell svc usb setFunctions rndis`).
2. PC gets an `enx…` interface (e.g. `172.29.137.157`). Stop it stealing the PC's internet:
   `nmcli connection modify "<that connection>" ipv4.never-default yes ipv6.never-default yes`
3. In the app, use the PC's USB address: `udpout://172.29.137.157:18570`, `rtsp://172.29.137.157:8564/drone`. The app remembers the last URLs.
4. adb in tethering mode needs a udev rule for Samsung (`04e8`): `/etc/udev/rules.d/51-android-samsung.rules`.

## 4. Fly
1. **ARM**, then **TAKEOFF** (climbs to 5 m and holds).
2. **MANUAL** switches to Position mode. After that:
   - Left stick: up/down = climb/descend (center = hold altitude), left/right = yaw
   - Right stick: up/down = forward/back, left/right = roll left/right
   - Releasing a stick springs it back to center, and the drone holds its position.
3. **HOLD / RTL / LAND** when done.

Stick input streams at 20 Hz for as long as the app is connected. If the app dies, PX4 sees RC/datalink loss and runs its failsafe.

## Notes
- Rotation: the app doesn't lock orientation (Android 16 ignores locks on large screens, and this Samsung's display ignores app orientation requests). The HUD adapts to portrait, landscape and any window size, following the device's auto-rotate setting. The activity handles rotation/resize in place (`configChanges`). The MAVLink link and the video stream (`video/VideoStream.kt`) live in the ViewModel, so rotating never reconnects. While connected, the system bars are hidden (swipe from the edge to show them).
- Wi-Fi jitter: PX4 treats the app's sticks as RC. If no stick update arrives for `COM_RC_LOSS_T` (default 0.5 s), it triggers a Hold failsafe, which makes the drone jerk. In SITL it is raised to 1.5 s (`build/px4_sitl_default/bin/px4-param set COM_RC_LOSS_T 1.5`, saved in `rootfs/fs/parameters.bson`). `MPC_MAN_Y_MAX` is lowered to 90 deg/s for smoother yaw. Use 5 GHz Wi-Fi or a wired/USB-tethered link for best results.
- Sticks: 25 Hz, 5% deadzone, 50% expo on yaw/pitch/roll. Throttle is linear so that center = hold altitude.
- Video: RTSP over UDP on devices (RTP 8064/RTCP 8065), TCP on the emulator.
- Gimbal: `x500_gimbal` carries a CGO3 gimbal (tilt -135..+45 deg). The app drives it over the MAVLink gimbal v2 protocol (MAVSDK `gimbal`). PX4 takes gimbal control during LAND/RTL and then releases it, so the app watches `controlStatus` and takes control again on the next command.
- Video stalls: on Wi-Fi, RTSP/TCP can back up silently (MediaMTX logs "reader is too slow"). The player reconnects when no frame has rendered for 3 s, plays faster to catch up when frames queue, and reconnects fresh after the app returns from the background. A low-latency Wi-Fi lock is held while the app is in the foreground. The stream is capped at 1.5 Mbit/s.
- Video: the camera plugin emits H.264 High 4:4:4, which Android can't decode. `video/mediamtx-drone.yml` re-encodes it to baseline 960x720 with `-threads 1`. Multi-slice frames from flat scenes end up in STAP-A packets, and ExoPlayer never renders those. The app plays RTSP over TCP with minimal buffering and retries every 2 s, so start order doesn't matter.
- This MediaMTX instance is separate from the others on this PC (8554/8556) and uses only TCP 8564.
- `app/src/main/jniLibs/*/libc++_shared.so` is copied from NDK 27. `mavsdk_server` needs it but the AAR doesn't bundle it.
- All MAVLink code is in `data/DroneRepository.kt`. To fly a real PX4, change only the connection URL.
