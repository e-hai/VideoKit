package com.video

import androidx.paging.*
import kotlinx.coroutines.flow.Flow

object MainRepository {

    fun getTestPaging(): Flow<PagingData<VideoModel>> {
        return Pager(PagingConfig(10)) {
            ListPagingSource()
        }.flow
    }

    class ListPagingSource : PagingSource<Int, VideoModel>() {

        override fun getRefreshKey(state: PagingState<Int, VideoModel>): Int? {
            return null
        }

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoModel> {
            return try {
                val nextPageNumber = params.key ?: 1
                val responseData = createMediaSource()
                LoadResult.Page(
                    data = responseData,
                    prevKey = null,
                    nextKey = nextPageNumber.plus(1)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                LoadResult.Error(e)
            }
        }
    }


    private fun createMediaSource(): List<VideoModel> {
        return listOf(
            VideoModel(
                "http://face-model-osszh.startech.ltd/basis-admin/6c1405e63dab4a4d933cf4c6d86d712d.mp4",
                "视频A"
            ),
            VideoModel(
                "http://face-model-osszh.startech.ltd/basis-admin/50d5ed442c4444baafb1f238e21922e4.mp4",
                "视频B"
            ),
            VideoModel(
                "https://face-model-osszh.startech.ltd/basis-admin/d807182c7ac246d69ed39ccb1e17f122.mp4",
                "视频D"
            ),
            VideoModel(
                "https://face-model-osszh.startech.ltd/basis-admin/c67300b1c0284cd3b18d5aefb0074ef8.mp4",
                "视频E"
            ),
        )
    }
}

data class VideoModel(val videoUrl: String, val title: String="") : java.io.Serializable

