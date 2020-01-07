/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.search.adapter

import androidx.lifecycle.MutableLiveData
import androidx.paging.PositionalDataSource
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.NetworkState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import java.util.concurrent.Executor

class SearchDataSource<T>(
        private val mastodonApi: MastodonApi,
        private val searchType: SearchType,
        private val searchRequest: String,
        private val disposables: CompositeDisposable,
        private val retryExecutor: Executor,
        private val initialItems: List<T>? = null,
        private val parser: (SearchResult?) -> List<T>,
        private val source: SearchDataSourceFactory<T>) : PositionalDataSource<T>() {

    val networkState = MutableLiveData<NetworkState>()

    private var retry: (() -> Any)? = null

    val initialLoad = MutableLiveData<NetworkState>()

    fun retry() {
        retry?.let {
            retryExecutor.execute {
                it.invoke()
            }
        }
    }

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<T>) {
        if (!initialItems.isNullOrEmpty()) {
            callback.onResult(initialItems.toList(), 0)
        } else {
            networkState.postValue(NetworkState.LOADED)
            retry = null
            initialLoad.postValue(NetworkState.LOADING)
            mastodonApi.searchObservable(
                    query = searchRequest,
                    type = searchType.apiParameter,
                    resolve = true,
                    limit = params.requestedLoadSize,
                    offset = 0,
                    following =false)
                    .subscribe(
                            { data ->
                                val res = parser(data)
                                callback.onResult(res, params.requestedStartPosition)
                                initialLoad.postValue(NetworkState.LOADED)

                            },
                            { error ->
                                retry = {
                                    loadInitial(params, callback)
                                }
                                initialLoad.postValue(NetworkState.error(error.message))
                            }
                    ).addTo(disposables)
        }

    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<T>) {
        networkState.postValue(NetworkState.LOADING)
        retry = null
        if(source.exhausted) {
            return callback.onResult(emptyList())
        }
        mastodonApi.searchObservable(searchType.apiParameter, searchRequest, true, params.loadSize, params.startPosition, false)
                .subscribe(
                        { data ->
                            // Working around Mastodon bug where exact match is returned no matter
                            // which offset is requested (so if we seach for a full username, it's
                            // infinite)
                            // see https://github.com/tootsuite/mastodon/issues/11365
                            val res = if (data.accounts.size == 1
                                    && data.accounts[0].username
                                            .equals(searchRequest, ignoreCase = true)) {
                                listOf()
                            } else {
                                parser(data)
                            }
                            if(res.isEmpty()) {
                                source.exhausted = true
                            }
                            callback.onResult(res)
                            networkState.postValue(NetworkState.LOADED)
                        },
                        { error ->
                            retry = {
                                loadRange(params, callback)
                            }
                            networkState.postValue(NetworkState.error(error.message))
                        }
                ).addTo(disposables)


    }
}