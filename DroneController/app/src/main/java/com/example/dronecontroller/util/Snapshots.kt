package com.example.dronecontroller.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves a frame to Pictures/DroneController. Returns the file name. Call off the main thread. */
fun saveSnapshot(context: Context, bitmap: Bitmap): String {
    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Snapshots need Android 10+" }
    val name = "drone_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DroneController")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create image")
    resolver.openOutputStream(uri).use { out ->
        checkNotNull(out) { "Cannot open image" }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    }
    return name
}
