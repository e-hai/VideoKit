package com.kit.video.editor;

/**
 * 输入接口
 */
interface InputHandler {
    boolean initialize();

    FrameData getData();

    void release();
}
