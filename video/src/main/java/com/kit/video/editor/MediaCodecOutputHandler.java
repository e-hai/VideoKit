package com.kit.video.editor;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

/**
 * 音视频编码输出处理
 */
class MediaCodecOutputHandler implements OutputHandler {
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaMuxer muxer;
    private int videoTrackIndex;
    private int audioTrackIndex;
    private boolean isMuxerStarted;
    private final String outputPath;
    private final int outputWidth;
    private final int outputHeight;
    private Surface inputSurface;
    private boolean needInputSurface;

    public MediaCodecOutputHandler(String outputPath, int outputWidth, int outputHeight, boolean needInputSurface) {
        this.outputPath = outputPath;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.needInputSurface = needInputSurface;
    }


    @Override
    public boolean initialize() {
        try {
            String videoMineType = "video/avc";
            int frameRate = 30;
            int iFrameInterval = 1;
            float bpp = 0.25f;
            int videoBitRate = (int) (bpp * frameRate * outputWidth * outputHeight);
            MediaFormat videoFormat = MediaFormat.createVideoFormat(videoMineType, outputWidth, outputHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
            videoEncoder = MediaCodec.createEncoderByType(videoMineType);
            if (needInputSurface) {
                inputSurface = videoEncoder.createInputSurface();
            }
            videoEncoder.configure(videoFormat, inputSurface, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            String audioMineType = "audio/mp4a-latm";
            int sampleRate = 44100;
            int channelCount = 1;
            int audioBitrate = 64000;
            MediaFormat audioFormat = MediaFormat.createAudioFormat(audioMineType, sampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncoder = MediaCodec.createEncoderByType(audioMineType);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            return true;
        } catch (Exception e) {
            Log.e("MediaCodecOutputHandler", "Initialization failed", e);
            return false;
        }
    }

    @Nullable
    public Surface getInputSurface() {
        return inputSurface;
    }


    @Override
    public void writeVideoFrame(FrameData frame) {
        // 如果传入的帧数据为空，直接返回
        if (frame == null) {
            return;
        }

        try {
            // 获取视频编码器的输入缓冲区数组
            ByteBuffer[] inputBuffers = videoEncoder.getInputBuffers();
            // 获取视频编码器的输出缓冲区数组
            ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();
            // 获取可用的输入缓冲区索引
            int inputBufferIndex = videoEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                // 获取指定索引的输入缓冲区
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                // 清空输入缓冲区
                inputBuffer.clear();
                // 将帧数据写入输入缓冲区
                inputBuffer.put(frame.getByteBuffer());
                // 将输入缓冲区放入编码器队列
                videoEncoder.queueInputBuffer(inputBufferIndex, 0, frame.getByteBuffer().limit(), frame.getPts(), 0);
            }

            // 创建 BufferInfo 对象以存储输出缓冲区的信息
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            // 获取可用的输出缓冲区索引
            int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 0);
            // 循环处理所有可用的输出缓冲区
            while (outputBufferIndex >= 0) {
                // 获取指定索引的输出缓冲区
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                // 如果缓冲区包含编解码器配置数据，重置大小为 0
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                // 如果缓冲区包含有效数据
                if (bufferInfo.size != 0) {
                    // 如果混音器尚未启动，添加视频轨道并启动混音器
                    if (!isMuxerStarted) {
                        videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                        if (audioTrackIndex >= 0) {
                            muxer.start();
                            isMuxerStarted = true;
                        }
                    }
                    // 将输出缓冲区的数据写入混音器
                    muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo);
                }
                // 释放输出缓冲区
                videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                // 获取下一个可用的输出缓冲区索引
                outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            // 捕获并记录异常
            Log.e("MediaCodecOutputHandler", "Error writing video frame", e);
        }
    }

    @Override
    public void writeAudioFrame(FrameData frame) {
        // 如果传入的帧数据为空，直接返回
        if (frame == null) {
            return;
        }

        try {
            // 获取音频编码器的输入缓冲区数组
            ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
            // 获取音频编码器的输出缓冲区数组
            ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();
            // 获取可用的输入缓冲区索引
            int inputBufferIndex = audioEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                // 获取指定索引的输入缓冲区
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                // 清空输入缓冲区
                inputBuffer.clear();
                // 将帧数据写入输入缓冲区
                inputBuffer.put(frame.getByteBuffer());
                // 将输入缓冲区放入编码器队列
                audioEncoder.queueInputBuffer(inputBufferIndex, 0, frame.getByteBuffer().limit(), frame.getPts(), 0);
            }

            // 创建 BufferInfo 对象以存储输出缓冲区的信息
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            // 获取可用的输出缓冲区索引
            int outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
            // 循环处理所有可用的输出缓冲区
            while (outputBufferIndex >= 0) {
                // 获取指定索引的输出缓冲区
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                // 如果缓冲区包含编解码器配置数据，重置大小为 0
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                // 如果缓冲区包含有效数据
                if (bufferInfo.size != 0) {
                    // 如果混音器尚未启动，添加音频轨道并启动混音器
                    if (!isMuxerStarted) {
                        audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                        if (videoTrackIndex >= 0) {
                            muxer.start();
                            isMuxerStarted = true;
                        }
                    }
                    // 将输出缓冲区的数据写入混音器
                    muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo);
                }
                // 释放输出缓冲区
                audioEncoder.releaseOutputBuffer(outputBufferIndex, false);
                // 获取下一个可用的输出缓冲区索引
                outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            // 捕获并记录异常
            Log.e("MediaCodecOutputHandler", "Error writing audio frame", e);
        }
    }

    @Override
    public void release() {
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
        if (muxer != null) {
            if (isMuxerStarted) {
                muxer.stop();
            }
            muxer.release();
            muxer = null;
        }
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
    }
}

