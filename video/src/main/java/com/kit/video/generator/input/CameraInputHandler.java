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
    private long startTimeNs;
    private int textureId;



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
        eglSurface.makeCurrent();  // 设置当前上下文
        textureRenderer = new TextureRenderer();  // 创建渲染器
        textureRenderer.setup();  // 初始化渲染器
        startTimeNs = System.nanoTime();
        return true;
    }


    // 执行渲染
    @WorkerThread
    public void draw(int textureId) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);  // 清除颜色缓冲区
        textureRenderer.render(textureId, true);  // 渲染纹理
        eglSurface.swap();  // 交换缓冲区
        this.textureId = textureId;
    }

    @WorkerThread
    @Override
    public FrameData getData() {
        long timestampNs = System.nanoTime() - startTimeNs;
        // PTS单位为微秒
        return new FrameData(textureId, timestampNs / 1000);
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
}
