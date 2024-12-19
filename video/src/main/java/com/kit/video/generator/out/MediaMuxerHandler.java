package com.kit.video.generator.out;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaMuxerHandler {
    private MediaMuxer muxer;
    private boolean isMuxerStarted;

    public MediaMuxerHandler(String outputPath) throws IOException {
        muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public int addVideoTrack(MediaFormat videoFormat) {
        return muxer.addTrack(videoFormat);
    }

    public int addAudioTrack(MediaFormat audioFormat) {
        return muxer.addTrack(audioFormat);
    }

    public void startMuxing() {
        if (!isMuxerStarted) {
            muxer.start();
            isMuxerStarted = true;
        }
    }

    public void writeSampleData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        if (isMuxerStarted) {
            muxer.writeSampleData(trackIndex, buffer, bufferInfo);
        }
    }

    public void stop() {
        if (isMuxerStarted && muxer != null) {
            try {
                muxer.stop();
                muxer.release();
                muxer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}