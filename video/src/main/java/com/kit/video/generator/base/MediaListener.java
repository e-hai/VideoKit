package com.kit.video.generator.base;

/**
 * 媒体处理事件监听器接口
 */
public interface MediaListener {

    /**
     * 媒体处理开始时调用的方法
     */
    void onStart();

    /**
     * 媒体处理成功结束时调用的方法
     */
    void onEnd();

    /**
     * 媒体处理过程中发生错误时调用的方法
     *
     * @param errorMessage 错误描述信息
     */
    void onError(String errorMessage);
}
