package com.kit.video.sample

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.viewpager2.widget.ViewPager2
import com.kit.video.list.exoplayer.ExoManager
import com.kit.video.sample.generator.GeneratorSampleActivity
import com.kit.video.sample.list.ExoDemoAdapter
import com.kit.video.sample.list.MainViewModel
import com.kit.video.sample.list.SinglePlayActivity
import com.kit.video.sample.list.SinglePlayActivity.Companion.KEY_VIDEO
import com.kit.video.sample.list.VideoListSampleActivity
import com.kit.video.smaple.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.bt_go_list).setOnClickListener {
            val intent = Intent(this, VideoListSampleActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.bt_go_generator).setOnClickListener {
            val intent = Intent(this, GeneratorSampleActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 隐藏系统底下导航栏和状态栏
     * **/
    private fun hideSystemNavigation() {
        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // 隐藏底部导航栏
                    or View.SYSTEM_UI_FLAG_FULLSCREEN // 隐藏状态栏
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // 沉浸式模式，用户交互后会自动恢复
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE // 保持布局稳定
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // 布局延伸到导航栏下方
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) // 布局延伸到状态栏下方
        }
    }


}







