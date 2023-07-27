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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import javax.inject.Inject

class AccountMediaViewModel @Inject constructor(
    api: MastodonApi
) : ViewModel() {

    lateinit var accountId: String

    val attachmentData: MutableList<AttachmentViewData> = mutableListOf()

    var currentSource: AccountMediaPagingSource? = null

    @OptIn(ExperimentalPagingApi::class)
    val media = Pager(
        config = PagingConfig(
            pageSize = LOAD_AT_ONCE,
            prefetchDistance = LOAD_AT_ONCE * 2
        ),
        pagingSourceFactory = {
            AccountMediaPagingSource(
                viewModel = this
            ).also { source ->
                currentSource = source
            }
        },
        remoteMediator = AccountMediaRemoteMediator(api, this)
    ).flow
        .cachedIn(viewModelScope)

    fun revealAttachment(viewData: AttachmentViewData) {
        val position = attachmentData.indexOfFirst { oldViewData -> oldViewData.id == viewData.id }
        attachmentData[position] = viewData.copy(isRevealed = true)
        currentSource?.invalidate()
    }

    companion object {
        private const val LOAD_AT_ONCE = 30
    }
}
