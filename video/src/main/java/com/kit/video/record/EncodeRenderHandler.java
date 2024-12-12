package com.kit.video.record;


import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.kit.video.glutil.TextureRenderer;
import com.kit.video.glutil.EglSurface;
import com.kit.video.glutil.EglWrapper;


/**
 * 编码渲染处理器，负责将视频帧渲染到纹理并进行处理。
 * 这个类通过 EGL 环境和 OpenGL ES 渲染视频数据，适用于 GPU 加速的编码。
 */
public class EncodeRenderHandler implements Runnable {
    private static final String TAG = "GPUCameraRecorder";  // 日志标识符
    private final Object sync = new Object();  // 同步锁
    private EGLContext sharedContext;  // 共享的 EGL 上下文
    private boolean isRecordable;  // 是否可录制
    private Object surface;  // 渲染目标 Surface
    private int texId = -1;  // 纹理 ID
    private boolean requestSetEglContext;  // 是否请求设置 EGL 上下文
    private boolean requestRelease;  // 是否请求释放资源
    private int requestDraw;  // 渲染请求计数

    private float[] MVPMatrix = new float[16];  // MVP 矩阵

    private final float XMatrixScale;  // X 轴缩放比例
    private final float YMatrixScale;  // Y 轴缩放比例
    private final float fileWidth;  // 文件宽度
    private final float fileHeight;  // 文件高度
    private EglWrapper egl;  // EGL 包装类
    private EglSurface inputSurface;  // 输入 Surface
    private TextureRenderer previewShader;  // 预览 Shader

