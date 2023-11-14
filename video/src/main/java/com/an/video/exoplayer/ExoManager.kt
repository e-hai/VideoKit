package com.an.video.exoplayer

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoManager(
    lifecycleOwner: LifecycleOwner,
    context: Context,
    private val loop: Boolean = true,
    private val listener: Player.Listener? = null
) {

    private var videoView: PlayerView? = null
    private var mediaSource: MediaSource? = null
    private var player: ExoPlayer? = null
    private val loadControl: LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            0,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
        )
        .setAllocator(DefaultAllocator(true, 16))
        .build()

    init {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    player = ExoPlayer
                        .Builder(context)
                        .setLoadControl(loadControl)
                        .build()
                        .apply {
                            playWhenReady = true
                            repeatMode = if (loop) {
                                Player.REPEAT_MODE_ONE
                            } else {
                                Player.REPEAT_MODE_OFF
                            }
                        }
                    videoView?.player = player
                    mediaSource?.apply {
                        player?.setMediaSource(this)
                        player?.prepare()
                    }
                    listener?.let {
                        player?.addListener(it)
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    videoView?.onResume()
                    player?.playWhenReady = true
                }

                Lifecycle.Event.ON_PAUSE -> {
                    videoView?.onPause()
                    player?.playWhenReady = false
                }

                Lifecycle.Event.ON_STOP -> {
                    videoView?.player = null
                    player?.stop()
                    player?.release()
                    player = null
                }

                else -> {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    fun playVideoFromRaw(
        playerView: PlayerView,
        @RawRes videoRaw: Int
    ) {
        val context = playerView.context
        val mediaSource = ExoHelper.createRawMediaSource(context, videoRaw)
        playVideoFromMediaSource(playerView, mediaSource)
    }

    fun playVideoFromUrl(
        playerView: PlayerView,
        videoUrl: String
    ) {
        val context = playerView.context
        val videoUri = Uri.parse(videoUrl)
        val mediaSource = ExoHelper.createMediaSource(context, videoUri)
        playVideoFromMediaSource(playerView, mediaSource)
    }

    fun playVideoFromMediaSource(
        newPlayerView: PlayerView,
        newMediaSource: MediaSource
    ) {
        newPlayerView.useController = false
        newPlayerView.setShutterBackgroundColor(Color.TRANSPARENT)
        videoView?.player = null
        newPlayerView.player = player?.apply {
            setMediaSource(newMediaSource)
            prepare()
        }
        mediaSource = newMediaSource
        videoView = newPlayerView
    }

    companion object {
        const val TAG = "VideoManager"
    }
}