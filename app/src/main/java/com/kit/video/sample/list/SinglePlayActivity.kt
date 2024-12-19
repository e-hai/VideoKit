package com.kit.video.sample.list

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.media3.ui.PlayerView
import com.kit.video.list.exoplayer.ExoManager
import com.kit.video.smaple.R

class SinglePlayActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_play)
        val videoView = findViewById<PlayerView>(R.id.videoView)
//        (intent.getSerializableExtra(KEY_VIDEO) as VideoModel).let {
//            ExoManager(this,this).playVideoFromUrl(
//                videoView, it.videoUrl
//            )
//        }

        ExoManager(this,this).playVideoFromRaw(videoView,R.raw.sub_pop_sticker)
    }

    companion object {
        const val KEY_VIDEO = "KEY_VIDEO"
        fun start(activity: Activity, videoModel: VideoModel?=null) {
            val intent = Intent(activity, SinglePlayActivity::class.java)
            intent.putExtra(KEY_VIDEO, videoModel)
            activity.startActivity(intent)
        }
    }
}