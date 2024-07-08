package com.an.video.exoplayer

import android.view.View
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

abstract class ExoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    abstract fun getPlayerView(): PlayerView
}