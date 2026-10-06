package com.example.dronecontroller.util

import android.os.Build

/** True on the Android emulator (its NAT blocks inbound UDP, so video must use RTSP over TCP). */
val isEmulator: Boolean
    get() = Build.FINGERPRINT.contains("generic") || Build.PRODUCT.contains("sdk") ||
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")
