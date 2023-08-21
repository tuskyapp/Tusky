/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.components.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class DraftsViewModel @Inject constructor(
    val database: AppDatabase,
    val accountManager: AccountManager,
    val api: MastodonApi,
    private val draftHelper: DraftHelper
) : ViewModel() {

    val drafts = Pager(
        config = PagingConfig(pageSize = 20),
        pagingSourceFactory = { database.draftDao().draftsPagingSource(accountManager.activeAccount?.id!!) }
    ).flow
        .cachedIn(viewModelScope)

    private val deletedDrafts: MutableList<DraftEntity> = mutableListOf()

    fun deleteDraft(draft: DraftEntity) {
        // this does not immediately delete media files to avoid unnecessary file operations
        // in case the user decides to restore the draft
        viewModelScope.launch {
            database.draftDao().delete(draft.id)
            deletedDrafts.add(draft)
        }
    }

    fun restoreDraft(draft: DraftEntity) {
        viewModelScope.launch {
            database.draftDao().insertOrReplace(draft)
            deletedDrafts.remove(draft)
        }
    }

    suspend fun getStatus(statusId: String): NetworkResult<Status> {
        return api.status(statusId)
    }

    override fun onCleared() {
        viewModelScope.launch {
            deletedDrafts.forEach {
                draftHelper.deleteAttachments(it)
            }
        }
    }
}
