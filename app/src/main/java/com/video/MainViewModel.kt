package com.video

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.media3.exoplayer.source.MediaSource
import androidx.paging.PagingData
import androidx.paging.map
import com.an.video.exoplayer.ExoHelper
import kotlinx.coroutines.flow.map

class MainViewModel(private val context: Application) : AndroidViewModel(context) {

    fun getExoPagingData(): LiveData<PagingData<TestModelExo>> {
        return MainRepository.getTestPaging()
            .map { data ->
                data.map {
                    TestModelExo(
                        ExoHelper.createMediaSource(context, it.videoUrl.toUri()),
                        it.videoUrl.toUri(),
                        it.title
                    )
                }
            }.asLiveData()
    }

    fun getOriginPagingData(): LiveData<PagingData<VideoModel>> {
        return MainRepository.getTestPaging()
            .asLiveData()
    }
}

data class TestModelExo(val source: MediaSource, val videoUri: Uri, val title: String)