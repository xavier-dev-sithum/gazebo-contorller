package com.example.dronecontroller.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.example.dronecontroller.util.isEmulator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Low-latency RTSP stream of the drone camera (served by MediaMTX, see video/mediamtx-drone.yml).
 *
 * Owned by the ViewModel, not by a composable, so it survives rotation and layout changes:
 * views only attach to [player]. The UI calls [resume]/[pause] from the Activity lifecycle.
 *  - retries every 2 s on errors, so the stream can be started before or after the app
 *  - watchdog: reconnects if no frame was rendered for STALL_MS (Wi-Fi hiccups freeze RTSP silently)
 *  - catch-up: plays slightly faster while frames are queued, so latency can't build up
 */
@OptIn(UnstableApi::class)
@MainThread
class VideoStream(context: Context, private val url: String) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLoadControl(
            // Keep as little buffered as possible: we want "now", not smooth playback.
            // maxBuffer is 2 s so loading never pauses (a paused loader backs up the stream);
            // the catch-up below keeps the actual delay low.
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(500, 2000, 100, 100)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build()

    private val _status = MutableStateFlow("Connecting to UAV camera…")
    /** Human-readable state for the overlay; empty while video is playing. */
    val status: StateFlow<String> = _status.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val lastFrameAt = AtomicLong(0)
    private var startedAt = 0L
    private var running = false

    private val retry = Runnable { if (running) connect() }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt.get() > STALL_MS && now - startedAt > STALL_MS) {
                Log.w(TAG, "stalled: no frame for ${now - lastFrameAt.get()} ms, reconnecting")
                _status.value = "Video stalled – reconnecting…"
                connect()
            }
            if (now - statsAt >= STATS_MS) {
                Log.i(TAG, "stats: ${frames * 1000 / (now - statsAt).coerceAtLeast(1)} fps, " +
                    "max gap $maxGapMs ms, queued ${player.totalBufferedDuration} ms, " +
                    "speed ${player.playbackParameters.speed}")
                frames = 0; maxGapMs = 0; statsAt = now
            }
            // Catch up to live: frames waiting in the buffer = delay we can remove. Far behind
            // (e.g. right after a reconnect) -> up to 2x; near live -> gentle, to avoid running dry.
            val queued = player.totalBufferedDuration
            val speed = when {
                queued > 800 -> 2.0f
                queued > 400 -> 1.5f
                queued > 200 -> 1.15f
                else -> 1f
            }
            if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
            handler.postDelayed(this, TICK_MS)
        }
    }

    // Diagnostics (logcat tag "VideoStream"): frame gaps, drops, reconnects. Read with
    // `adb logcat -s VideoStream` when the picture freezes.
    private var frames = 0
    private var maxGapMs = 0L
    private var statsAt = 0L

    init {
        // Called on the playback thread for every rendered frame.
        player.setVideoFrameMetadataListener { _, _, _, _ ->
            val now = SystemClock.elapsedRealtime()
            val gap = now - lastFrameAt.getAndSet(now)
            if (gap > GAP_LOG_MS && now - startedAt > gap) Log.w(TAG, "frame gap ${gap} ms")
            frames++
            if (gap > maxGapMs) maxGapMs = gap
        }
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(t: AnalyticsListener.EventTime, dropped: Int, elapsedMs: Long) {
                Log.w(TAG, "decoder dropped $dropped frames in $elapsedMs ms")
            }
        })
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> _status.value = ""
                    Player.STATE_BUFFERING -> if (_status.value.isEmpty()) _status.value = "Buffering…"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "player error ${error.errorCodeName}: ${error.message}")
                _status.value = "No video (${error.errorCodeName}) – retrying"
                handler.removeCallbacks(retry)
                handler.postDelayed(retry, RETRY_MS)
            }
        })
    }

    /** Start (or keep) playing. Idempotent: calling it again while running does nothing. */
    fun resume() {
        if (running) return
        running = true
        connect()
        handler.post(tick)
    }

    /** Stop streaming (app in background). [resume] reconnects at the live edge. */
    fun pause() {
        running = false
        handler.removeCallbacksAndMessages(null)
        player.stop()
    }

    fun release() {
        pause()
        player.release()
    }

    private fun connect() {
        Log.i(TAG, "connecting $url")
        handler.removeCallbacks(retry)
        startedAt = SystemClock.elapsedRealtime()
        lastFrameAt.set(startedAt)
        player.stop()
        player.setMediaSource(
            RtspMediaSource.Factory()
                // UDP on real devices: a late packet costs one glitchy frame instead of freezing
                // the whole TCP stream. The emulator's NAT drops inbound UDP, so it stays on TCP.
                // (ExoPlayer also falls back to TCP by itself if no UDP arrives.)
                .setForceUseRtpTcp(isEmulator)
                .setTimeoutMs(4000)
                .createMediaSource(MediaItem.fromUri(url))
        )
        player.prepare()
        player.playWhenReady = true
    }

    private companion object {
        const val TAG = "VideoStream"
        const val STALL_MS = 2000L
        const val GAP_LOG_MS = 300L
        const val STATS_MS = 10_000L
        const val RETRY_MS = 2000L
        const val TICK_MS = 500L
    }
}
