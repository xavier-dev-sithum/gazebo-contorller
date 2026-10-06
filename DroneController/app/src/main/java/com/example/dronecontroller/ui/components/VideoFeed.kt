package com.example.dronecontroller.ui.components

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.example.dronecontroller.R
import com.example.dronecontroller.video.VideoStream

/**
 * Shows a [VideoStream]. The stream itself lives in the ViewModel, so rotating the screen or
 * switching between the portrait/landscape layouts only re-attaches a view; playback continues.
 * Streaming pauses while the app is in the background and resumes at the live edge.
 */
@Composable
fun VideoFeed(
    stream: VideoStream,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    snapshot: SnapshotController? = null,
) {
    val status by stream.status.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(stream, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> stream.resume()
                Lifecycle.Event.ON_STOP -> stream.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer) // delivers ON_START right away if already started
        // Leaving composition (e.g. layout switch) must not stop playback; only detach.
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(modifier.background(Color.Black).clipToBounds()) {
        AndroidView(
            // TextureView-backed so the zoom transform and snapshots work (see player_texture.xml).
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_texture, null) as PlayerView).also { view ->
                    view.player = stream.player
                    snapshot?.grab = { (view.videoSurfaceView as? TextureView)?.bitmap }
                }
            },
            update = { it.player = stream.player },
            onRelease = { it.player = null },
            modifier = Modifier.matchParentSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
        if (status.isNotEmpty()) {
            Text(
                status,
                color = Color(0xFF6B7A88),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Lets the UI grab the current video frame (the player renders into a TextureView). */
class SnapshotController {
    var grab: (() -> Bitmap?)? = null
}
