/* Copyright 2022 Tusky contributors
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

package com.keylesspalace.tusky.components.instanceinfo

import android.util.Log
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.onSuccess
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.EmojisEntity
import com.keylesspalace.tusky.db.InstanceInfoEntity
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.MastodonApiV1
import com.keylesspalace.tusky.network.MastodonApiV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InstanceInfoRepository @Inject constructor(
    private val api: MastodonApi,
    private val api1: MastodonApiV1,
    private val api2: MastodonApiV2,
    db: AppDatabase,
    accountManager: AccountManager
) {

    private val dao = db.instanceDao()
    private val instanceName = accountManager.activeAccount!!.domain

    /**
     * Returns the custom emojis of the instance.
     * Will always try to fetch them from the api, falls back to cached Emojis in case it is not available.
     * Never throws, returns empty list in case of error.
     */
    suspend fun getEmojis(): List<Emoji> = withContext(Dispatchers.IO) {
        api.getCustomEmojis()
            .onSuccess { emojiList -> dao.upsert(EmojisEntity(instanceName, emojiList)) }
            .getOrElse { throwable ->
                Log.w(TAG, "failed to load custom emojis, falling back to cache", throwable)
                dao.getEmojiInfo(instanceName)?.emojiList.orEmpty()
            }
    }

    /**
     * Return information about the instance.
     *
     * Returns the first success of, in order:
     * - /api/v2/instance
     * - /api/v1/instance
     * - locally cached information
     * - defaults for a vanilla Mastodon server
     */
    suspend fun getInstanceInfo(): InstanceInfo = withContext(Dispatchers.IO) {
        val instance = api2.instance().getOrElse {
            Log.w(TAG, "api2.instance() failed", it)
            null
        } ?: api1.instance().getOrElse {
            Log.w(TAG, "api1.instance() failed", it)
            null
        }

        if (instance != null) {
            val instanceInfoEntity = InstanceInfoEntity.from(instance, instanceName)
            dao.upsert(instanceInfoEntity)
            return@withContext InstanceInfo.from(instanceInfoEntity)
        }

        val instanceInfoEntity = dao.getInstanceInfo(instanceName)
        if (instanceInfoEntity != null) {
            return@withContext InstanceInfo.from(instanceInfoEntity)
        }

        Log.w(TAG, "no cached instance info, falling back to default")
        return@withContext InstanceInfo.default()
    }

    companion object {
        private const val TAG = "InstanceInfoRepo"
    }
}
