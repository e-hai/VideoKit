package com.kit.video.generator;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.kit.video.generator.base.FrameData;
import com.kit.video.generator.base.MediaListener;
import com.kit.video.generator.out.MediaCodecOutputHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewProcessingPipeline extends FrameLayout {
    private static final String TAG = "ViewProcessingPipeline";
    private MediaCodecOutputHandler outputHandler;
    private volatile boolean isRunning = false;
    private MediaListener mediaListener;
    private ExecutorService executorService;
    private FrameData frameData; // 重用 FrameData 实例
    private long lastTimestampUs = 0;  // 上一帧的时间戳（微秒）
    private long frameIntervalUs = 1000000L / 60; // 每帧间隔（30fps，单位为微秒）


    public ViewProcessingPipeline(@NonNull Context context) {
        super(context);
        init();
    }

    public ViewProcessingPipeline(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ViewProcessingPipeline(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public ViewProcessingPipeline(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }


    private void init() {
        setWillNotDraw(false);
        frameData = new FrameData(0, 0); // 初始化 FrameData 实例
    }


    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);
        if (outputHandler != null && isRunning) {
            if (null != onFrameCapturedListener) {
                onFrameCapturedListener.onPause();
            }
            executorService.execute(() -> {
                Surface outputSurface = outputHandler.getInputSurface();
                if (outputSurface == null && outputSurface.isValid()) {
                    Log.e(TAG, "Output surface is null");
                    return;
                }
                Canvas surfaceCanvas = null;
                try {
                    surfaceCanvas = outputSurface.lockCanvas(null);
                    if (surfaceCanvas != null) {
                        super.draw(surfaceCanvas);
                        // 计算时间戳
                        long timestampNs = lastTimestampUs + frameIntervalUs;
                        frameData.setPts(timestampNs);
                        lastTimestampUs = timestampNs;  // 更新最后的时间戳
                        writeVideoFrame(frameData);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while locking or drawing on surface canvas", e);
                } finally {
                    if (surfaceCanvas != null) {
                        outputSurface.unlockCanvasAndPost(surfaceCanvas);
                    }
                    if (null != onFrameCapturedListener) {
                        post(() -> onFrameCapturedListener.onResume());
                    }
                }
            });
        }
    }


    public void setMediaListener(MediaListener mediaListener) {
        this.mediaListener = mediaListener;
    }

    public void start(String outputPath) {
        if (isRunning) return;
        isRunning = true;
        executorService = Executors.newSingleThreadExecutor();
        startOutput(outputPath, getWidth(), getHeight());
    }

    private void startOutput(String outputPath, int outputWidth, int outputHeight) {
        if (outputHandler == null) {
            outputHandler = new MediaCodecOutputHandler(outputPath, outputWidth, outputHeight, true, true);
            outputHandler.initialize();
            frameIntervalUs = outputHandler.getFrameInterval();
        }
        if (mediaListener != null) mediaListener.onStart();
    }


    public void stop() {
        if (!isRunning) return;
        if (outputHandler != null) {
            executorService.execute(() -> {
                // Write end of stream frame
                // 计算时间戳
                long timestampNs = lastTimestampUs + frameIntervalUs;
                FrameData endOfStreamFrame = new FrameData(true, timestampNs);
                writeVideoFrame(endOfStreamFrame);
                // Wait for all frames to be processed and written
                outputHandler.release();
                outputHandler = null;
            });
        }
        if (mediaListener != null) mediaListener.onEnd();
        executorService.shutdown();
        executorService = null;
        isRunning = false;
    }


    private void writeVideoFrame(FrameData frameData) {
        if (outputHandler != null) {
            outputHandler.writeVideoFrame(frameData);
        }
    }


    public interface OnFrameCapturedListener {
        void onPause();

        void onResume();
    }

    private OnFrameCapturedListener onFrameCapturedListener;

    public void setOnFrameCapturedListener(OnFrameCapturedListener onFrameCapturedListener) {
        this.onFrameCapturedListener = onFrameCapturedListener;
    }
}
