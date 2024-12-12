package com.kit.video.record;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * 媒体混频器捕获包装类
 * 此类使用 MediaMuxer 处理音视频流的混合及文件输出，支持音频和视频编码器的交互。
 */
public class MediaMuxerCaptureWrapper {
    private static final String TAG = "MediaMuxerWrapper"; // 日志标签

    // 媒体混频器实例
    private final MediaMuxer mediaMuxer;
    // 编码器数量（音频 + 视频）
    private int encoderCount;
    // 已启动的编码器数量
    private int startedCount;
    // 标志：混频器是否已启动
    private boolean isStarted;
    // 视频和音频编码器
    private MediaEncoder videoEncoder, audioEncoder;
    // 用于防止音频时间戳回退的变量
    private long preventAudioPresentationTimeUs = -1;
    // 音频轨道索引
    private int audioTrackIndex = -1;

    /**
     * 构造方法：初始化 MediaMuxer。
     *
     * @param filePath 输出文件路径
     * @throws IOException 如果初始化失败，抛出异常
     */
    public MediaMuxerCaptureWrapper(final String filePath) throws IOException {
        // 创建一个 MediaMuxer 实例，指定输出路径和格式
        mediaMuxer = new MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        encoderCount = startedCount = 0;
        isStarted = false; // 初始状态为未启动
    }

    /**
     * 准备所有已分配的编码器（视频和音频）。
     * 调用各自编码器的 `prepare` 方法。
     *
     * @throws IOException 如果准备失败，抛出异常
     */
    public void prepare() throws IOException {
        if (videoEncoder != null) {
            videoEncoder.prepare();
        }
        if (audioEncoder != null) {
            audioEncoder.prepare();
        }
    }

    /**
     * 开始录制。
     * 调用各自编码器的 `startRecording` 方法。
     */
    public void startRecording() {
        if (videoEncoder != null) {
            videoEncoder.startRecording();
        }
        if (audioEncoder != null) {
            audioEncoder.startRecording();
        }
    }

    /**
     * 停止录制。
     * 调用各自编码器的 `stopRecording` 方法并清除引用。
     */
    public void stopRecording() {
        if (videoEncoder != null) {
            videoEncoder.stopRecording();
        }
        videoEncoder = null; // 清除视频编码器引用
        if (audioEncoder != null) {
            audioEncoder.stopRecording();
        }
        audioEncoder = null; // 清除音频编码器引用
    }

    /**
     * 检查混频器是否已启动。
     *
     * @return true 表示已启动；false 表示未启动
     */
    public synchronized boolean isStarted() {
        return isStarted;
    }


    /**
     * 为混频器分配编码器（视频或音频）。
     * 被编码器调用以注册到当前混频器实例。
     *
     * @param encoder 视频或音频编码器实例
     * @throws IllegalArgumentException 如果重复添加编码器或不支持的类型
     */
    void addEncoder(final MediaEncoder encoder) {
        if (encoder instanceof MediaVideoEncoder) {
            if (videoEncoder != null)
                throw new IllegalArgumentException("视频编码器已存在。");
            videoEncoder = encoder;
        } else if (encoder instanceof MediaAudioEncoder) {
            if (audioEncoder != null)
                throw new IllegalArgumentException("音频编码器已存在。");
            audioEncoder = encoder;
        } else
            throw new IllegalArgumentException("不支持的编码器类型。");

        // 更新编码器数量
        encoderCount = (videoEncoder != null ? 1 : 0) + (audioEncoder != null ? 1 : 0);
    }

    /**
     * 请求启动混频器以开始写入数据。
     * 所有编码器准备完毕后调用。
     *
     * @return true 表示混频器已启动；false 表示尚未准备好
     */
    synchronized boolean start() {
        Log.v(TAG, "start:");
        startedCount++; // 增加已启动的编码器计数
        if ((encoderCount > 0) && (startedCount == encoderCount)) {
            mediaMuxer.start(); // 启动 MediaMuxer
            isStarted = true; // 更新启动状态
            notifyAll(); // 通知所有等待的线程
            Log.v(TAG, "MediaMuxer 已启动");
        }
        return isStarted;
    }

    /**
     * 请求停止混频器写入。
     * 当所有编码器接收到 EOS（流结束信号）时调用。
     */
    synchronized void stop() {
        Log.v(TAG, "stop:startedCount=" + startedCount);
        startedCount--; // 减少已启动的编码器计数
        if ((encoderCount > 0) && (startedCount <= 0)) {
            mediaMuxer.stop(); // 停止 MediaMuxer
            mediaMuxer.release(); // 释放资源
            isStarted = false; // 更新状态
            Log.v(TAG, "MediaMuxer 已停止");
        }
    }

    /**
     * 添加一个轨道到混频器。
     * 此方法由编码器调用。
     *
     * @param format 编码器的媒体格式
     * @return 返回轨道索引，负值表示出错
     * @throws IllegalStateException 如果混频器已启动
     */
    synchronized int addTrack(final MediaFormat format) {
        if (isStarted) {
            throw new IllegalStateException("混频器已启动，无法添加轨道。");
        }

        final int trackIx = mediaMuxer.addTrack(format); // 添加轨道
        Log.i(TAG, "addTrack:trackNum=" + encoderCount + ",trackIx=" + trackIx + ",format=" + format);

        // 如果是音频轨道，记录其索引
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (!mime.startsWith("video/")) {
            audioTrackIndex = trackIx;
        }
        return trackIx;
    }

    /**
     * 将编码器输出的数据写入混频器。
     *
     * @param trackIndex 轨道索引
     * @param byteBuf    编码后的数据缓冲区
     * @param bufferInfo 数据的相关信息（如时间戳）
     */
    synchronized void writeSampleData(final int trackIndex, final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo) {
        if (startedCount <= 0) return; // 如果没有活动的编码器，则直接返回

        if (audioTrackIndex == trackIndex) { // 如果是音频轨道
            if (preventAudioPresentationTimeUs < bufferInfo.presentationTimeUs) {
                mediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo); // 写入数据
                preventAudioPresentationTimeUs = bufferInfo.presentationTimeUs; // 更新时间戳
            }
        } else { // 如果是视频轨道
            mediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo); // 直接写入数据
        }
    }
}


