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
import androidx.paging.toLiveData
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.Observable
import io.reactivex.Single
import javax.inject.Inject

class DraftsViewModel @Inject constructor(
    val database: AppDatabase,
    val accountManager: AccountManager,
    val api: MastodonApi,
    val draftHelper: DraftHelper
) : ViewModel() {

    val drafts = database.draftDao().loadDrafts(accountManager.activeAccount?.id!!).toLiveData(pageSize = 20)

    fun showOldDraftsButton(): Observable<Boolean> {
        return database.tootDao().savedTootCount()
                .map { count -> count > 0 }
    }

    fun deleteDraft(draft: DraftEntity) {
        draftHelper.deleteDraft(draft)
                .subscribe()
    }

    fun getToot(tootId: String): Single<Status> {
        return api.statusSingle(tootId)
    }

}
