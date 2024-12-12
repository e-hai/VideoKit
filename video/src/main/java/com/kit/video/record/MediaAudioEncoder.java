package com.kit.video.record;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 媒体音频编码器
 * 此类用于捕获音频数据并通过 MediaCodec 进行 AAC 编码，然后将其写入 MediaMuxer。
 */
public class MediaAudioEncoder extends MediaEncoder {
    private static final String TAG = "MediaAudioEncoder";

    // 音频编码类型（MIME）
    private static final String MIME_TYPE = "audio/mp4a-latm";
    // 采样率，44.1kHz 是所有设备都支持的通用值
    private static final int SAMPLE_RATE = 44100;
    // 比特率
    private static final int BIT_RATE = 64000;
    // 每帧采样点数（AAC 特定值）
    private static final int SAMPLES_PER_FRAME = 1024;
    // 每缓冲区帧数
    private static final int FRAMES_PER_BUFFER = 25;

    // 音频捕获线程
    private AudioThread audioThread = null;

    /**
     * 构造方法
     *
     * @param muxer    媒体混频器包装类
     * @param listener 编码器监听器
     */
    public MediaAudioEncoder(final MediaMuxerCaptureWrapper muxer, final MediaEncoderListener listener) {
        super(muxer, listener);
    }

    /**
     * 准备音频编码器
     *
     * @throws IOException 如果初始化失败，抛出异常
     */
    @Override
    protected void prepare() throws IOException {

        Log.v(TAG, "prepare:");
        trackIndex = -1;
        muxerStarted = isEOS = false;

        // 查找支持 AAC 的音频编码器
        final MediaCodecInfo audioCodecInfo = selectAudioCodec(MIME_TYPE);
        if (audioCodecInfo == null) {
            Log.e(TAG, "无法找到支持的编码器: " + MIME_TYPE);
            return;
        }
        Log.i(TAG, "选定的编码器: " + audioCodecInfo.getName());

        // 配置音频编码器的格式
        final MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        Log.i(TAG, "音频格式: " + audioFormat);

        // 创建并配置 MediaCodec 编码器
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        Log.i(TAG, "编码器已准备就绪");

        if (listener != null) {
            try {
                listener.onPrepared(this);
            } catch (final Exception e) {
                Log.e(TAG, "prepare:", e);
            }
        }
    }

    /**
     * 开始录制
     */
    @Override
    protected void startRecording() {
        super.startRecording();

        // 启动音频捕获线程
        if (audioThread == null) {
            audioThread = new AudioThread();
            audioThread.start();
        }
    }

    /**
     * 释放资源
     */
    @Override
    protected void release() {
        audioThread = null; // 停止音频线程
        super.release(); // 调用父类的释放方法
    }

    // 可用的音频源列表
    private static final int[] AUDIO_SOURCES = new int[]{
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
    };

    /**
     * 音频捕获线程
     * 从麦克风捕获未压缩的 PCM 数据并将其传递给编码器进行编码。
     */
    private class AudioThread extends Thread {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            try {
                // 获取音频缓冲区的最小大小
                final int min_buffer_size = AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
                if (buffer_size < min_buffer_size)
                    buffer_size = ((min_buffer_size / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;

                AudioRecord audioRecord = null;
                // 尝试初始化 AudioRecord
                for (final int source : AUDIO_SOURCES) {
                    try {
                        audioRecord = new AudioRecord(source,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                buffer_size);
                        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
                            audioRecord = null;
                    } catch (final Exception e) {
                        audioRecord = null;
                    }
                    if (audioRecord != null) break;
                }

                // 如果成功初始化 AudioRecord，则开始录音
                if (audioRecord != null) {
                    try {
                        if (isCapturing) {
                            Log.v(TAG, "AudioThread: 开始录音");
                            final ByteBuffer buf = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
                            int readBytes;
                            audioRecord.startRecording();
                            try {
                                while (isCapturing && !requestStop && !isEOS) {
                                    // 读取音频数据
                                    buf.clear();
                                    readBytes = audioRecord.read(buf, SAMPLES_PER_FRAME);
                                    if (readBytes > 0) {
                                        // 将数据传递给编码器
                                        buf.position(readBytes);
                                        buf.flip();
                                        encode(buf, readBytes, getPTSUs());
                                        frameAvailableSoon();
                                    }
                                }
                                frameAvailableSoon();
                            } finally {
                                audioRecord.stop();
                            }
                        }
                    } finally {
                        audioRecord.release();
                    }
                } else {
                    Log.e(TAG, "无法初始化 AudioRecord");
                }
            } catch (final Exception e) {
                Log.e(TAG, "AudioThread#run", e);
            }
            Log.v(TAG, "AudioThread: 录音结束");
        }
    }

    /**
     * 选择第一个支持特定 MIME 类型的音频编码器
     *
     * @param mimeType MIME 类型
     * @return 支持的 MediaCodecInfo，如果未找到则返回 null
     */
    private static MediaCodecInfo selectAudioCodec(final String mimeType) {
        Log.v(TAG, "selectAudioCodec:");

        MediaCodecInfo result = null;
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] codecInfos = list.getCodecInfos();

        // 遍历所有编解码器
        LOOP:
        for (final MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder()) { // 跳过解码器
                continue;
            }
            final String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    Log.i(TAG, "编解码器: " + codecInfo.getName() + ", MIME=" + type);
                    result = codecInfo;
                    break LOOP; // 找到第一个匹配的编码器后退出
                }
            }
        }
        return result;
    }
}

