package com.an.video.original

import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.an.video.BaseAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class VideoViewPagingAdapter<T : Any, VH : VideoViewHolder>(
    private val videoManager: VideoViewManager,
    diffCallback: DiffUtil.ItemCallback<T>,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : PagingDataAdapter<T, VH>(diffCallback, mainDispatcher, workerDispatcher), BaseAdapter {

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