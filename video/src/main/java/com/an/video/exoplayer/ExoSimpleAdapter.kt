package com.an.video.exoplayer

import androidx.recyclerview.widget.RecyclerView
import com.an.video.BaseAdapter
import com.google.android.exoplayer2.source.MediaSource

abstract class ExoSimpleAdapter<VH : ExoViewHolder>(
    private val videoManager: ExoManager
) : RecyclerView.Adapter<VH>(), BaseAdapter {

    private var currentPosition = -1
    private lateinit var recyclerView: RecyclerView


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun switchVideo(position: Int) {
        if (currentPosition == position) return
        (recyclerView.findViewHolderForAdapterPosition(position) as ExoViewHolder).apply {
            getVideoMediaSource(position)?.let {
                videoManager.playVideoFromMediaSource(getPlayerView(), it)
            }
        }
    }

    abstract fun getVideoMediaSource(position: Int): MediaSource?
}