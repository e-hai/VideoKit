package com.an.video.original

import androidx.recyclerview.widget.RecyclerView
import com.an.video.BaseAdapter


abstract class VideoViewSimpleAdapter<VH : VideoViewHolder>(
    private val videoManager: VideoViewManager
) : RecyclerView.Adapter<VH>(), BaseAdapter {

    private var currentPosition = -1
    private lateinit var recyclerView: RecyclerView


    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun switchVideo(position: Int) {
        if (currentPosition == position) return
        (recyclerView.findViewHolderForAdapterPosition(position) as VideoViewHolder).apply {
            videoManager.playVideo(
                getPlayerView(),
                getVideoUri(position)
            )
        }
    }
}