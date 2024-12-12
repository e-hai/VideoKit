package com.kit.video.list.original

import android.net.Uri
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kit.video.list.BaseAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class VideoViewPagingAdapter<T : Any, VH : VideoViewHolder>(
    private val videoManager: VideoViewManager,
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
            val exoViewHolder = it as VideoViewHolder
            val mediaSource = getVideoUri(position) ?: return
            videoManager.playVideo(exoViewHolder.getPlayerView(), mediaSource)
        }
    }

    abstract fun getVideoUri(position: Int): Uri?
}