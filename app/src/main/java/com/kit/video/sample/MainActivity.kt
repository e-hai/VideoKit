package com.kit.video.sample

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
import com.kit.video.smaple.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    private var selectedPosition = 0
    private lateinit var viewPager: ViewPager2
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback
    private lateinit var exoDemoAdapter: ExoDemoAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        setContentView(R.layout.activity_main)
        exoDemoAdapter = ExoDemoAdapter(this, ExoManager(this, this, listener = object :
            Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
            }
        }))
        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                selectedPosition = position
                exoDemoAdapter.switchVideo(position)
            }
        }
        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = exoDemoAdapter
        lifecycleScope.launch {
            viewModel.exampleExoData.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { pagingData ->
                    exoDemoAdapter.submitData(pagingData)
                }
        }

        findViewById<Button>(R.id.nextView).setOnClickListener {
            SinglePlayActivity.start(this)
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



    override fun onStart() {
        super.onStart()
        viewPager.postDelayed({
            viewPager.setCurrentItem(selectedPosition, false)
            viewPager.registerOnPageChangeCallback(pageChangeCallback)
            pageChangeCallback.onPageSelected(selectedPosition)
        }, 500)
    }


    override fun onStop() {
        super.onStop()
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    companion object {
        const val TAG = "MainActivity"
    }

}







