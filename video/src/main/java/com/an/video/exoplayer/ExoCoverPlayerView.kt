package com.an.video.exoplayer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 带封面的播放器
 * **/
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoCoverPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private val coverView: ImageView

    private val coverListener: Player.Listener

    init {
        val params = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        coverView = ImageView(context)
        coverView.setBackgroundColor(Color.BLACK)
        coverView.adjustViewBounds = true
        coverView.scaleType = ImageView.ScaleType.FIT_XY
        addView(coverView, params)

        coverListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                postDelayed({
                    coverView.visibility = View.GONE
                }, 1000)
            }
        }
        resizeMode= AspectRatioFrameLayout.RESIZE_MODE_FIT
    }


    override fun setPlayer(player: Player?) {
        if (null == player) {
            coverView.visibility = VISIBLE
            this.player?.removeListener(coverListener)
        } else {
            player.addListener(coverListener)
        }
        super.setPlayer(player)
    }

    fun getCoverView(): ImageView {
        return coverView
    }

}