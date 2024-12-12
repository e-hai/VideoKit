package com.kit.video.record;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;


/**
 * 媒体编码器类
 * 提供基于 MediaCodec 的异步媒体编码功能，并与 MediaMuxerCaptureWrapper 协作写入媒体文件。
 * 通过子类实现具体的音频或视频编码逻辑。
 */
public abstract class MediaEncoder implements Runnable {
    private final String TAG = getClass().getSimpleName();

    // 常量：用于与 MediaCodec 交互时的超时时间，单位为微秒。
    protected static final int TIMEOUT_USEC = 10000;    // 10[msec]

    /**
     * 媒体编码器监听器，用于接收编码器的生命周期事件。
     */
    public interface MediaEncoderListener {
        void onPrepared(MediaEncoder encoder); // 编码器准备完成

        void onStopped(MediaEncoder encoder);  // 编码器停止

        void onExit(MediaEncoder encoder);     // 编码线程退出
    }

    // 同步锁，用于线程间通信
    protected final Object sync = new Object();
    // 表示编码器当前是否正在捕获数据
    protected volatile boolean isCapturing;
    // 表示是否有帧数据可用，控制数据的写入
    protected int requestDrain;
    // 请求停止捕获的标志
    protected volatile boolean requestStop;
    // 标志：编码器是否接收到流结束信号（EOS）
    protected boolean isEOS;
    // 标志：多路复用器是否已启动
    protected boolean muxerStarted;
    // 当前轨道索引，用于多路复用器
    protected int trackIndex;
    // 用于具体编码任务的 MediaCodec 实例
    protected MediaCodec mediaCodec;
    // 弱引用 MediaMuxerCaptureWrapper，避免内存泄漏
    protected final WeakReference<MediaMuxerCaptureWrapper> weakMuxer;
    // 用于存储编码缓冲区信息（如时间戳、大小等）
    private MediaCodec.BufferInfo bufferInfo;
    // 编码器生命周期事件监听器
    protected final MediaEncoderListener listener;

