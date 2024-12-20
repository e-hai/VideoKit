package com.kit.video.generator.input;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.kit.video.generator.base.FrameData;
import com.kit.video.generator.base.InputHandler;

import java.nio.ByteBuffer;

/**
 * 音频输入处理（从视频文件中提取音频数据）
 */
public class AudioFileInputHandler implements InputHandler {
    private MediaExtractor extractor;
    private MediaCodec decoder;
    private final String inputPath;
    private boolean endOfStream;

    public AudioFileInputHandler(String inputPath) {
        this.inputPath = inputPath;
    }

    @Override
    public boolean initialize() {
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath); // 替换为实际路径
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(format, null, null, 0);
                    decoder.start();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e("AudioInputHandler", "Initialization failed", e);
            return false;
        }
    }

    @Override
    public FrameData getData() {
        // 如果已经到达流的末尾
        if (endOfStream) return null;

        int inIndex = decoder.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            ByteBuffer buffer = decoder.getInputBuffer(inIndex);
            int sampleSize = extractor.readSampleData(buffer, 0);
            long presentationTimeUs = extractor.getSampleTime();
            if (sampleSize < 0) {
                decoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                endOfStream = true;
            } else {
                decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                extractor.advance();
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
        if (outIndex >= 0) {
            ByteBuffer buffer = decoder.getOutputBuffer(outIndex);
            FrameData frame;
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                frame = new FrameData(buffer, info.presentationTimeUs);
            } else {
                frame = new FrameData(true, info.presentationTimeUs);
            }
            decoder.releaseOutputBuffer(outIndex, false);
            return frame;
        }

        return null;
    }

    @Override
    public void release() {
        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
        if (extractor != null) {
            extractor.release();
        }
    }
}
