package com.kit.video.generator.out;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.kit.video.generator.base.FrameData;
import com.kit.video.generator.base.OutputHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 音视频编码输出处理类
 */
public class MediaCodecOutputHandler implements OutputHandler {
    private static final String TAG = "MediaCodecOutputHandler";
    private VideoEncoder videoEncoder;
    private AudioEncoder audioEncoder;
    private MediaMuxerHandler muxerHandler;
    private boolean isInitialized = false;

    public MediaCodecOutputHandler(String outputPath, int outputWidth, int outputHeight, boolean needInputSurface, boolean isMute) {
        try {
            muxerHandler = new MediaMuxerHandler(outputPath);
            videoEncoder = new VideoEncoder(muxerHandler, outputWidth, outputHeight, needInputSurface);
            videoEncoder.initialize();

            if (!isMute) {
                audioEncoder = new AudioEncoder(muxerHandler);
                audioEncoder.initialize();
            }
        } catch (Exception e) {
            e.printStackTrace();
            isInitialized = true;
        }
    }

    @Override
    public boolean initialize() {
        return isInitialized;
    }

    public void writeVideoFrame(FrameData frame) {
        try {
            videoEncoder.writeFrame(frame);
            Log.e(TAG, "writeFrame=" + frame.getPts());

        } catch (Exception e) {
            Log.e(TAG, "Error writing video frame", e);
        }
    }

    public void writeAudioFrame(FrameData frame) {
        try {
            audioEncoder.writeFrame(frame);
        } catch (Exception e) {
            Log.e(TAG, "Error writing audio frame", e);
        }
    }

    public void release() {
        Log.e(TAG, "release");

        if (null != videoEncoder) {
            videoEncoder.release();
        }
        if (audioEncoder != null) {
            audioEncoder.release();
        }
        if (null != muxerHandler) {
            muxerHandler.stop();
        }
    }


    public Surface getInputSurface() {
        return videoEncoder.getInputSurface();
    }


    public long getFrameInterval() {
        return 1000000L / videoEncoder.getFrameRate();
    }
}
