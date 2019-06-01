package com.keylesspalace.tusky.components.report.adapter

import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.Executor


class StatusesDataSourceFactory(
        private val accountId: String,
        private val mastodonApi: MastodonApi,
        private val disposables: CompositeDisposable,
        private val retryExecutor: Executor) : DataSource.Factory<String, Status>() {
    val sourceLiveData = MutableLiveData<StatusesDataSource>()
    override fun create(): DataSource<String, Status> {
        val source = StatusesDataSource(accountId,mastodonApi,disposables,retryExecutor)
        sourceLiveData.postValue(source)
        return source
    }
}