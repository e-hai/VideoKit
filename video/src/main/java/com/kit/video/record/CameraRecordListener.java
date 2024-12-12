package com.kit.video.record;

public interface CameraRecordListener {

    void onRecordComplete();

    void onRecordStart();

    void onError(Exception exception);

    /**
     * Is called when native codecs finish to write file.
     */
    void onVideoFileReady();
}
