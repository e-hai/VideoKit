package com.kit.video.list.exoplayer

import android.util.Log
import androidx.media3.exoplayer.source.MediaSource
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.*
import com.kit.video.list.BaseAdapter
import com.kit.video.list.exoplayer.ExoManager.Companion.TAG
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
        Log.d(TAG, "switchVideo=$currentPosition $position ")

        if (currentPosition == position) return
        (recyclerView?.findViewHolderForAdapterPosition(position))?.let {
            val exoViewHolder = it as ExoViewHolder
            val mediaSource = getVideoMediaSource(position) ?: return
            videoManager.playVideoFromMediaSource(exoViewHolder.getPlayerView(), mediaSource)
        }
    }


    abstract fun getVideoMediaSource(position: Int): MediaSource?
}