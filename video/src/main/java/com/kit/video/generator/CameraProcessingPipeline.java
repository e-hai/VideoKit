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

    private CameraInputHandler cameraInput;
    private AudioRecordInputHandler audioInput;
    private MediaCodecOutputHandler output;
    private ExecutorService videoExecutor;
    private ExecutorService audioExecutor;
    private volatile boolean isRunning = true;
    private MediaListener mediaListener;

    public void setMediaListener(MediaListener mediaListener) {
        this.mediaListener = mediaListener;
    }

    public void start(EGLContext parentContext, int outputWidth, int outputHeight, String outputPath) {
        initializeExecutors();
        initializeHandlers(outputPath, outputWidth, outputHeight);

        if (!initializeComponents()) {
            Log.e("Pipeline", "Initialization failed");
            shutdown();
            return;
        }
        startVideoProcessing(parentContext);
        startAudioProcessing();
    }

    private void initializeExecutors() {
        videoExecutor = Executors.newSingleThreadExecutor();
        audioExecutor = Executors.newSingleThreadExecutor();
    }

    private void initializeHandlers(String outputPath, int outputWidth, int outputHeight) {
        cameraInput = new CameraInputHandler();
        audioInput = new AudioRecordInputHandler();
        output = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, true,false);
    }

    private boolean initializeComponents() {
        return output.initialize() && audioInput.initialize();
    }

    private void startVideoProcessing(EGLContext parentContext) {
        videoExecutor.execute(() -> {
            try {
                cameraInput.setEglContext(parentContext, output.getInputSurface());
                if (cameraInput.initialize()) {
                    if (null != mediaListener) mediaListener.onStart();
                } else {
                    Log.e("Pipeline", "Video input initialization failed");
                    shutdown();
                }
            } catch (Exception e) {
                Log.e("Pipeline", "Error during video processing", e);
                shutdown();
            }
        });
    }

    private void startAudioProcessing() {
        audioExecutor.execute(() -> {
            try {
                while (isRunning) {
                    FrameData audioFrame = audioInput.getData();
                    if (audioFrame != null) output.writeAudioFrame(audioFrame);
                }
            } catch (Exception e) {
                Log.e("Pipeline", "Error during audio processing", e);
            }
        });
    }

    public void drawTexture(int textureId) {
        videoExecutor.execute(() -> {
            try {
                cameraInput.draw(textureId);
                FrameData videoFrame = cameraInput.getData();
                if (videoFrame != null && isRunning) output.writeVideoFrame(videoFrame);
            } catch (Exception e) {
                Log.e("Pipeline", "Error during drawing texture", e);
            }
        });
    }

    public void stop() {
        shutdown();
    }

    private void shutdown() {
        // Write end of stream frames
        writeEndOfStreamFrames();
        if (videoExecutor != null) videoExecutor.shutdown();
        if (audioExecutor != null) audioExecutor.shutdown();
        if (cameraInput != null) cameraInput.release();
        if (audioInput != null) audioInput.release();
        if (output != null) output.release();
    }

    private void writeEndOfStreamFrames() {
        isRunning = false;
        videoExecutor.submit(() -> {
            try {
                output.writeVideoFrame(new FrameData(true));
                if (null != mediaListener) mediaListener.onEnd();
            } catch (Exception e) {
                Log.e("Pipeline", "Error during drawing texture", e);
            }
        });

        audioExecutor.submit(() -> {
            try {
                output.writeAudioFrame(new FrameData(true));
            } catch (Exception e) {
                Log.e("Pipeline", "Error during audio processing", e);
            }
        });
    }
}
