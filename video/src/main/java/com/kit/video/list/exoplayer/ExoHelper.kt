package com.kit.video.list.exoplayer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ExoHelper {

    private const val TAG = "ExoHelper"

    /**
     * 创建一个从 raw 资源加载的 MediaSource
     *
     * @param context 上下文
     * @param rawRes raw 资源 ID
     * @return ProgressiveMediaSource 实例
     */
    fun createRawMediaSource(context: Context, @RawRes rawRes: Int): MediaSource {
        val rawResourceDataSource = RawResourceDataSource(context)
        val dataSpec = DataSpec(RawResourceDataSource.buildRawResourceUri(rawRes))
        try {
            rawResourceDataSource.open(dataSpec)
        } catch (e: RawResourceDataSource.RawResourceDataSourceException) {
            Log.e(TAG, "打开 raw 资源失败", e)
            throw RuntimeException("打开 raw 资源失败", e)
        }
        val uri = rawResourceDataSource.uri ?: throw RuntimeException("未找到 raw 资源 URI")
        return createProgressiveMediaSource(context, uri)
    }

    /**
     * 创建一个从指定 URI 加载的 MediaSource
     *
     * @param context 上下文
     * @param uri 视频文件的 URI
     * @return ProgressiveMediaSource 实例
     */
    fun createMediaSource(context: Context, uri: Uri): MediaSource {
        return createProgressiveMediaSource(context, uri)
    }

    /**
     * 创建一个 ProgressiveMediaSource
     *
     * @param context 上下文
     * @param uri 视频文件的 URI
     * @return ProgressiveMediaSource 实例
     */
    private fun createProgressiveMediaSource(context: Context, uri: Uri): MediaSource {
        val dataSourceFactory = MediaCacheFactory.getCacheFactory(context.applicationContext)
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaCacheFactory {

    private const val TAG = "MediaCacheFactory"
    private var cacheFactory: DataSource.Factory? = null

    /**
     * 获取缓存数据源工厂
     *
     * @param ctx 上下文
     * @return DataSource.Factory 实例
     */
    @Synchronized
    fun getCacheFactory(ctx: Context): DataSource.Factory {
        if (cacheFactory == null) {
            val downDirectory = File(ctx.cacheDir, "videos")
            val cache = SimpleCache(
                downDirectory,
                LeastRecentlyUsedCacheEvictor(1024 * 1024 * 512), // 512MB 缓存大小
            )

            val upstreamDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(false)
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
                .setUserAgent("MY_Exoplayer")

            cacheFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setCacheReadDataSourceFactory(DefaultDataSource.Factory(ctx, upstreamDataSourceFactory))
                .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setEventListener(object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        Log.d(TAG, "已缓存字节数: $cacheSizeBytes, 当前读取字节数: $cachedBytesRead")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        Log.d(TAG, "缓存被忽略, 原因: $reason")
                    }
                })
        }
        return cacheFactory!!
    }
}