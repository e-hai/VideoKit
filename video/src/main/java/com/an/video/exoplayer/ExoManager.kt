package com.an.video.exoplayer

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.RawResourceDataSource

object ExoHelper {
    fun createMediaSource(context: Context, uri: Uri): MediaSource {
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext)
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource
            .Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }
}

class ExoManager(
    lifecycleOwner: LifecycleOwner,
    context: Context,
    private val loop: Boolean = true
) {

    private var videoView: StyledPlayerView? = null
    private var mediaSource: MediaSource? = null
    private var player: ExoPlayer? = null

    init {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "ON_START")
                    player = ExoPlayer
                        .Builder(context)
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
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME")
                    videoView?.onResume()
                    player?.playWhenReady = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE")
                    videoView?.onPause()
                    player?.playWhenReady = false
                }
                Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_STOP")
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
        playerView: StyledPlayerView,
        @RawRes videoRaw: Int
    ) {
        val context = playerView.context
        val rawResourceDataSource = RawResourceDataSource(context)
        val dataSpec = DataSpec(RawResourceDataSource.buildRawResourceUri(videoRaw))
        try {
            rawResourceDataSource.open(dataSpec)
        } catch (e: RawResourceDataSource.RawResourceDataSourceException) {
            e.printStackTrace()
        }
        val videoUri = rawResourceDataSource.uri ?: return
        val mediaSource = ExoHelper.createMediaSource(context, videoUri)
        playVideoFromMediaSource(playerView, mediaSource)
    }

    fun playVideoFromUrl(
        playerView: StyledPlayerView,
        videoUrl: String
    ) {
        val context = playerView.context
        val videoUri = Uri.parse(videoUrl)
        val mediaSource = ExoHelper.createMediaSource(context, videoUri)
        playVideoFromMediaSource(playerView, mediaSource)
    }

    fun playVideoFromMediaSource(
        newPlayerView: StyledPlayerView,
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