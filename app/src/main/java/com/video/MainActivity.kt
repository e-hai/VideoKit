package com.video

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.viewModels
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.widget.ViewPager2
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.load
import com.an.video.exoplayer.ExoCoverPlayerView
import com.an.video.exoplayer.ExoManager
import com.an.video.exoplayer.ExoPagingDataAdapter
import com.an.video.exoplayer.ExoViewHolder
import com.an.video.original.VideoViewHolder
import com.an.video.original.VideoViewManager
import com.an.video.original.VideoViewPagingAdapter
import com.an.video.original.VideoViewSimpleAdapter
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val adapter = ExoDemoAdapter(this, ExoManager(this, this))
        val viewPager = findViewById<ViewPager2>(R.id.viewPager).apply {
            this.adapter = adapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    adapter.switchVideo(position)
                }
            })
        }

        viewModel.getExoPagingData().observe(this) {
            lifecycleScope.launch {
                adapter.submitData(it)
            }
        }

        findViewById<Button>(R.id.nextView).setOnClickListener {
            val videoUri = adapter.getVideoUri(viewPager.currentItem)
            val videoModel = VideoModel(videoUri?.toString() ?: "")
            SinglePlayActivity.start(this@MainActivity, videoModel)
        }
    }
}


class ExoDemoAdapter(context: Context, videoManager: ExoManager) :
    ExoPagingDataAdapter<TestModelExo,
            ExoDemoAdapter.Companion.ExoVH>(videoManager, COMPARATOR) {
    private val imageLoader = ImageLoader.Builder(context)
        .components {
            add(VideoFrameDecoder.Factory())
        }
        .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExoVH {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.adapter_exo_demo, parent, false)
        return ExoVH(view)
    }

    override fun onBindViewHolder(holder: ExoVH, position: Int) {
        val item = getItem(position) ?: return
        holder.titleView.text = item.title
        holder.getPlayerView().getCoverView().load(item.videoUri, imageLoader)
    }

    override fun getVideoMediaSource(position: Int): MediaSource? {
        return getItem(position)?.source
    }

    override fun getVideoUri(position: Int): Uri? {
        return getItem(position)?.videoUri
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

            override fun getPlayerView(): ExoCoverPlayerView {
                return itemView.findViewById(R.id.videoView)
            }
        }
    }
}


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