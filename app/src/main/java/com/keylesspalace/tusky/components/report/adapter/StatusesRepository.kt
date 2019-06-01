package com.keylesspalace.tusky.components.report.adapter

import androidx.lifecycle.Transformations
import androidx.paging.Config
import androidx.paging.toLiveData
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.BiListing
import com.keylesspalace.tusky.util.Listing
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusesRepository @Inject constructor(private val mastodonApi: MastodonApi) {

    private val executor = Executors.newSingleThreadExecutor()

    fun getStatuses(accountId: String, initialStatus: String?, disposables: CompositeDisposable, pageSize: Int = 20): BiListing<Status> {
        val sourceFactory = StatusesDataSourceFactory(accountId, mastodonApi, disposables, executor)
        val livePagedList = sourceFactory.toLiveData(
                config = Config(pageSize = pageSize, enablePlaceholders = false, initialLoadSizeHint = pageSize * 2),
                fetchExecutor = executor, initialLoadKey = initialStatus
        )
        return BiListing(
                pagedList = livePagedList,
                networkStateBefore = Transformations.switchMap(sourceFactory.sourceLiveData) {
                    it.networkStateBefore
                },
                networkStateAfter = Transformations.switchMap(sourceFactory.sourceLiveData) {
                    it.networkStateAfter
                },
                retry = {
                    sourceFactory.sourceLiveData.value?.retryAllFailed()
                },
                refresh = {
                    sourceFactory.sourceLiveData.value?.invalidate()
                },
                refreshState = Transformations.switchMap(sourceFactory.sourceLiveData) {
                    it.initialLoad
                }

        )
    }
}