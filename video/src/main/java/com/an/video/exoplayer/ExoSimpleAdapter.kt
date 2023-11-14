package com.an.video.exoplayer

import androidx.media3.exoplayer.source.MediaSource
import androidx.recyclerview.widget.RecyclerView
import com.an.video.BaseAdapter

abstract class ExoSimpleAdapter<VH : ExoViewHolder>(
    private val videoManager: ExoManager
) : RecyclerView.Adapter<VH>(), BaseAdapter {

    private var currentPosition = -1
    private var recyclerView: RecyclerView? = null


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun switchVideo(position: Int) {
        if (currentPosition == position) return
        (recyclerView?.findViewHolderForAdapterPosition(position))?.let {
            val exoViewHolder = it as ExoViewHolder
            val mediaSource = getVideoMediaSource(position) ?: return
            videoManager.playVideoFromMediaSource(exoViewHolder.getPlayerView(), mediaSource)
        }
    }

    abstract fun getVideoMediaSource(position: Int): MediaSource?
}