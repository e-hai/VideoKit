package com.kit.video.editor;

import java.nio.ByteBuffer;

/**
 * 数据帧封装
 */
class FrameData {
    ByteBuffer data;
    long pts;
    int textureId; // 如果是纹理数据，则使用该字段

    FrameData(ByteBuffer data, long pts) {
        this.data = data;
        this.pts = pts;
    }

    FrameData(int textureId, long pts) {
        this.textureId = textureId;
        this.pts = pts;
    }

    public ByteBuffer getByteBuffer() {
        return data;
    }

    public int getTextureId() {
        return textureId;
    }

    public long getPts() {
        return pts;
    }
}
