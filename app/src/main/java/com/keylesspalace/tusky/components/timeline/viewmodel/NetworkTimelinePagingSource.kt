/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.timeline.viewmodel

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.viewdata.StatusViewData

class NetworkTimelinePagingSource(
    private val viewModel: NetworkTimelineViewModel
) : PagingSource<String, StatusViewData>() {

    override fun getRefreshKey(state: PagingState<String, StatusViewData>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, StatusViewData> {

        return if (params is LoadParams.Refresh) {
            val list = viewModel.statusData.toList()
            LoadResult.Page(list, null, viewModel.nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
