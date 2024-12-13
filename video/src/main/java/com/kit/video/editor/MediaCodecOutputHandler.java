package com.kit.video.editor;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

/**
 * 音视频编码输出处理类
 */
class MediaCodecOutputHandler implements OutputHandler {
    private static final String TAG = MediaCodecOutputHandler.class.getSimpleName();
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private MediaMuxer muxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean isMuxerStarted;
    private final String outputPath;
    private final int outputWidth;
    private final int outputHeight;
    private Surface inputSurface;
    private final boolean needInputSurface;

    /**
     * 构造函数，初始化输出路径、宽度、高度以及是否需要输入Surface
     *
     * @param outputPath       输出文件路径
     * @param outputWidth      输出视频宽度
     * @param outputHeight     输出视频高度
     * @param needInputSurface 是否需要输入Surface
     */
    public MediaCodecOutputHandler(String outputPath, int outputWidth, int outputHeight, boolean needInputSurface) {
        this.outputPath = outputPath;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.needInputSurface = needInputSurface;
    }

    /**
     * 初始化编码器和混音器
     *
     * @return 初始化成功返回true，否则返回false
     */
    @Override
    public boolean initialize() {
        try {
            // 初始化视频编码器
            String videoMimeType = "video/avc";
            MediaCodecInfo videoCodecInfo = checkVideoCodec(videoMimeType);
            if (videoCodecInfo == null) {
                Log.e(TAG, "查找不到支持 H.264 的视频编码器");
                return false;
            }
            int frameRate = 30;
            int iFrameInterval = 1;
            float bpp = 0.25f;
            int videoBitRate = (int) (bpp * frameRate * outputWidth * outputHeight);
            MediaFormat videoFormat = MediaFormat.createVideoFormat(videoMimeType, outputWidth, outputHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
            videoEncoder = MediaCodec.createEncoderByType(videoMimeType);
            if (needInputSurface) {
                inputSurface = videoEncoder.createInputSurface();
            }
            videoEncoder.configure(videoFormat, inputSurface, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // 初始化音频编码器
            String audioMimeType = "audio/mp4a-latm";
            int sampleRate = 44100;
            int channelCount = 1;
            int audioBitrate = 64000;
            MediaFormat audioFormat = MediaFormat.createAudioFormat(audioMimeType, sampleRate, channelCount);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncoder = MediaCodec.createEncoderByType(audioMimeType);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // 初始化混音器
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Initialization failed", e);
            release();
            return false;
        }
    }

    /**
     * 查找支持特定 MIME 类型的编码器
     *
     * @param mimeType MIME 类型
     * @return 匹配的编码器信息，如果没有则返回 null
     */
    private MediaCodecInfo checkVideoCodec(final String mimeType) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] codecInfos = list.getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder()) {
                continue; // 跳过解码器
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    Log.i(TAG, "找到编码器: " + codecInfo.getName() + ", MIME=" + type);
                    int format = selectColorFormat(codecInfo, mimeType);
                    if (format > 0) {
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 选择编码器支持的颜色格式
     *
     * @param codecInfo 编解码器信息
     * @param mimeType  MIME 类型
     * @return 匹配的颜色格式，如果没有则返回 0
     */
    private int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        final MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mimeType);
        for (int colorFormat : caps.colorFormats) {
            // 检查颜色格式是否为 Surface 格式
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return colorFormat;
            }
        }
        Log.e(TAG, "无法找到合适的颜色格式: " + codecInfo.getName() + " / " + mimeType);
        return 0;
    }

    /**
     * 获取输入Surface
     *
     * @return 输入Surface，如果没有则返回null
     */
    @Nullable
    public Surface getInputSurface() {
        return inputSurface;
    }

    /**
     * 写入视频帧数据
     *
     * @param frame 视频帧数据
     */
    @Override
    public void writeVideoFrame(FrameData frame) {
        writeFrame(frame, videoEncoder, videoTrackIndex);
    }

    /**
     * 写入音频帧数据
     *
     * @param frame 音频帧数据
     */
    @Override
    public void writeAudioFrame(FrameData frame) {
        writeFrame(frame, audioEncoder, audioTrackIndex);
    }

    /**
     * 写入帧数据到指定的编码器
     *
     * @param frame      帧数据
     * @param encoder    编码器
     * @param trackIndex 轨道索引
     */
    private void writeFrame(FrameData frame, MediaCodec encoder, int trackIndex) {
        if (frame == null || encoder == null) {
            return;
        }

        try {
            // 获取可用的输入缓冲区索引
            int inputBufferIndex = encoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                // 获取指定索引的输入缓冲区
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                // 清空输入缓冲区
                inputBuffer.clear();
                // 将帧数据写入输入缓冲区
                if (!frame.isEndOfStream()) {
                    inputBuffer.put(frame.getByteBuffer());
                }
                // 将输入缓冲区放入编码器队列
                encoder.queueInputBuffer(inputBufferIndex, 0,
                        frame.getByteBuffer().limit(),
                        frame.getPts(),
                        frame.isEndOfStream() ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }

            // 创建 BufferInfo 对象以存储输出缓冲区的信息
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            // 获取可用的输出缓冲区索引
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
            // 循环处理所有可用的输出缓冲区
            while (outputBufferIndex >= 0) {
                // 获取指定索引的输出缓冲区
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                // 如果缓冲区包含编解码器配置数据，重置大小为 0
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }
                // 如果缓冲区包含有效数据
                if (bufferInfo.size != 0) {
                    // 如果混音器尚未启动，添加轨道并启动混音器
                    if (!isMuxerStarted) {
                        trackIndex = muxer.addTrack(encoder.getOutputFormat());
                        if (trackIndex >= 0) {
                            muxer.start();
                            isMuxerStarted = true;
                        }
                    }
                    // 将输出缓冲区的数据写入混音器
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                }
                // 释放输出缓冲区
                encoder.releaseOutputBuffer(outputBufferIndex, false);
                // 获取下一个可用的输出缓冲区索引
                outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing frame", e);
        }
    }

    /**
     * 释放资源
     */
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
