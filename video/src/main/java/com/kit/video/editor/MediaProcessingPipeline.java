package com.kit.video.editor;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 主处理逻辑
 */
public class MediaProcessingPipeline {
    public void start(String inputPath) {
        int outputWidth = 640;
        int outputHeight = 480;
        String outputPath = "/path/to/output.mp4";

        InputHandler videoInput = new VideoFileInputHandler(inputPath);
        InputHandler audioInput = new AudioFileInputHandler(inputPath);
        OutputHandler output = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, false);

        if (!videoInput.initialize() || !audioInput.initialize() || !output.initialize()) {
            Log.e("Pipeline", "Initialization failed");
            return;
        }

        // 异步线程池
        ExecutorService executor = Executors.newFixedThreadPool(3);
        BlockingQueue<FrameData> videoQueue = new LinkedBlockingQueue<>();
        BlockingQueue<FrameData> audioQueue = new LinkedBlockingQueue<>();

        executor.submit(() -> {
            while (true) {
                FrameData frame = videoInput.getData();
                if (frame != null) videoQueue.offer(frame);
            }
        });

        executor.submit(() -> {
            while (true) {
                FrameData frame = audioInput.getData();
                if (frame != null) audioQueue.offer(frame);
            }
        });

        executor.submit(() -> {
            while (true) {
                FrameData videoFrame = videoQueue.poll();
                FrameData audioFrame = audioQueue.poll();

                if (videoFrame != null) output.writeVideoFrame(videoFrame);
                if (audioFrame != null) output.writeAudioFrame(audioFrame);
            }
        });
    }
}
