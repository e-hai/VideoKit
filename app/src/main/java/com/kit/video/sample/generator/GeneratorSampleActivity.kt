package com.kit.video.sample.generator

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.Animator
import androidx.core.animation.ObjectAnimator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kit.video.generator.ViewProcessingPipeline
import com.kit.video.generator.ViewProcessingPipeline.OnFrameCapturedListener
import com.kit.video.generator.base.MediaListener
import com.kit.video.smaple.R
import java.io.File

class GeneratorSampleActivity : AppCompatActivity() {

    private lateinit var viewMove: View
    private lateinit var startButton: Button
    private lateinit var layoutPipeline: ViewProcessingPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generator_sample)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        layoutPipeline = findViewById(R.id.layout_pipeline)
        viewMove = findViewById(R.id.view_move)
        startButton = findViewById(R.id.startButton)

        startButton.setOnClickListener {
            startAnimation()
        }
        layoutPipeline.setMediaListener(object : MediaListener {
            override fun onStart() {
                Log.d("TAG", "onStart")
            }

            override fun onEnd() {
                Log.d("TAG", "onEnd")
            }

            override fun onError(errorMessage: String?) {
                Log.d("TAG", "onError=$errorMessage")
            }
        })
    }

    private fun startAnimation() {

        val animator = ObjectAnimator.ofFloat(viewMove, "translationY", 0f, 500f).apply {
            duration = 2000 // 动画持续时间，单位为毫秒
            addUpdateListener {
                layoutPipeline.invalidate()
            }
            addListener(object : Animator.AnimatorListener {

                override fun onAnimationStart(p0: Animator) {
                    val fileName = "example.mp4"
                    val file = File(filesDir, fileName)
                    file.createNewFile()
                    layoutPipeline.start(file.absolutePath)
                }

                override fun onAnimationEnd(p0: Animator) {
                    layoutPipeline.stop()
                }

                override fun onAnimationCancel(p0: Animator) {
                }

                override fun onAnimationRepeat(p0: Animator) {
                }
            })
        }
        layoutPipeline.setOnFrameCapturedListener(object : OnFrameCapturedListener {
            override fun onPause() {
                animator.pause()
            }

            override fun onResume() {
                animator.resume()
            }
        })
        animator.start()
    }
}