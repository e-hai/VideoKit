package com.kit.video.record;

import android.opengl.EGLContext;
import android.os.Handler;
import android.util.Log;


/**
 * 管理视频和音频的编码、录制与停止等操作。
 * 通过与 `MediaEncoder` 和 `MediaMuxerCaptureWrapper` 的交互处理录制过程。
 *
 * 主要操作流程：
 * 1. 创建音频和视频的 `MediaCodec` 编码器，编码完成后通过 `MediaMuxerCaptureWrapper` 进行合成。
 * 2. 音频数据来自 `AudioRecord`（录音），视频数据来自图像流（`Surface`）。
 * 3. 涉及四个线程：
 *    - 在 `MediaAudioEncoder` 初始化时启动音频线程（AudioThread）。
 *    - 在 `MediaVideoEncoder` 初始化时启动视频编码线程。
 *    - 在 `EncodeRenderHandler` 初始化时启动渲染线程。
 *    - AudioThread 用于处理音频数据的录制与编码。
 */
public class MediaManager implements MediaEncoder.MediaEncoderListener {
    private boolean started = false;  // 标记是否已经启动录制
    private boolean mute = false;  // 是否静音
    private MediaMuxerCaptureWrapper muxer;  // 媒体复用器，用于处理音视频输出文件
    private MediaVideoEncoder videoEncoder;  // 视频编码器
    private CameraRecordListener cameraRecordListener;  // 录制监听器
    private boolean flipVertical = false;  // 是否垂直翻转
    private boolean flipHorizontal = false;  // 是否水平翻转
    private boolean videoStopped;  // 视频编码是否停止
    private boolean audioStopped;  // 音频编码是否停止
    private boolean videoExitReady;  // 视频编码器准备退出
    private boolean audioExitReady;  // 音频编码器准备退出
    private final EGLContext parentContext;  // EGL 上下文，供 OpenGL 使用

    /**
     * 构造函数，初始化 MediaManager 实例。
     *
     * @param parentContext EGL 上下文
     */
    public MediaManager(final EGLContext parentContext) {
        this.parentContext = parentContext;
    }

    /**
     * 编码器准备就绪时的回调。
     * 在视频编码器和音频编码器准备好后会调用此方法。
     *
     * @param encoder 编码器实例
     */
    @Override
    public void onPrepared(final MediaEncoder encoder) {
        Log.v("TAG", "onPrepared: encoder=" + encoder);
        if (encoder instanceof MediaVideoEncoder) {
            videoStopped = false;  // 视频编码未停止
            setVideoEncoder((MediaVideoEncoder) encoder);  // 设置视频编码器
        }

        if (encoder instanceof MediaAudioEncoder) {
            audioStopped = false;  // 音频编码未停止
        }
    }

    /**
     * 编码器停止时的回调。
     * 在视频或音频编码器停止时会调用此方法。
     *
     * @param encoder 编码器实例
     */
    @Override
    public void onStopped(final MediaEncoder encoder) {
        Log.v("TAG", "onStopped: encoder=" + encoder);
        if (encoder instanceof MediaVideoEncoder) {
            videoStopped = true;  // 视频编码已停止
            setVideoEncoder(null);  // 清除视频编码器
        }
        if (encoder instanceof MediaAudioEncoder) {
            audioStopped = true;  // 音频编码已停止
        }
    }

    /**
     * 编码器退出时的回调。
     * 在视频或音频编码器完成编码后会调用此方法。
     *
     * @param encoder 编码器实例
     */
    @Override
    public void onExit(final MediaEncoder encoder) {
        if (encoder instanceof MediaVideoEncoder && videoStopped) {
            videoExitReady = true;  // 视频编码器退出准备完成
        }
        if (encoder instanceof MediaAudioEncoder && audioStopped) {
            audioExitReady = true;  // 音频编码器退出准备完成
        }
        if (videoExitReady && (audioExitReady || mute)) {
            // 视频和音频都已准备好或处于静音状态，通知录像文件准备好
            if (null != cameraRecordListener) cameraRecordListener.onVideoFileReady();
        }
    }

    /**
     * 设置视频编码器。
     * 初始化视频编码器并设置 EGL 上下文。
     *
     * @param encoder 视频编码器
     */
    private void setVideoEncoder(final MediaVideoEncoder encoder) {
        videoEncoder = encoder;
        if (null != videoEncoder) videoEncoder.setEglContext(parentContext, -1);
    }

