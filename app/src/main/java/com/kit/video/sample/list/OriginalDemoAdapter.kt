package com.kit.video.sample.list

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import com.kit.video.list.original.VideoViewHolder
import com.kit.video.list.original.VideoViewManager
import com.kit.video.list.original.VideoViewPagingAdapter
import com.kit.video.smaple.R

class OriginalDemoAdapter(videoManager: VideoViewManager) :
    VideoViewPagingAdapter<VideoModel, OriginalDemoAdapter.Companion.OriginalVH>(
        videoManager,
        ORIGINAL_COMPARATOR
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OriginalVH {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_videoview_demo, parent, false)
        return OriginalVH(view)
    }

    override fun onBindViewHolder(holder: OriginalVH, position: Int) {
        holder.titleView.text = getItem(position)?.title
    }


    override fun getVideoUri(position: Int): Uri? {
        return getItem(position)?.videoUrl?.toUri()
    }

    companion object {
        val ORIGINAL_COMPARATOR = object : DiffUtil.ItemCallback<VideoModel>() {
            override fun areItemsTheSame(
                oldItem: VideoModel,
                newItem: VideoModel
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: VideoModel,
                newItem: VideoModel
            ): Boolean {
                return oldItem == newItem
            }
        }

        class OriginalVH(itemView: View) : VideoViewHolder(itemView) {
            val titleView: TextView

            init {
                titleView = itemView.findViewById(R.id.titleView)
            }


            override fun getPlayerView(): VideoView {
                return itemView.findViewById(R.id.videoView)
            }
        }
    }
}