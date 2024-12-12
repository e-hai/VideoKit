package com.kit.video.list.original

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Android自带的播发器，关联生命周期
 * **/
class VideoViewManager(
    lifecycleOwner: LifecycleOwner,
    private val loop: Boolean = true
) {

    private var currentVideoView: VideoView? = null

    init {
        val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d(TAG, "ON_START")
                    currentVideoView?.start()
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME")
                    currentVideoView?.resume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE")
                    currentVideoView?.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d(TAG, "ON_DESTROY")
                    currentVideoView?.stopPlayback()
                }
                else -> {
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleEventObserver)
    }


    fun playVideo(videoView: VideoView, videoUri: Uri?) {
        currentVideoView?.stopPlayback()
        //避免播放失败弹窗
        videoView.setOnErrorListener { _, _, _ -> true }
        //循环播放
        videoView.setOnCompletionListener {
            if (loop) videoView.start()
        }
        videoView.setVideoURI(videoUri)
        videoView.start()
        currentVideoView = videoView
    }

    companion object {
        const val TAG = "VideoViewManager"
    }
}