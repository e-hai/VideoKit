package com.kit.video.record;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;



import java.io.IOException;


/**
 * 媒体视频编码器
 * 此类用于捕获视频帧数据并通过 MediaCodec 进行 H.264 编码，然后将其写入 MediaMuxer。
 */
public class MediaVideoEncoder extends MediaEncoder {
    private static final String TAG = "MediaVideoEncoder";

    // 编码类型（MIME）
    private static final String MIME_TYPE = "video/avc"; // H.264 编码
    private static final int FRAME_RATE = 30; // 帧率
    private static final float BPP = 0.25f;  // 每像素的比特数，用于计算码率

    private final int fileWidth;  // 输出文件的宽度
    private final int fileHeight; // 输出文件的高度
    private EncodeRenderHandler encodeRenderHandler; // 渲染处理器，用于纹理绘制
    private Surface surface;  // 编码器的输入 Surface

    /**
     * 构造方法
     *
     * @param muxer          媒体混频器包装类
     * @param listener       编码器监听器
     * @param fileWidth      输出文件的宽度
     * @param fileHeight     输出文件的高度
     * @param flipHorizontal 是否水平翻转
     * @param flipVertical   是否垂直翻转
     * @param viewWidth      视图宽度
     * @param viewHeight     视图高度
     */
    public MediaVideoEncoder(final MediaMuxerCaptureWrapper muxer,
                             final MediaEncoderListener listener,
                             final int fileWidth,
                             final int fileHeight,
                             final boolean flipHorizontal,
                             final boolean flipVertical,
                             final float viewWidth,
                             final float viewHeight) {
        super(muxer, listener);
        this.fileWidth = fileWidth;
        this.fileHeight = fileHeight;
        encodeRenderHandler = EncodeRenderHandler.createHandler(
                TAG,
                flipVertical,
                flipHorizontal,
                (viewWidth > viewHeight) ? (viewWidth / viewHeight) : (viewHeight / viewWidth),
                fileWidth,
                fileHeight
        );
        Log.d(TAG, "fileWidth=" + fileWidth + " fileHeight=" + fileHeight
                + " flipHorizontal=" + flipHorizontal + " flipVertical=" + flipVertical
                + " viewWidth=" + viewWidth + " viewHeight=" + viewHeight);
    }

    /**
     * 提供帧数据并通知编码器新帧可用
     *
     * @param texName   纹理名称
     * @param mvpMatrix 模型视图投影矩阵
     */
    public void frameAvailableSoon(final int texName, final float[] mvpMatrix) {
        if (super.frameAvailableSoon()) {
            encodeRenderHandler.draw(texName, mvpMatrix);
        }
    }

    /**
     * 通知编码器新帧即将可用
     *
     * @return 是否成功通知
     */
    @Override
    public boolean frameAvailableSoon() {
        boolean result = super.frameAvailableSoon();
        if (result) {
            encodeRenderHandler.prepareDraw();
        }
        return result;
    }

    /**
     * 准备视频编码器
     *
     * @throws IOException 如果初始化失败
     */
    @Override
    protected void prepare() throws IOException {
        Log.i(TAG, "prepare: ");
        trackIndex = -1;
        muxerStarted = isEOS = false;

        // 查找支持 H.264 的视频编码器
        final MediaCodecInfo videoCodecInfo = selectVideoCodec(MIME_TYPE);

        if (videoCodecInfo == null) {
            Log.e(TAG, "无法找到支持的编码器: " + MIME_TYPE);
            return;
        }
        Log.i(TAG, "选定的编码器: " + videoCodecInfo.getName());

        // 配置视频编码器格式
        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, fileWidth, fileHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate(fileWidth, fileHeight));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3); // 每 3 秒一个关键帧
        Log.i(TAG, "视频格式: " + format);

        // 创建并配置 MediaCodec 编码器
        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        // 获取输入的 Surface（在 configure 和 start 之间调用）
        surface = mediaCodec.createInputSurface();
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
     * 设置 EGL 上下文
     *
     * @param shared_context 共享的 EGL 上下文
     * @param tex_id         纹理 ID
     */
    public void setEglContext(final EGLContext shared_context, final int tex_id) {
        encodeRenderHandler.setEglContext(shared_context, tex_id, surface);
    }

    /**
     * 释放资源
     */
    @Override
    protected void release() {
        Log.i(TAG, "release:");
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (encodeRenderHandler != null) {
            encodeRenderHandler.release();
            encodeRenderHandler = null;
        }
        super.release();
    }

    /**
     * 计算比特率
     *
     * @param width  视频宽度
     * @param height 视频高度
     * @return 比特率
     */
    private static int calcBitRate(int width, int height) {
        final int bitrate = (int) (BPP * FRAME_RATE * width * height);
        Log.i(TAG, "bitrate=" + bitrate);
        return bitrate;
    }

    /**
     * 查找支持特定 MIME 类型的编码器
     *
     * @param mimeType MIME 类型
     * @return 匹配的编码器信息，如果没有则返回 null
     */
    private static MediaCodecInfo selectVideoCodec(final String mimeType) {
        Log.v(TAG, "selectVideoCodec:");
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
    private static int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        Log.i(TAG, "选择颜色格式: ");
        int result = 0;
        final MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mimeType);

        for (int colorFormat : caps.colorFormats) {
            if (isRecognizedViewoFormat(colorFormat)) {
                result = colorFormat;
                break;
            }
        }
        if (result == 0) {
            Log.e(TAG, "无法找到合适的颜色格式: " + codecInfo.getName() + " / " + mimeType);
        }
        return result;
    }

    /**
     * 检查颜色格式是否为 Surface 格式
     *
     * @param colorFormat 颜色格式
     * @return 是否为 Surface 格式
     */
    private static boolean isRecognizedViewoFormat(final int colorFormat) {
        Log.i(TAG, "检查颜色格式: colorFormat=" + colorFormat);
        return (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    /**
     * 向编码器发送结束标志（EOS）
     */
    @Override
    protected void signalEndOfInputStream() {
        Log.d(TAG, "发送 EOS 到编码器");
        mediaCodec.signalEndOfInputStream(); // API >= 18
        isEOS = true;
    }
}