    /**
     * 设置是否进行水平翻转。
     *
     * @param flipHorizontal 是否水平翻转
     */
    public void setFlipHorizontal(boolean flipHorizontal) {
        this.flipHorizontal = flipHorizontal;
    }

    /**
     * 设置是否进行垂直翻转。
     *
     * @param flipVertical 是否垂直翻转
     */
    public void setFlipVertical(boolean flipVertical) {
        this.flipVertical = flipVertical;
    }

    /**
     * 设置是否静音。
     *
     * @param mute 是否静音
     */
    public void setMute(boolean mute) {
        this.mute = mute;
    }

    /**
     * 设置录像回调监听器。
     *
     * @param cameraRecordListener 录像回调监听器
     */
    public void setCameraRecordListener(CameraRecordListener cameraRecordListener) {
        this.cameraRecordListener = cameraRecordListener;
    }

    /**
     * 开始录制并处理数据。
     * 该方法会初始化复用器、视频编码器、音频编码器，并开始录制。
     *
     * @param filePath 录制文件的路径
     * @param fileWidth 文件宽度
     * @param fileHeight 文件高度
     */
    public void start(final String filePath, int fileWidth, int fileHeight) {
        if (started) return;  // 如果已经启动录制，直接返回
        new Handler().post(() -> {
            try {
                // 初始化媒体复用器
                muxer = new MediaMuxerCaptureWrapper(filePath);
                // 初始化视频编码器
                new MediaVideoEncoder(
                        muxer,
                        this,
                        fileWidth,
                        fileHeight,
                        flipHorizontal,
                        flipVertical,
                        fileWidth,
                        fileHeight
                );
                // 如果未静音，初始化音频编码器
                if (!mute) {
                    new MediaAudioEncoder(muxer, this);
                }
                muxer.prepare();  // 准备复用器
                muxer.startRecording();  // 开始录制

                // 通知回调监听器，录制已开始
                if (cameraRecordListener != null) {
                    cameraRecordListener.onRecordStart();
                }
            } catch (Exception e) {
                notifyOnError(e);  // 发生错误时通知
            }
        });

        started = true;  // 标记为已启动
    }

    /**
     * 停止录制。
     * 该方法会停止音视频录制，并清理资源。
     */
    public void stop() {
        if (!started) return;  // 如果没有启动录制，直接返回
        try {
            new Handler().post(() -> {
                try {
                    if (muxer != null) {
                        muxer.stopRecording();  // 停止录制
                        muxer = null;  // 清空复用器
                    }
                } catch (Exception e) {
                    Log.d("TAG", "RuntimeException: stop() is called immediately after start()");
                    notifyOnError(e);  // 停止录制过程中发生异常，通知错误
                }
                notifyOnDone();  // 通知录制完成
            });

        } catch (Exception e) {
            notifyOnError(e);  // 发生错误时通知
        }
        started = false;  // 标记为未启动
    }

    /**
     * 当新的一帧可用时调用此方法。
     * 它将帧纹理和 MVP 矩阵传递给视频编码器。
     *
     * @param texName 纹理名称
     * @param mvpMatrix MVP 矩阵
     */
    public void frameAvailableSoon(final int texName, final float[] mvpMatrix) {
        if (null != videoEncoder) videoEncoder.frameAvailableSoon(texName, mvpMatrix);
    }

    /**
     * 释放资源。
     * 停止录制并释放所有相关资源。
     */
    public void release() {
        try {
            if (muxer != null) {
                muxer.stopRecording();  // 停止录制
                muxer = null;  // 清空复用器
            }
        } catch (Exception e) {
            Log.d("TAG", "RuntimeException: stop() is called immediately after start()");
        }
    }

    /**
     * 录制完成时通知监听器。
     */
    private void notifyOnDone() {
        if (cameraRecordListener == null) return;
        cameraRecordListener.onRecordComplete();  // 通知录制完成
    }

    /**
     * 发生错误时通知监听器。
     *
     * @param e 错误信息
     */
    private void notifyOnError(Exception e) {
        if (cameraRecordListener == null) return;
        cameraRecordListener.onError(e);  // 通知发生错误
    }
}

