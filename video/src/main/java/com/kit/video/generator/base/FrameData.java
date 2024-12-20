package com.kit.video.generator.base;

import java.nio.ByteBuffer;

/**
 * 数据帧封装
 */
public class FrameData {
    // 视频帧数据
    ByteBuffer data;

    // 呈现时间戳，单位：微秒
    long pts;

    // 纹理ID，如果是纹理数据，则使用该字段
    int textureId;

    // 是否为流结束标记
    boolean endOfStream;

    /**
     * 构造函数，用于初始化带有字节缓冲区的数据帧
     *
     * @param data 视频帧数据
     * @param pts  呈现时间戳，单位：微秒
     */
    public FrameData(ByteBuffer data, long pts) {
        this.data = data;
        this.pts = pts;
    }

    /**
     * 构造函数，用于初始化带有纹理ID的数据帧
     *
     * @param textureId 纹理ID
     * @param pts       呈现时间戳，单位：微秒
     */
    public FrameData(int textureId, long pts) {
        this.textureId = textureId;
        this.pts = pts;
    }

    /**
     * 构造函数，用于初始化流结束标记
     *
     * @param endOfStream 是否为流结束标记
     */
    public FrameData(boolean endOfStream, long pts) {
        this.endOfStream = endOfStream;
        this.pts = pts;
    }

    /**
     * 获取视频帧数据
     *
     * @return 视频帧数据
     */
    public ByteBuffer getByteBuffer() {
        return data;
    }

    /**
     * 获取纹理ID
     *
     * @return 纹理ID
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * 获取呈现时间戳
     *
     * @return 呈现时间戳，单位：微秒
     */
    public long getPts() {
        return pts;
    }

    /**
     * 设置呈现时间戳
     *
     * @param pts 呈现时间戳，单位：微秒
     */
    public void setPts(long pts) {
        this.pts = pts;
    }

    /**
     * 判断是否为流结束标记
     *
     * @return 是否为流结束标记
     */
    public boolean isEndOfStream() {
        return endOfStream;
    }
}
