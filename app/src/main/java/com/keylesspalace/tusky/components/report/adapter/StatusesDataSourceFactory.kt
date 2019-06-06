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
        val source = StatusesDataSource(accountId, mastodonApi, disposables, retryExecutor)
        sourceLiveData.postValue(source)
        return source
    }
}