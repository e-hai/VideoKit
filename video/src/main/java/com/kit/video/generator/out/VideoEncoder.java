package com.kit.video.generator.out;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.kit.video.generator.base.FrameData;

import java.nio.ByteBuffer;

public class VideoEncoder implements MediaCodecEncoder {
    private static final String TAG = VideoEncoder.class.getSimpleName();
    private static final int TIMEOUT_USEC = 10000;    // 10[msec]
    private MediaCodec videoEncoder;
    private Surface inputSurface;
    private int trackIndex;
    private final boolean needInputSurface;
    private final MediaMuxerHandler muxerHandler;
    private final int outputWidth;
    private final int outputHeight;
    int frameRate;
    int iFrameInterval;
    int videoBitRate;

    public VideoEncoder(MediaMuxerHandler muxerHandler, int outputWidth, int outputHeight, boolean needInputSurface) {
        this.needInputSurface = needInputSurface;
        this.muxerHandler = muxerHandler; // 传入 muxerHandler
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
    }

    @Override
    public void initialize() throws Exception {
        String videoMimeType = "video/avc";
        MediaCodecInfo videoCodecInfo = checkVideoCodec(videoMimeType);
        if (videoCodecInfo == null) {
            throw new RuntimeException("Unable to find H.264 video encoder");
        }
        frameRate = 60;
        iFrameInterval = 1;
        videoBitRate = (int) (0.25f * frameRate * outputWidth * outputHeight);
        MediaFormat videoFormat = MediaFormat.createVideoFormat(videoMimeType, outputWidth, outputHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);

        videoEncoder = MediaCodec.createEncoderByType(videoMimeType);
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (needInputSurface) {
            inputSurface = videoEncoder.createInputSurface();
        }
        videoEncoder.start();
    }

    @Override
    public void writeFrame(FrameData frame) throws Exception {
        if (frame == null || videoEncoder == null) return;

        if (needInputSurface) {
            if (frame.isEndOfStream()) {
                videoEncoder.signalEndOfInputStream();
                Log.d(TAG, "signalEndOfInputStream");
            }
        } else {
            int inputBufferIndex = videoEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = videoEncoder.getInputBuffer(inputBufferIndex);
                assert inputBuffer != null;
                inputBuffer.clear();
                if (!frame.isEndOfStream()) {
                    inputBuffer.put(frame.getByteBuffer());
                }
                videoEncoder.queueInputBuffer(inputBufferIndex, 0, frame.getByteBuffer().limit(), frame.getPts(),
                        frame.isEndOfStream() ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }
        }

        // 处理编码后的输出
        processEncodedFrame(frame, videoEncoder);
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
                Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                // 等待5次(=TIMEOUT_USEC x 5 = 50msec)，直到数据/EOS到来
                if (!frameData.isEndOfStream()) {
                    ++waitCount;
                    if (waitCount > 5) {
                        Log.d(TAG, "break loop");
                        break LOOP;
                    }
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                // 从编解码器获取输出格式并将其传递给混音器
                // getOutputFormat应该在INFO_OUTPUT_FORMAT_CHANGED之后调用，否则会崩溃。
                final MediaFormat format = encoder.getOutputFormat(); // API >= 16
                trackIndex = muxerHandler.addVideoTrack(format);
                muxerHandler.startMuxing();
            } else if (encoderStatus < 0) {
                //意想不到的状况
                Log.d(TAG, "unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                Log.d(TAG, "getOutputBuffer");

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
                    Log.d(TAG, "BUFFER_FLAG_CODEC_CONFIG");
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {
                    // 编码数据已准备好，清除等待计数器
                    waitCount = 0;
                    //写入编码数据到混频器(需要调整presentationTimeUs)。
                    bufferInfo.presentationTimeUs = frameData.getPts();
                    Log.w(TAG, frameData.isEndOfStream() + "=writeSampleData=" + frameData.getPts());
                    muxerHandler.writeSampleData(trackIndex, encodedData, bufferInfo);
                }
                // 将缓冲区返回给编码器
                encoder.releaseOutputBuffer(encoderStatus, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // 当EOS来的时候。
                    Log.d(TAG, "BUFFER_FLAG_END_OF_STREAM");
                    break LOOP;
                }
            }
        }
    }

    @Override
    public void release() {
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
    }

    @Override
    public MediaCodec getEncoder() {
        return videoEncoder;
    }

    public Surface getInputSurface() {
        return inputSurface;
    }

    public int getFrameRate() {
        return frameRate;
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

}
