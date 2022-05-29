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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class ScheduledStatusViewModel @Inject constructor(
    val mastodonApi: MastodonApi,
    val eventHub: EventHub
) : ViewModel() {

    private val pagingSourceFactory = ScheduledStatusPagingSourceFactory(mastodonApi)

    val data = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20),
        pagingSourceFactory = pagingSourceFactory
    ).flow
        .cachedIn(viewModelScope)

    fun deleteScheduledStatus(status: ScheduledStatus) {
        viewModelScope.launch {
            mastodonApi.deleteScheduledStatus(status.id).fold(
                {
                    pagingSourceFactory.remove(status)
                },
                { throwable ->
                    Log.w("ScheduledTootViewModel", "Error deleting scheduled status", throwable)
                }
            )
        }
    }
}
