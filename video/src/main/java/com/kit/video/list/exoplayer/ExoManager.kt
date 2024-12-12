package com.kit.video.list.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
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


class ExoManager(
    lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val loop: Boolean = true,
    private val listener: Player.Listener? = null
) {

    private var videoView: PlayerView? = null
    private var player: ExoPlayer? = null
    private var lastMediaSource: MediaSource? = null

    @SuppressLint("UnsafeOptInUsageError")
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
                Lifecycle.Event.ON_CREATE->{
                    Log.d(TAG, "ON_CREATE")
                }
                Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "ON_START ")
                    if (Build.VERSION.SDK_INT > 23) {
                        initializePlayer()
                        videoView?.onResume()
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME  ")
                    if (Build.VERSION.SDK_INT <= 23 || player == null) {
                        initializePlayer()
                        videoView?.onResume()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE")
                    if (Build.VERSION.SDK_INT <= 23) {
                        videoView?.onPause()
                        releasePlayer()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_STOP")
                    if (Build.VERSION.SDK_INT > 23) {
                        videoView?.onPause()
                        releasePlayer();
                    }
                }
                Lifecycle.Event.ON_DESTROY->{
                    Log.d(TAG, "ON_DESTROY")
                }
                else -> {
                    Log.d(TAG, "ON_ else")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        Log.d(TAG, "initializePlayer")

        val newPlayer = ExoPlayer
            .Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = if (loop) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
                playWhenReady = true
                seekTo(1)
            }
        lastMediaSource?.apply {
            newPlayer.setMediaSource(this)
            newPlayer.prepare()
        }
        listener?.let {
            newPlayer.addListener(it)
        }
        videoView?.player = newPlayer
        player = newPlayer
    }

    private fun releasePlayer() {
        Log.d(TAG, "releasePlayer")
        player?.apply {
            release()
            player = null
            videoView?.player = null
        }
    }

    fun playVideoFromRaw(
        playerView: PlayerView,
        @RawRes videoRaw: Int
    ) {
        val context = playerView.context
        val lastMediaSource = ExoHelper.createRawMediaSource(context, videoRaw)
        playVideoFromMediaSource(playerView, lastMediaSource)
    }

    fun playVideoFromUrl(
        playerView: PlayerView,
        videoUrl: String
    ) {
        val context = playerView.context
        val videoUri = Uri.parse(videoUrl)
        val lastMediaSource = ExoHelper.createMediaSource(context, videoUri)
        playVideoFromMediaSource(playerView, lastMediaSource)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun playVideoFromMediaSource(
        newPlayerView: PlayerView,
        newMediaSource: MediaSource
    ) {
        Log.d(TAG, "playVideo")
        player?.let {
            it.setMediaSource(newMediaSource)
            it.prepare()
            PlayerView.switchTargetView(it, videoView, newPlayerView)
        }

        lastMediaSource = newMediaSource
        videoView = newPlayerView
    }

    companion object {
        const val TAG = "VideoManager"
    }
}