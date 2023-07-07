package com.an.video.exoplayer

import android.util.Log
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.*
import com.an.video.BaseAdapter
import com.google.android.exoplayer2.source.MediaSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

abstract class ExoPagingDataAdapter<T : Any, VH : ExoViewHolder>(
    private val videoManager: ExoManager,
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
        (recyclerView.findViewHolderForAdapterPosition(position) as ExoViewHolder).apply {
            getVideoMediaSource(position)?.let {
                videoManager.playVideoFromMediaSource(getPlayerView(), it)
            }
        }
    }

    abstract fun getVideoMediaSource(position: Int): MediaSource?
}