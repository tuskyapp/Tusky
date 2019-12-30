package com.keylesspalace.tusky.components.scheduled

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.paging.Config
import androidx.paging.toLiveData
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.StatusScheduledEvent
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import javax.inject.Inject

class ScheduledTootViewModel @Inject constructor(
        val mastodonApi: MastodonApi,
        val eventHub: EventHub
): ViewModel() {

    private val disposables = CompositeDisposable()

    private val dataSourceFactory = ScheduledTootDataSourceFactory(mastodonApi, disposables)

    val data = dataSourceFactory.toLiveData(
            config = Config(pageSize = 20, initialLoadSizeHint = 20, enablePlaceholders = false)
    )

    val networkState = dataSourceFactory.networkState

    init {
        eventHub.events
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    if (event is StatusScheduledEvent) {
                        reload()
                    }
                }
                .addTo(disposables)
    }

    fun reload() {
        dataSourceFactory.reload()
    }

    fun deleteScheduledStatus(status: ScheduledStatus) {
        mastodonApi.deleteScheduledStatus(status.id)
                .subscribe({
                    dataSourceFactory.remove(status)
                },{ throwable ->
                    Log.w("ScheduledTootViewModel", "Error deleting scheduled status", throwable)
                })
                .addTo(disposables)

    }

    override fun onCleared() {
        disposables.clear()
    }

}