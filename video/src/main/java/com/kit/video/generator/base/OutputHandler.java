package com.kit.video.generator.base;


/**
 * 输出接口
 */
public interface OutputHandler {
    boolean initialize();

    void writeVideoFrame(FrameData frame);

    void writeAudioFrame(FrameData frame);

    void release();
}