    /**
     * 创建并初始化 EncodeRenderHandler
     *
     * @param name           线程名称
     * @param flipVertical   是否垂直翻转
     * @param flipHorizontal 是否水平翻转
     * @param viewAspect     视图宽高比
     * @param fileWidth      文件宽度
     * @param fileHeight     文件高度
     * @return 初始化后的 EncodeRenderHandler 实例
     */
    static EncodeRenderHandler createHandler(final String name,
                                             final boolean flipVertical,
                                             final boolean flipHorizontal,
                                             final float viewAspect,
                                             final float fileWidth,
                                             final float fileHeight) {
        Log.v(TAG, "createHandler:");
        Log.v(TAG, "fileAspect:" + (fileHeight / fileWidth) + " viewAspect: " + viewAspect);

        final EncodeRenderHandler handler = new EncodeRenderHandler(
                flipVertical,
                flipHorizontal,
                fileHeight > fileWidth ? fileHeight / fileWidth : fileWidth / fileHeight,
                viewAspect,
                fileWidth,
                fileHeight
        );
        synchronized (handler.sync) {
            new Thread(handler, !TextUtils.isEmpty(name) ? name : TAG).start();
            try {
                handler.sync.wait();  // 等待线程初始化完成
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
        }

        return handler;
    }

    /**
     * 构造函数
     *
     * @param flipVertical   是否垂直翻转
     * @param flipHorizontal 是否水平翻转
     * @param fileAspect     文件宽高比
     * @param viewAspect     视图宽高比
     * @param fileWidth      文件宽度
     * @param fileHeight     文件高度
     */
    private EncodeRenderHandler(final boolean flipVertical,
                                final boolean flipHorizontal,
                                final float fileAspect,
                                final float viewAspect,
                                final float fileWidth,
                                final float fileHeight) {
        this.fileWidth = fileWidth;
        this.fileHeight = fileHeight;

        // 根据文件宽高比和视图宽高比计算矩阵的缩放比例
        if (fileAspect == viewAspect) {
            XMatrixScale = (flipHorizontal ? -1 : 1);
            YMatrixScale = flipVertical ? -1 : 1;
        } else {
            if (fileAspect < viewAspect) {
                XMatrixScale = (flipHorizontal ? -1 : 1);
                YMatrixScale = (flipVertical ? -1 : 1) * (viewAspect / fileAspect);
                Log.v(TAG, "cameraAspect: " + viewAspect + " YMatrixScale :" + YMatrixScale);
            } else {
                XMatrixScale = (flipHorizontal ? -1 : 1) * (fileAspect / viewAspect);
                YMatrixScale = (flipVertical ? -1 : 1);
                Log.v(TAG, "cameraAspect: " + viewAspect + " YMatrixScale :" + YMatrixScale + " XMatrixScale :" + XMatrixScale);
            }
        }
    }

    /**
     * 设置 EGL 上下文
     *
     * @param shared_context 共享的 EGL 上下文
     * @param tex_id         纹理 ID
     * @param surface        渲染目标 Surface
     */
    final void setEglContext(final EGLContext shared_context, final int tex_id, final Object surface) {
        Log.i(TAG, "setEglContext:");
        // 检查支持的 Surface 类型
        if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture) && !(surface instanceof SurfaceHolder)) {
            throw new RuntimeException("unsupported window type:" + surface);
        }
        synchronized (sync) {
            if (requestRelease) return;  // 如果已经请求释放资源，直接返回
            sharedContext = shared_context;
            texId = tex_id;
            this.surface = surface;
            this.isRecordable = true;
            requestSetEglContext = true;  // 标记请求设置 EGL 上下文
            sync.notifyAll();  // 通知渲染线程
            try {
                sync.wait();  // 等待 EGL 上下文设置完成
            } catch (final InterruptedException e) {
            }
        }
    }

    /**
     * 准备绘制
     */
    final void prepareDraw() {
        synchronized (sync) {
            if (requestRelease) return;
            requestDraw++;  // 增加渲染请求计数
            sync.notifyAll();  // 通知渲染线程
        }
    }

    /**
     * 绘制视频帧
     *
     * @param tex_id    纹理 ID
     * @param mvpMatrix MVP 矩阵
     */
    public final void draw(final int tex_id, final float[] mvpMatrix) {
        synchronized (sync) {
            if (requestRelease) return;  // 如果已经请求释放资源，直接返回
            texId = tex_id;
            System.arraycopy(mvpMatrix, 0, MVPMatrix, 0, 16);  // 将 MVP 矩阵复制到类成员变量
            Matrix.scaleM(MVPMatrix, 0, XMatrixScale, YMatrixScale, 1);  // 进行缩放操作
            requestDraw++;  // 增加渲染请求计数
            sync.notifyAll();  // 通知渲染线程
        }
    }

    /**
     * 释放资源
     */
    public final void release() {
        Log.i(TAG, "release:");
        synchronized (sync) {
            if (requestRelease) return;  // 如果已经请求释放资源，直接返回
            requestRelease = true;  // 标记请求释放资源
            sync.notifyAll();  // 通知渲染线程
            try {
                sync.wait();  // 等待资源释放完成
            } catch (final InterruptedException e) {
            }
        }
    }

    /**
     * 渲染线程的执行方法
     */
    @Override
    public final void run() {
        Log.i(TAG, "EncodeRenderHandler thread started:");
        synchronized (sync) {
            requestSetEglContext = requestRelease = false;
            requestDraw = 0;
            sync.notifyAll();
        }
        boolean localRequestDraw;  // 是否需要执行渲染

        for (; ; ) {
            synchronized (sync) {
                if (requestRelease) break;  // 如果请求释放资源，则退出线程
                if (requestSetEglContext) {  // 如果请求设置 EGL 上下文
                    requestSetEglContext = false;
                    internalPrepare();  // 初始化 EGL 环境
                }
                localRequestDraw = requestDraw > 0;  // 如果有渲染请求
                if (localRequestDraw) {
                    requestDraw--;  // 渲染请求计数减 1
                }
            }
            if (localRequestDraw) {
                // 执行渲染
                if ((egl != null) && texId >= 0) {
                    inputSurface.makeCurrent();  // 设置为当前的 EGL 上下文
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);  // 清除颜色缓冲区
                    int finalTexId = texId;
                    previewShader.render(finalTexId, true);  // 渲染纹理
                    inputSurface.swap();  // 交换缓冲区
                }
            } else {
                // 如果没有渲染请求，则阻塞线程
                synchronized (sync) {
                    try {
                        sync.wait();
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        }
        synchronized (sync) {
            requestRelease = true;  // 标记为释放资源
            internalRelease();  // 释放资源
            sync.notifyAll();  // 通知释放完成
        }
        Log.i(TAG, "EncodeRenderHandler thread finished:");
    }

    /**
     * 初始化 EGL 环境和渲染器
     */
    private void internalPrepare() {
        Log.i(TAG, "internalPrepare:");
        internalRelease();  // 释放之前的资源
        egl = new EglWrapper(sharedContext, false, isRecordable);  // 创建 EGL 环境
        inputSurface = egl.createFromSurface(surface);  // 从 Surface 创建输入 Surface
        inputSurface.makeCurrent();  // 设置当前上下文
        previewShader = new TextureRenderer();  // 创建渲染器
        previewShader.setup();  // 初始化渲染器
        surface = null;  // 释放 Surface
        sync.notifyAll();  // 通知渲染线程
    }

    /**
     * 释放 EGL 环境和渲染器
     */
    private void internalRelease() {
        Log.i(TAG, "internalRelease:");
        if (inputSurface != null) {
            inputSurface.release();  // 释放输入 Surface
            inputSurface = null;
        }
        if (egl != null) {
            egl.release();  // 释放 EGL 环境
            egl = null;
        }
    }
}


