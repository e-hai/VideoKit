package com.kit.video.generator.out;

import android.media.MediaCodec;

import com.kit.video.generator.base.FrameData;

public interface MediaCodecEncoder {
    void initialize() throws Exception;

    void writeFrame(FrameData frame) throws Exception;

    void release();

    MediaCodec getEncoder();
}