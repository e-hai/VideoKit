package com.kit.video.editor;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * 视频文件输入处理（字节数据）
 */
class VideoFileInputHandler implements InputHandler {
    private MediaExtractor extractor;
    private MediaCodec decoder;
    private int videoTrackIndex;
    private boolean endOfStream;
    private final String inputPath;

    public VideoFileInputHandler(String inputPath){
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
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                    extractor.selectTrack(videoTrackIndex);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(format, null, null, 0);
                    decoder.start();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e("VideoFileInputHandler", "Initialization failed", e);
            return false;
        }
    }

    @Override
    public FrameData getData() {
        // 如果已经到达流的末尾，返回 null
        if (endOfStream) return null;

        // 获取解码器的输入缓冲区索引
        int inIndex = decoder.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            // 获取输入缓冲区
            ByteBuffer buffer = decoder.getInputBuffer(inIndex);
            // 从提取器中读取样本数据到缓冲区
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                // 如果样本大小小于 0，表示没有更多数据，标记为流结束
                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                endOfStream = true;
            } else {
                // 获取样本的时间戳
                long presentationTimeUs = extractor.getSampleTime();
                // 将样本数据放入解码器的输入缓冲区
                decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0);
                // 移动提取器到下一个样本
                extractor.advance();
            }
        }

        // 创建 BufferInfo 对象以存储输出缓冲区的信息
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        // 获取解码器的输出缓冲区索引
        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
        if (outIndex >= 0) {
            // 获取输出缓冲区
            ByteBuffer buffer = decoder.getOutputBuffer(outIndex);
            // 创建 FrameData 对象，包含缓冲区和时间戳
            FrameData frame = new FrameData(buffer, info.presentationTimeUs);
            // 释放输出缓冲区
            decoder.releaseOutputBuffer(outIndex, false);
            // 返回 FrameData 对象
            return frame;
        }

        // 如果没有可用的输出缓冲区，返回 null
        return null;
    }

    @Override
    public void release() {
        decoder.stop();
        decoder.release();
        extractor.release();
    }
}
