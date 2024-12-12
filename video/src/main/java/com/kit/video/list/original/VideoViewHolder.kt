package com.kit.video.list.original

import android.view.View
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView

abstract class VideoViewHolder(itemView: View) :
    RecyclerView.ViewHolder(itemView) {

    abstract fun getPlayerView(): VideoView
}