package com.kit.video.editor;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 主处理逻辑
 */
public class FileProcessingPipeline {
    private static final String TAG = "FileProcessingPipeline";
    private final ExecutorService executor;
    private final BlockingQueue<FrameData> videoQueue;
    private final BlockingQueue<FrameData> audioQueue;
    private volatile boolean isRunning = true;

    public FileProcessingPipeline() {
        this.executor = Executors.newFixedThreadPool(3);
        this.videoQueue = new LinkedBlockingQueue<>();
        this.audioQueue = new LinkedBlockingQueue<>();
    }

    public void start(String inputPath, String outputPath) {
        int[] videoDimensions = getVideoDimensions(inputPath);
        if (videoDimensions == null) {
            Log.e(TAG, "Failed to retrieve video dimensions");
            return;
        }
        int outputWidth = videoDimensions[0];
        int outputHeight = videoDimensions[1];

        InputHandler videoInput = new VideoFileInputHandler(inputPath);
        InputHandler audioInput = new AudioFileInputHandler(inputPath);
        OutputHandler output = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, false);

        try {
            if (!videoInput.initialize() || !audioInput.initialize() || !output.initialize()) {
                Log.e(TAG, "Initialization failed");
                return;
            }

            submitTasks(videoInput, audioInput, output);

        } catch (Exception e) {
            Log.e(TAG, "Error during initialization", e);
        }
    }

    private int[] getVideoDimensions(String inputPath) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(inputPath);
            String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (widthStr != null && heightStr != null) {
                int width = Integer.parseInt(widthStr);
                int height = Integer.parseInt(heightStr);
                return new int[]{width, height};
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving video dimensions", e);
        }
        return null;
    }

    private void submitTasks(InputHandler videoInput, InputHandler audioInput, OutputHandler output) {
        executor.submit(() -> processData(videoInput, videoQueue));
        executor.submit(() -> processData(audioInput, audioQueue));
        executor.submit(() -> processOutput(output));
    }

    private void processData(InputHandler inputHandler, BlockingQueue<FrameData> queue) {
        while (isRunning) {
            FrameData frame = inputHandler.getData();
            if (frame != null) {
                queue.offer(frame);
            }
        }
        inputHandler.release();
    }

    private void processOutput(OutputHandler output) {
        while (isRunning) {
            FrameData videoFrame = videoQueue.poll();
            FrameData audioFrame = audioQueue.poll();

            if (videoFrame != null) {
                output.writeVideoFrame(videoFrame);
                if (videoFrame.isEndOfStream()) {
                    isRunning = false;
                    Log.d(TAG, "视频编码结束");
                    break;
                }
            }
            if (audioFrame != null) {
                output.writeAudioFrame(audioFrame);
            }
        }
        // 关闭资源
        output.release();
    }


    public void stop() {
        isRunning = false;
        executor.shutdown(); // Attempt to stop all actively executing tasks
        Log.d(TAG, "Stopping pipeline...");
    }
}
