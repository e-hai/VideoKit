package com.an.video.exoplayer

import androidx.media3.exoplayer.source.MediaSource
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.*
import com.an.video.BaseAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class ExoPagingDataAdapter<T : Any, VH : ExoViewHolder>(
    private val videoManager: ExoManager,
    diffCallback: DiffUtil.ItemCallback<T>,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : PagingDataAdapter<T, VH>(diffCallback, mainDispatcher, workerDispatcher), BaseAdapter {

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