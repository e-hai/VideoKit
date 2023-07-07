package com.an.video.exoplayer

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.ui.StyledPlayerView

abstract class ExoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun getPlayerView(): ExoCoverPlayerView
}