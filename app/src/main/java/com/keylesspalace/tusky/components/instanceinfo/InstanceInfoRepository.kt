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
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.onSuccess
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.EmojisEntity
import com.keylesspalace.tusky.db.InstanceInfoEntity
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
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
     * Returns information about the instance.
     * Will always try to fetch the most up-to-date data from the api, falls back to cache in case it is not available.
     * Never throws, returns defaults of vanilla Mastodon in case of error.
     */
    suspend fun getInstanceInfo(): InstanceInfo = withContext(Dispatchers.IO) {
        api.getInstance()
            .fold(
                { instance ->
                    val instanceEntity = InstanceInfoEntity(
                        instance = instanceName,
                        maximumTootCharacters = instance.configuration.statuses?.maxCharacters ?: DEFAULT_CHARACTER_LIMIT,
                        maxPollOptions = instance.configuration.polls?.maxOptions ?: DEFAULT_MAX_OPTION_COUNT,
                        maxPollOptionLength = instance.configuration.polls?.maxCharactersPerOption ?: DEFAULT_MAX_OPTION_LENGTH,
                        minPollDuration = instance.configuration.polls?.minExpirationSeconds ?: DEFAULT_MIN_POLL_DURATION,
                        maxPollDuration = instance.configuration.polls?.maxExpirationSeconds ?: DEFAULT_MAX_POLL_DURATION,
                        charactersReservedPerUrl = instance.configuration.statuses?.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                        version = instance.version,
                        videoSizeLimit = instance.configuration.mediaAttachments?.videoSizeLimitBytes?.toInt() ?: DEFAULT_VIDEO_SIZE_LIMIT,
                        imageSizeLimit = instance.configuration.mediaAttachments?.imageSizeLimitBytes?.toInt() ?: DEFAULT_IMAGE_SIZE_LIMIT,
                        imageMatrixLimit = instance.configuration.mediaAttachments?.imagePixelCountLimit?.toInt() ?: DEFAULT_IMAGE_MATRIX_LIMIT,
                        maxMediaAttachments = instance.configuration.statuses?.maxMediaAttachments ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
                        maxFields = instance.pleroma?.metadata?.fieldLimits?.maxFields,
                        maxFieldNameLength = instance.pleroma?.metadata?.fieldLimits?.nameLength,
                        maxFieldValueLength = instance.pleroma?.metadata?.fieldLimits?.valueLength,
                    )
                    dao.upsert(instanceEntity)
                    instanceEntity
                },
                { throwable ->
                    if (throwable.isHttpNotFound()) {
                        getInstanceInfoV1()
                    } else {
                        Log.w(TAG, "failed to instance, falling back to cache and default values", throwable)
                        dao.getInstanceInfo(instanceName)
                    }
                }
            ).let { instanceInfo: InstanceInfoEntity? ->
                InstanceInfo(
                    maxChars = instanceInfo?.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
                    pollMaxOptions = instanceInfo?.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
                    pollMaxLength = instanceInfo?.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
                    pollMinDuration = instanceInfo?.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
                    pollMaxDuration = instanceInfo?.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
                    charactersReservedPerUrl = instanceInfo?.charactersReservedPerUrl ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
                    videoSizeLimit = instanceInfo?.videoSizeLimit ?: DEFAULT_VIDEO_SIZE_LIMIT,
                    imageSizeLimit = instanceInfo?.imageSizeLimit ?: DEFAULT_IMAGE_SIZE_LIMIT,
                    imageMatrixLimit = instanceInfo?.imageMatrixLimit ?: DEFAULT_IMAGE_MATRIX_LIMIT,
                    maxMediaAttachments = instanceInfo?.maxMediaAttachments ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
                    maxFields = instanceInfo?.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
                    maxFieldNameLength = instanceInfo?.maxFieldNameLength,
                    maxFieldValueLength = instanceInfo?.maxFieldValueLength,
                    version = instanceInfo?.version,
                )
            }
    }

    private suspend fun getInstanceInfoV1(): InstanceInfoEntity? = withContext(Dispatchers.IO) {
        api.getInstanceV1()
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
                        version = instance.version,
                        videoSizeLimit = instance.configuration?.mediaAttachments?.videoSizeLimit ?: instance.uploadLimit,
                        imageSizeLimit = instance.configuration?.mediaAttachments?.imageSizeLimit ?: instance.uploadLimit,
                        imageMatrixLimit = instance.configuration?.mediaAttachments?.imageMatrixLimit,
                        maxMediaAttachments = instance.configuration?.statuses?.maxMediaAttachments ?: instance.maxMediaAttachments,
                        maxFields = instance.pleroma?.metadata?.fieldLimits?.maxFields,
                        maxFieldNameLength = instance.pleroma?.metadata?.fieldLimits?.nameLength,
                        maxFieldValueLength = instance.pleroma?.metadata?.fieldLimits?.valueLength,
                    )
                    dao.upsert(instanceEntity)
                    instanceEntity
                },
                { throwable ->
                    Log.w(TAG, "failed to instance, falling back to cache and default values", throwable)
                    dao.getInstanceInfo(instanceName)
                }
            )
    }

    companion object {
        private const val TAG = "InstanceInfoRepo"

        const val DEFAULT_CHARACTER_LIMIT = 500
        private const val DEFAULT_MAX_OPTION_COUNT = 4
        private const val DEFAULT_MAX_OPTION_LENGTH = 50
        private const val DEFAULT_MIN_POLL_DURATION = 300
        private const val DEFAULT_MAX_POLL_DURATION = 604800

        private const val DEFAULT_VIDEO_SIZE_LIMIT = 41943040 // 40MiB
        private const val DEFAULT_IMAGE_SIZE_LIMIT = 10485760 // 10MiB
        private const val DEFAULT_IMAGE_MATRIX_LIMIT = 16777216 // 4096^2 Pixels

        // Mastodon only counts URLs as this long in terms of status character limits
        const val DEFAULT_CHARACTERS_RESERVED_PER_URL = 23

        const val DEFAULT_MAX_MEDIA_ATTACHMENTS = 4
        const val DEFAULT_MAX_ACCOUNT_FIELDS = 4
    }
}
