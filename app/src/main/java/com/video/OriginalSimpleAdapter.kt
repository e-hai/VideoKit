package com.video

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.core.net.toUri
import com.an.video.original.VideoViewHolder
import com.an.video.original.VideoViewManager
import com.an.video.original.VideoViewSimpleAdapter

class OriginalSimpleAdapter(videoManager: VideoViewManager) :
    VideoViewSimpleAdapter<OriginalSimpleAdapter.SimpleVH>(videoManager) {
    private val dataList = emptyList<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleVH {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_videoview_demo, parent, false)
        return SimpleVH(view)
    }

    override fun onBindViewHolder(holder: SimpleVH, position: Int) {
    }

    class SimpleVH(itemView: View) : VideoViewHolder(itemView) {
        override fun getPlayerView(): VideoView {
            return itemView.findViewById(R.id.videoView)
        }
    }

    override fun getVideoUri(position: Int): Uri {
        return dataList[position].toUri()
    }

    override fun getItemCount(): Int {
        return dataList.size
    }
}