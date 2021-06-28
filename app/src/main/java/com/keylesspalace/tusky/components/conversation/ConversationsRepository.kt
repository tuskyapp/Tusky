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

package com.keylesspalace.tusky.components.conversation

import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationsRepository @Inject constructor(
    val mastodonApi: MastodonApi,
    val db: AppDatabase
) {

    fun deleteCacheForAccount(accountId: Long) {
        Single.fromCallable {
            db.conversationDao().deleteForAccount(accountId)
        }.subscribeOn(Schedulers.io())
            .subscribe()
    }
}
