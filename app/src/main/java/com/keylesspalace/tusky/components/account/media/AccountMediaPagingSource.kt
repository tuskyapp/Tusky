/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.account.media

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.keylesspalace.tusky.viewdata.AttachmentViewData

class AccountMediaPagingSource(
    private val viewModel: AccountMediaViewModel
) : PagingSource<String, AttachmentViewData>() {

    override fun getRefreshKey(state: PagingState<String, AttachmentViewData>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, AttachmentViewData> {

        return if (params is LoadParams.Refresh) {
            val list = viewModel.attachmentData.toList()
            LoadResult.Page(list, null, list.lastOrNull()?.statusId)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }
}
