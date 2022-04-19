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
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.EmojisEntity
import com.keylesspalace.tusky.db.InstanceInfoEntity
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.VersionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InstanceInfoRepository @Inject constructor(
    private val api: MastodonApi,
    db: AppDatabase,
    accountManager: AccountManager
) {

    private val dao = db.instanceDao()
    private val instanceName = accountManager.activeAccount!!.domain

    suspend fun getEmojis() = withContext(Dispatchers.IO) {
        api.getCustomEmojis()
            .onSuccess { emojiList -> dao.insertOrReplace(EmojisEntity(instanceName, emojiList)) }
            .getOrElse { throwable ->
                Log.w(TAG, "failed to load custom emojis, falling back to cache", throwable)
                dao.getEmojiInfo(instanceName)?.emojiList.orEmpty()
            }
    }

    suspend fun getInstanceInfo(): InstanceInfo = withContext(Dispatchers.IO) {
        api.getInstance()
            .fold(
                { instance ->
                    val instanceEntity = InstanceInfoEntity(
                        instance = instanceName,
                        maximumTootCharacters = instance.configuration?.statuses?.maxCharacters ?: instance.maxTootChars,
                        maxPollOptions = instance.configuration?.polls?.maxOptions ?: instance.pollConfiguration?.maxOptions,
                        maxPollOptionLength = instance.configuration?.polls?.maxCharactersPerOption ?: instance.pollConfiguration?.maxOptionChars,
                        minPollDuration = instance.configuration?.polls?.minExpiration ?: instance.pollConfiguration?.minExpiration,
                        maxPollDuration = instance.configuration?.polls?.maxExpiration ?: instance.pollConfiguration?.maxExpiration,
                        charactersReservedPerUrl = instance.configuration?.statuses?.charactersReservedPerUrl,
                        version = instance.version
                    )
                    dao.insertOrReplace(instanceEntity)
                    instanceEntity
                },
                { throwable ->
                    Log.w(TAG, "failed to instance, falling back to cache and default values", throwable)
                    dao.getInstanceInfo(instanceName)
                }
            ).let { instanceInfo: InstanceInfoEntity? ->
                InstanceInfo(
                    maxChars = instanceInfo?.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
                    pollMaxOptions = instanceInfo?.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
                    pollMaxLength = instanceInfo?.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
                    pollMinDuration = instanceInfo?.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
                    pollMaxDuration = instanceInfo?.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
                    charactersReservedPerUrl = instanceInfo?.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                    supportsScheduled = instanceInfo?.version?.let { VersionUtils(it).supportsScheduledToots() } ?: false
                )
            }
    }

    companion object {
        private const val TAG = "InstanceInfoRepo"

        const val DEFAULT_CHARACTER_LIMIT = 500
        private const val DEFAULT_MAX_OPTION_COUNT = 4
        private const val DEFAULT_MAX_OPTION_LENGTH = 50
        private const val DEFAULT_MIN_POLL_DURATION = 300
        private const val DEFAULT_MAX_POLL_DURATION = 604800

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23
    }
}
