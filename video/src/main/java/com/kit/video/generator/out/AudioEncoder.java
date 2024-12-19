package com.kit.video.generator.out;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.kit.video.generator.base.FrameData;

import java.nio.ByteBuffer;

public class AudioEncoder implements MediaCodecEncoder {
    private static final String TAG = AudioEncoder.class.getSimpleName();
    private static final int TIMEOUT_USEC = 10000;    // 10[msec]
    private MediaCodec audioEncoder;
    private int trackIndex;
    private final MediaMuxerHandler muxerHandler;

    public AudioEncoder(MediaMuxerHandler muxerHandler) {
        this.muxerHandler = muxerHandler; // 传入 muxerHandler
    }

    @Override
    public void initialize() throws Exception {
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
        audioEncoder.start();
    }

    @Override
    public void writeFrame(FrameData frame) throws Exception {
        if (frame == null || audioEncoder == null) return;

        int inputBufferIndex = audioEncoder.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
            assert inputBuffer != null;
            inputBuffer.clear();
            if (!frame.isEndOfStream()) {
                inputBuffer.put(frame.getByteBuffer());
            }
            audioEncoder.queueInputBuffer(inputBufferIndex, 0, frame.getByteBuffer().limit(), frame.getPts(),
                    frame.isEndOfStream() ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
        }

        // 处理编码后的输出
        processEncodedFrame(frame, audioEncoder);
    }

    @Override
    public void release() {
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
    }

    @Override
    public MediaCodec getEncoder() {
        return audioEncoder;
    }

    private void processEncodedFrame(FrameData frameData, MediaCodec encoder) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int waitCount = 0;

        LOOP:
        while (true) {
            // 检查 Surface 中是否有新的数据，当前没有数据可用，dequeueOutputBuffer() 会返回 INFO_TRY_AGAIN_LATER，因此轮询5次以确保拿到数据
            // 获取最大超时时间为TIMEOUT_USEC(=10[msec])的编码数据
            int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 等待5次(=TIMEOUT_USEC x 5 = 50msec)，直到数据/EOS到来
                if (!frameData.isEndOfStream()) {
                    if (++waitCount > 5) break LOOP;
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                // 从编解码器获取输出格式并将其传递给混音器
                // getOutputFormat应该在INFO_OUTPUT_FORMAT_CHANGED之后调用，否则会崩溃。
                final MediaFormat format = encoder.getOutputFormat(); // API >= 16
                trackIndex = muxerHandler.addAudioTrack(format);
                muxerHandler.startMuxing();
            } else if (encoderStatus < 0) {
                //意想不到的状况
                Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                final ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    // 这不应该发生……可能是MediaCodec内部错误
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    //当你的目标是Android4.3或更低版本时，你应该在这里设置输出格式为muxer
                    //但是MediaCodec#getOutputFormat不能在这里调用(因为INFO_OUTPUT_FORMAT_CHANGED还没有来)
                    //因此，我们应该扩展并准备缓冲区数据的输出格式。
                    //此示例适用于API>=18(>=Android 4.3)，此处忽略此标志
                    Log.d(TAG, "drain:BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    // 编码数据已准备好，清除等待计数器
                    waitCount = 0;
                    //写入编码数据到混频器(需要调整presentationTimeUs)。
                    bufferInfo.presentationTimeUs = frameData.getPts();
                    muxerHandler.writeSampleData(trackIndex, encodedData, bufferInfo);
                }
                // 将缓冲区返回给编码器
                encoder.releaseOutputBuffer(encoderStatus, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // 当EOS来的时候。
                    break LOOP;
                }
            }
        }
    }
}