    /**
     * 构造方法
     * 初始化编码器并启动编码线程。
     *
     * @param muxer    媒体复用器，用于写入已编码的数据。
     * @param listener 编码器监听器，用于接收事件回调。
     */
    MediaEncoder(final MediaMuxerCaptureWrapper muxer, final MediaEncoderListener listener) {
        if (listener == null) throw new NullPointerException("MediaEncoderListener is null");
        if (muxer == null) throw new NullPointerException("MediaMuxerCaptureWrapper is null");

        // 弱引用复用器，避免内存泄漏
        weakMuxer = new WeakReference<>(muxer);
        // 将当前编码器添加到复用器
        muxer.addEncoder(this);
        this.listener = listener;

        // 初始化缓冲区信息
        synchronized (sync) {
            bufferInfo = new MediaCodec.BufferInfo();
            // 启动编码线程
            new Thread(this, getClass().getSimpleName()).start();
            try {
                // 等待线程启动
                sync.wait();
            } catch (final InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    /**
     * 通知编码线程新帧数据即将可用。
     *
     * @return 如果编码器正在捕获且未请求停止，返回 true。
     */
    public boolean frameAvailableSoon() {
        synchronized (sync) {
            if (!isCapturing || requestStop) {
                return false;
            }
            requestDrain++;
            sync.notifyAll();
        }
        return true;
    }

    /**
     * 编码线程的主循环。
     * 处理帧数据的编码、停止信号和资源释放。
     */
    @Override
    public void run() {
        synchronized (sync) {
            // 初始化线程状态
            requestStop = false;
            requestDrain = 0;
            sync.notify(); // 通知主线程线程已启动
        }
        boolean localRequestStop;
        boolean localRequestDrain;
        while (true) {
            synchronized (sync) {
                localRequestStop = requestStop;
                localRequestDrain = (requestDrain > 0);
                if (localRequestDrain) {
                    requestDrain--;
                }
            }
            //停止处理数据
            if (localRequestStop) {
                drain(); // 处理剩余数据
                signalEndOfInputStream(); // 发送流结束信号
                drain(); // 再次处理流结束后的剩余数据
                release(); // 释放资源
                break;
            }

            // 需要处理数据
            if (localRequestDrain) {
                drain();
            } else {
                synchronized (sync) {
                    try {
                        sync.wait(); // 等待新数据
                    } catch (final InterruptedException e) {
                        break;
                    }
                }
            }
        }
        Log.d(TAG, "Encoder thread exiting");
        synchronized (sync) {
            requestStop = true;
            isCapturing = false;
        }
        listener.onExit(this); // 通知监听器线程退出
    }

    /**
     * 子类需要实现的抽象方法，用于初始化编码器。
     *
     * @throws IOException 初始化失败时抛出异常。
     */
    abstract void prepare() throws IOException;

    /**
     * 启动录制
     * 设置标志并通知编码线程开始工作。
     */
    void startRecording() {
        Log.v(TAG, "startRecording");
        synchronized (sync) {
            isCapturing = true;
            requestStop = false;
            sync.notifyAll();
        }
    }

    /**
     * 请求停止录制。
     * 设置停止标志并通知线程，但不阻塞调用线程。
     */
    void stopRecording() {
        Log.v(TAG, "stopRecording");
        synchronized (sync) {
            if (!isCapturing || requestStop) {
                return;
            }
            requestStop = true; // 防止新数据进入队列
            sync.notifyAll();
        }
    }

    /**
     * 释放所有相关资源。
     * 停止编码器、多路复用器，并清理引用。
     */
    protected void release() {
        Log.d(TAG, "release:");
        try {
            listener.onStopped(this); // 通知监听器编码器停止
        } catch (final Exception e) {
            Log.e(TAG, "failed onStopped", e);
        }
        isCapturing = false;
        if (mediaCodec != null) {
            try {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
            } catch (final Exception e) {
                Log.e(TAG, "failed releasing MediaCodec", e);
            }
        }
        if (muxerStarted) {
            final MediaMuxerCaptureWrapper muxer = weakMuxer != null ? weakMuxer.get() : null;
            if (muxer != null) {
                try {
                    muxer.stop(); // 停止多路复用器
                } catch (final Exception e) {
                    Log.e(TAG, "failed stopping muxer", e);
                }
            }
        }
        bufferInfo = null; // 清理缓冲区信息
    }

    /**
     * 发送流结束信号。
     * 通常用于视频编码以结束编码流。
     */
    protected void signalEndOfInputStream() {
        Log.d(TAG, "sending EOS to encoder");
        // signalEndOfInputStream仅适用于带有surface的视频编码
        // 和等效的发送一个带有BUFFER_FLAG_END_OF_STREAM标志的空缓冲区。
        encode(null, 0, getPTSUs());
    }

    /**
     * 将数据传入编码器。
     *
     * @param buffer             数据缓冲区，为 null 表示结束流。
     * @param length             数据长度，为 0 表示结束流。
     * @param presentationTimeUs 时间戳。
     */
    protected void encode(final ByteBuffer buffer, final int length, final long presentationTimeUs) {
        if (!isCapturing) return;
        while (isCapturing) {
            final int inputBufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                if (buffer != null) {
                    inputBuffer.put(buffer);
                }
                if (length <= 0) {
                    // send EOS
                    isEOS = true;
                    Log.i(TAG, "send BUFFER_FLAG_END_OF_STREAM");
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    break;
                } else {
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTimeUs, 0);
                }
                break;
            }
        }
    }

    /**
     * 获取已编码数据并写入多路复用器。
     * 确保时间戳单调递增。
     */
    private void drain() {
        if (mediaCodec == null) return;
        int encoderStatus, count = 0;
        final MediaMuxerCaptureWrapper muxer = weakMuxer.get();
        if (muxer == null) {
            Log.w(TAG, "muxer is unexpectedly null");
            return;
        }
        LOOP:
        while (isCapturing) {
            // 检查 Surface 中是否有新的数据，当前没有数据可用，dequeueOutputBuffer() 会返回 INFO_TRY_AGAIN_LATER，因此轮询5次以确保拿到数据
            // 获取最大超时时间为TIMEOUT_USEC(=10[msec])的编码数据
            encoderStatus = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 等待5次(=TIMEOUT_USEC x 5 = 50msec)，直到数据/EOS到来
                if (!isEOS) {
                    if (++count > 5)
                        break LOOP;        // 过了一会儿
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.v(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                //此状态表示编解码器的输出格式发生了变化
                //这应该只在实际编码数据之前出现一次
                //但是这个状态在Android4.3或更低版本上不会出现
                //在这种情况下，你应该处理当MediaCodec。BUFFER_FLAG_CODEC_CONFIG来。
                if (muxerStarted) {    //第二次请求错误
                    throw new RuntimeException("format changed twice");
                }
                // 从编解码器获取输出格式并将其传递给混音器
                // getOutputFormat应该在INFO_OUTPUT_FORMAT_CHANGED之后调用，否则会崩溃。
                final MediaFormat format = mediaCodec.getOutputFormat(); // API >= 16
                trackIndex = muxer.addTrack(format);
                muxerStarted = true;
                if (!muxer.start()) {
                    //我们应该等到莫瑟准备好了
                    synchronized (muxer) {
                        while (!muxer.isStarted())
                            try {
                                muxer.wait(100);
                            } catch (final InterruptedException e) {
                                break LOOP;
                            }
                    }
                }
            } else if (encoderStatus < 0) {
                //意想不到的状况
                Log.w(TAG, "drain:unexpected result from encoder#dequeueOutputBuffer: " + encoderStatus);
            } else {
                final ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
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
                    count = 0;
                    if (!muxerStarted) {
                        // Muxer还没准备好…这将导致编程失败。
                        throw new RuntimeException("drain:muxer hasn't started");
                    }
                    //写入编码数据到混频器(需要调整presentationTimeUs)。
                    bufferInfo.presentationTimeUs = getPTSUs();
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                    prevOutputPTSUs = bufferInfo.presentationTimeUs;
                }
                // 将缓冲区返回给编码器
                mediaCodec.releaseOutputBuffer(encoderStatus, false);
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // 当EOS来的时候。
                    isCapturing = false;
                    break;      // out of while
                }
            }
        }
    }

    /**
     * 上一帧的时间戳
     */
    private long prevOutputPTSUs = 0;

    /**
     * 获取下一帧的时间戳，确保单调递增。
     *
     * @return 时间戳（单位：微秒）。
     */
    long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        //时间应该是单调的
        //否则muxer写入失败
        if (result < prevOutputPTSUs)
            result = (prevOutputPTSUs - result) + result;
        return result;
    }
}

