package com.kit.video.generator.base;


/**
 * 输入接口
 */
public interface InputHandler {
    boolean initialize();

    FrameData getData();

    void release();
}
