package com.an.video

import android.net.Uri

interface BaseAdapter {

    /**
     * 切换视频
     * **/
    fun switchVideo(position: Int)

    /**
     * 获取视频的URI
     * **/
    fun getVideoUri(position: Int): Uri?
}