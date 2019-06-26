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

import androidx.lifecycle.Transformations
import androidx.paging.Config
import androidx.paging.toLiveData
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.entity.SearchResults2
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Listing
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.Executors

class SearchRepository<T>(private val mastodonApi: MastodonApi) {

    private val executor = Executors.newSingleThreadExecutor()

    fun getSearchData(searchType: SearchType, searchRequest: String?, disposables: CompositeDisposable, pageSize: Int = 20,
                      initialItems: List<T>? = null, parser: (SearchResults2?) -> List<T>): Listing<T> {
        val sourceFactory = SearchDataSourceFactory(mastodonApi, searchType, searchRequest, disposables, executor, initialItems, parser)
        val livePagedList = sourceFactory.toLiveData(
                config = Config(pageSize = pageSize, enablePlaceholders = false, initialLoadSizeHint = pageSize * 2),
                fetchExecutor = executor
        )
        return Listing(
                pagedList = livePagedList,
                networkState = Transformations.switchMap(sourceFactory.sourceLiveData) {
                    it.networkState
                },
                retry = {
                    sourceFactory.sourceLiveData.value?.retry()
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