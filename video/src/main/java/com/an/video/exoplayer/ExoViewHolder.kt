package com.an.video.exoplayer

import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class ExoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun getPlayerView(): ExoCoverPlayerView
}