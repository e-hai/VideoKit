package com.kit.video.editor;

import android.opengl.EGLContext;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraProcessingPipeline {

    private CameraInputHandler videoInput;
    private AudioRecordInputHandler audioInput;
    private MediaCodecOutputHandler output;
    private ExecutorService videoExecutor;
    private ExecutorService audioExecutor;

    public void start(EGLContext parentContext, int outputWidth, int outputHeight, String outputPath) {
        videoExecutor = Executors.newSingleThreadExecutor(); // 单一线程处理 OpenGL 操作
        audioExecutor = Executors.newSingleThreadExecutor(); // 处理数据获取和输出

        videoInput = new CameraInputHandler();
        audioInput = new AudioRecordInputHandler();
        output = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, true);

        if (!output.initialize() || !audioInput.initialize()) {
            Log.e("Pipeline", "Initialization failed");
            return;
        }

        videoExecutor.submit(() -> {
            videoInput.setEglContext(parentContext, output.getInputSurface());
            if (!videoInput.initialize()) {
                Log.e("Pipeline", "Initialization failed");
            }
        });

        audioExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                FrameData audioFrame = audioInput.getData();
                if (audioFrame != null) output.writeAudioFrame(audioFrame);
            }
        });
    }

    public void drawTexture(int textureId) {
        videoExecutor.submit(() -> {
            videoInput.draw(textureId);
            FrameData videoFrame = videoInput.getData();
            if (videoFrame != null) output.writeVideoFrame(videoFrame);
        });
    }

    public void stop() {
        if (videoExecutor != null) {
            videoExecutor.shutdownNow();
            videoExecutor = null;
        }
        if (audioExecutor != null) {
            audioExecutor.shutdownNow();
            audioExecutor = null;
        }
        if (videoInput != null) {
            videoInput.release();
            videoInput = null;
        }
        if (audioInput != null) {
            audioInput.release();
            audioInput = null;
        }
        if (output != null) {
            output.release();
            output = null;
        }
    }
}
