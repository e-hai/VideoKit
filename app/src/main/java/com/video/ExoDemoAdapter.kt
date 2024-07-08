package com.video

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import com.an.video.exoplayer.ExoManager
import com.an.video.exoplayer.ExoPagingDataAdapter
import com.an.video.exoplayer.ExoViewHolder


class ExoDemoAdapter(context: Context, videoManager: ExoManager) :
    ExoPagingDataAdapter<TestModelExo, ExoDemoAdapter.Companion.ExoVH>(videoManager, COMPARATOR) {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExoVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.adapter_exo_demo, parent, false)
        return ExoVH(view)
    }

    override fun onBindViewHolder(holder: ExoVH, position: Int) {
        val item = getItem(position) ?: return
        holder.titleView.text = item.title
        Log.d(MainActivity.TAG, "onBindViewHolder= $position ")

    }


    override fun getVideoMediaSource(position: Int): MediaSource? {
        return getItem(position)?.source
    }

    companion object {

        val COMPARATOR = object : DiffUtil.ItemCallback<TestModelExo>() {
            override fun areItemsTheSame(
                oldItem: TestModelExo,
                newItem: TestModelExo
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: TestModelExo,
                newItem: TestModelExo
            ): Boolean {
                return oldItem == newItem
            }
        }


        class ExoVH(itemView: View) : ExoViewHolder(itemView) {
            val titleView: TextView

            init {
                titleView = itemView.findViewById(R.id.titleView)
            }

            override fun getPlayerView(): PlayerView {
                return itemView.findViewById(R.id.videoView)
            }
        }
    }
}