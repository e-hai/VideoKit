
package com.kit.video.generator;

import android.opengl.EGLContext;
import android.util.Log;

import com.kit.video.generator.base.FrameData;
import com.kit.video.generator.base.MediaListener;
import com.kit.video.generator.input.AudioRecordInputHandler;
import com.kit.video.generator.input.CameraInputHandler;
import com.kit.video.generator.out.MediaCodecOutputHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProcessingPipeline {

    private CameraInputHandler cameraInput; // 摄像头输入处理器
    private AudioRecordInputHandler audioInput; // 音频输入处理器
    private MediaCodecOutputHandler output; // 媒体编解码输出处理器
    private ExecutorService videoExecutor; // 视频处理线程池
    private ExecutorService audioExecutor; // 音频处理线程池
    private volatile boolean isRunning = true; // 管道是否正在运行
    private MediaListener mediaListener; // 媒体监听器

    /**
     * 设置媒体监听器
     * @param mediaListener 媒体监听器实例
     */
    public void setMediaListener(MediaListener mediaListener) {
        this.mediaListener = mediaListener;
    }

    /**
     * 启动摄像头处理管道
     * @param parentContext 父EGL上下文
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     * @param outputPath 输出路径
     */
    public void start(EGLContext parentContext, int outputWidth, int outputHeight, String outputPath) {
        initializeExecutors(); // 初始化线程池
        initializeHandlers(outputPath, outputWidth, outputHeight); // 初始化处理器

        if (!initializeComponents()) { // 初始化组件
            Log.e("Pipeline", "Initialization failed"); // 初始化失败日志
            if (null != mediaListener) mediaListener.onError("Initialization failed"); // 通知错误
            shutdown(); // 关闭管道
            return;
        }
        startVideoProcessing(parentContext); // 启动视频处理
        startAudioProcessing(); // 启动音频处理
    }

    /**
     * 初始化线程池
     */
    private void initializeExecutors() {
        videoExecutor = Executors.newSingleThreadExecutor(); // 创建单线程视频处理线程池
        audioExecutor = Executors.newSingleThreadExecutor(); // 创建单线程音频处理线程池
    }

    /**
     * 初始化处理器
     * @param outputPath 输出路径
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     */
    private void initializeHandlers(String outputPath, int outputWidth, int outputHeight) {
        cameraInput = new CameraInputHandler(); // 创建摄像头输入处理器
        audioInput = new AudioRecordInputHandler(); // 创建音频输入处理器
        output = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, true, false); // 创建媒体编解码输出处理器
    }

    /**
     * 初始化组件
     * @return 初始化是否成功
     */
    private boolean initializeComponents() {
        return output.initialize() && audioInput.initialize(); // 初始化输出处理器和音频输入处理器
    }

    /**
     * 启动视频处理
     * @param parentContext 父EGL上下文
     */
    private void startVideoProcessing(EGLContext parentContext) {
        videoExecutor.execute(() -> { // 在视频处理线程池中执行
            try {
                cameraInput.setEglContext(parentContext, output.getInputSurface()); // 设置EGL上下文和输入表面
                if (cameraInput.initialize()) { // 初始化摄像头输入处理器
                    if (null != mediaListener) mediaListener.onStart(); // 通知开始
                } else {
                    Log.e("Pipeline", "Video input initialization failed"); // 视频输入初始化失败日志
                    shutdown(); // 关闭管道
                }
            } catch (Exception e) {
                Log.e("Pipeline", "Error during video processing", e); // 视频处理错误日志
                shutdown(); // 关闭管道
            }
        });
    }

    /**
     * 启动音频处理
     */
    private void startAudioProcessing() {
        audioExecutor.execute(() -> { // 在音频处理线程池中执行
            try {
                while (isRunning) { // 当管道正在运行时
                    FrameData audioFrame = audioInput.getData(); // 获取音频帧数据
                    if (audioFrame != null) output.writeAudioFrame(audioFrame); // 写入音频帧数据
                }
            } catch (Exception e) {
                Log.e("Pipeline", "Error during audio processing", e); // 音频处理错误日志
            }
        });
    }

    /**
     * 绘制纹理
     * @param textureId 纹理ID
     */
    public void drawTexture(int textureId) {
        videoExecutor.execute(() -> { // 在视频处理线程池中执行
            try {
                cameraInput.draw(textureId); // 绘制纹理
                FrameData videoFrame = cameraInput.getData(); // 获取视频帧数据
                if (videoFrame != null && isRunning) output.writeVideoFrame(videoFrame); // 写入视频帧数据
            } catch (Exception e) {
                Log.e("Pipeline", "Error during drawing texture", e); // 绘制纹理错误日志
            }
        });
    }

    /**
     * 停止处理管道
     */
    public void stop() {
        shutdown(); // 关闭管道
    }

    /**
     * 关闭管道
     */
    private void shutdown() {
        // 写入结束流帧
        writeEndOfStreamFrames();
        if (videoExecutor != null) videoExecutor.shutdown(); // 关闭视频处理线程池
        if (audioExecutor != null) audioExecutor.shutdown(); // 关闭音频处理线程池
        if (cameraInput != null) cameraInput.release(); // 释放摄像头输入处理器
        if (audioInput != null) audioInput.release(); // 释放音频输入处理器
        if (output != null) output.release(); // 释放媒体编解码输出处理器
    }

    /**
     * 写入结束流帧
     */
    private void writeEndOfStreamFrames() {
        isRunning = false; // 设置管道不再运行
        videoExecutor.submit(() -> { // 在视频处理线程池中执行
            try {
                FrameData endOfStreamFrame = cameraInput.getEndOfStreamData(); // 获取结束流帧数据
                output.writeVideoFrame(endOfStreamFrame); // 写入视频结束流帧数据
                if (null != mediaListener) mediaListener.onEnd(); // 通知结束
            } catch (Exception e) {
                Log.e("Pipeline", "Error during drawing texture", e); // 绘制纹理错误日志
            }
        });

        audioExecutor.submit(() -> { // 在音频处理线程池中执行
            try {
                FrameData endOfStreamFrame = audioInput.getEndOfStreamData(); // 获取结束流帧数据
                output.writeAudioFrame(endOfStreamFrame); // 写入音频结束流帧数据
            } catch (Exception e) {
                Log.e("Pipeline", "Error during audio processing", e); // 音频处理错误日志
            }
        });
    }
}