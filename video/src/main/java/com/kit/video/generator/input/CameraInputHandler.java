package com.kit.video.generator.input;

import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.view.Surface;

import androidx.annotation.WorkerThread;

import com.kit.video.generator.base.FrameData;
import com.kit.video.generator.base.InputHandler;
import com.kit.video.glutil.EglSurface;
import com.kit.video.glutil.EglWrapper;
import com.kit.video.glutil.TextureRenderer;

/**
 * 摄像头输入处理（OpenGL纹理数据）
 */
public class CameraInputHandler implements InputHandler {

    private EGLContext parentContext;
    private Surface outputSurface;
    private EglWrapper eglWrapper;
    private EglSurface eglSurface;
    private TextureRenderer textureRenderer;
    private int textureId;
    private long lastPresentationTimeUs = 0;


    public void setEglContext(EGLContext parentContext, Surface outputSurface) {
        this.parentContext = parentContext;
        this.outputSurface = outputSurface;

    }


    /**
     * 初始化 EGL 环境和渲染器
     */
    @WorkerThread
    @Override
    public boolean initialize() {
        eglRelease();  // 释放之前的资源
        eglWrapper = new EglWrapper(parentContext, false, true);
        eglSurface = eglWrapper.createFromSurface(outputSurface);
        textureRenderer = new TextureRenderer();  // 创建渲染器
        textureRenderer.setup();  // 初始化渲染器
        return true;
    }


    // 执行渲染
    @WorkerThread
    public void draw(int textureId) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);     // 清除颜色缓冲区
        textureRenderer.render(textureId, true);  // 渲染纹理
        eglSurface.swap();  // 交换缓冲区
        this.textureId = textureId;
    }

    @WorkerThread
    @Override
    public FrameData getData() {
        long timestampUs = getLastPresentationTimeUs();
        // PTS单位为微秒
        return new FrameData(textureId, timestampUs);
    }


    public FrameData getEndOfStreamData() {
        return new FrameData(true, getLastPresentationTimeUs());
    }

    @Override
    public void release() {
        eglRelease();
        parentContext = null;
        outputSurface = null;
    }


    /**
     * 释放 EGL 环境和渲染器
     */
    private void eglRelease() {
        if (eglSurface != null) {
            eglSurface.release();  // 释放输入 Surface
            eglSurface = null;
        }
        if (eglWrapper != null) {
            eglWrapper.release();  // 释放 EGL 环境
            eglWrapper = null;
        }
        if (textureRenderer != null) {
            textureRenderer.release();
            textureRenderer = null;
        }
    }


    /**
     * 获取下一帧的时间戳，确保单调递增。
     *
     * @return 时间戳（单位：微秒）。
     */
    private long getLastPresentationTimeUs() {
        long result = System.nanoTime() / 1000L;
        //时间应该是单调的
        //否则muxer写入失败
        if (result < lastPresentationTimeUs) {
            result = (lastPresentationTimeUs - result) + result;
        }
        lastPresentationTimeUs = result;
        return lastPresentationTimeUs;
    }
}
