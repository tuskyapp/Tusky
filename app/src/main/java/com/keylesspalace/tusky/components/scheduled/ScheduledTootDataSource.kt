/* Copyright 2019 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.components.scheduled

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import androidx.paging.ItemKeyedDataSource
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.NetworkState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo

class ScheduledTootDataSourceFactory(
        private val mastodonApi: MastodonApi,
        private val disposables: CompositeDisposable
): DataSource.Factory<String, ScheduledStatus>() {

    private val scheduledTootsCache = mutableListOf<ScheduledStatus>()

    private var dataSource: ScheduledTootDataSource? = null

    val networkState = MutableLiveData<NetworkState>()

    override fun create(): DataSource<String, ScheduledStatus> {
        return ScheduledTootDataSource(mastodonApi, disposables, scheduledTootsCache, networkState).also {
            dataSource = it
        }
    }

    fun reload() {
        scheduledTootsCache.clear()
        dataSource?.invalidate()
    }

    fun remove(status: ScheduledStatus) {
        scheduledTootsCache.remove(status)
        dataSource?.invalidate()
    }

}


class ScheduledTootDataSource(
        private val mastodonApi: MastodonApi,
        private val disposables: CompositeDisposable,
        private val scheduledTootsCache: MutableList<ScheduledStatus>,
        private val networkState: MutableLiveData<NetworkState>
): ItemKeyedDataSource<String, ScheduledStatus>() {
    override fun loadInitial(params: LoadInitialParams<String>, callback: LoadInitialCallback<ScheduledStatus>) {
        if(scheduledTootsCache.isNotEmpty()) {
            callback.onResult(scheduledTootsCache.toList())
        } else {
            networkState.postValue(NetworkState.LOADING)
            mastodonApi.scheduledStatuses(limit = params.requestedLoadSize)
                    .subscribe({ newData ->
                        scheduledTootsCache.addAll(newData)
                        callback.onResult(newData)
                        networkState.postValue(NetworkState.LOADED)
                    }, { throwable ->
                        Log.w("ScheduledTootDataSource", "Error loading scheduled statuses", throwable)
                        networkState.postValue(NetworkState.error(throwable.message))
                    })
                    .addTo(disposables)
        }
    }

    override fun loadAfter(params: LoadParams<String>, callback: LoadCallback<ScheduledStatus>) {
        mastodonApi.scheduledStatuses(limit = params.requestedLoadSize, maxId = params.key)
                .subscribe({ newData ->
                    scheduledTootsCache.addAll(newData)
                    callback.onResult(newData)
                }, { throwable ->
                    Log.w("ScheduledTootDataSource", "Error loading scheduled statuses", throwable)
                    networkState.postValue(NetworkState.error(throwable.message))
                })
                .addTo(disposables)
    }

    override fun loadBefore(params: LoadParams<String>, callback: LoadCallback<ScheduledStatus>) {
        // we are always loading from beginning to end
    }

    override fun getKey(item: ScheduledStatus): String {
        return item.id
    }

}