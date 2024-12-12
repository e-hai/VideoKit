package com.kit.video.editor;

/**
 * 输出接口
 */
interface OutputHandler {
    boolean initialize();

    void writeVideoFrame(FrameData frame);

    void writeAudioFrame(FrameData frame);

    void release();
}