package com.an.video.exoplayer

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
    fun createRawMediaSource(context: Context, @RawRes rawRes: Int): MediaSource {
        val rawResourceDataSource = RawResourceDataSource(context)
        val dataSpec = DataSpec(RawResourceDataSource.buildRawResourceUri(rawRes))
        try {
            rawResourceDataSource.open(dataSpec)
        } catch (e: RawResourceDataSource.RawResourceDataSourceException) {
            e.printStackTrace()
        }
        val uri = rawResourceDataSource.uri ?: throw RuntimeException("not find raw")
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext)
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource
            .Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    fun createMediaSource(context: Context, uri: Uri): MediaSource {
        val dataSourceFactory = MediaCacheFactory.getCacheFactory(context.applicationContext)
        val mediaItem = MediaItem.fromUri(uri)
        return ProgressiveMediaSource
            .Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object MediaCacheFactory {
    private const val TAG = "MediaCacheFactory"
    private var cacheFactory: DataSource.Factory? = null

    @Synchronized
    fun getCacheFactory(ctx: Context): DataSource.Factory {
        if (cacheFactory == null) {
            val downDirectory = File(ctx.cacheDir, "videos")
            val cache = SimpleCache(
                downDirectory,
                LeastRecentlyUsedCacheEvictor(1024 * 1024 * 512)
            )
            cacheFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setCacheReadDataSourceFactory(
                    DefaultDataSource.Factory(
                        ctx,
                        DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(false)
                            .setConnectTimeoutMs(8000)
                            .setReadTimeoutMs(8000)
                            .setUserAgent("MY_Exoplayer")
                    )
                )
                .setUpstreamDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(false)
                        .setConnectTimeoutMs(8000)
                        .setReadTimeoutMs(8000)
                        .setUserAgent("MY_Exoplayer")
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setEventListener(object : CacheDataSource.EventListener {
                    override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                        Log.d(TAG, "onCachedBytesRead $cacheSizeBytes  >> $cachedBytesRead")
                    }

                    override fun onCacheIgnored(reason: Int) {
                        Log.d(TAG, "onCacheIgnored $reason")
                    }

                })
        }
        return cacheFactory!!
    }
}