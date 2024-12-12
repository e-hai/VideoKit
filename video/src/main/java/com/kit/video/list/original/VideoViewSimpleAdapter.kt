package com.kit.video.list.original

import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import com.kit.video.list.BaseAdapter


abstract class VideoViewSimpleAdapter<VH : VideoViewHolder>(
    private val videoManager: VideoViewManager
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
            val exoViewHolder = it as VideoViewHolder
            val mediaSource = getVideoUri(position) ?: return
            videoManager.playVideo(exoViewHolder.getPlayerView(), mediaSource)
        }
    }

    abstract fun getVideoUri(position: Int): Uri?
}