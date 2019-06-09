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