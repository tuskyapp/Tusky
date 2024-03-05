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
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.getOrThrow
import at.connyduck.calladapter.networkresult.map
import at.connyduck.calladapter.networkresult.onSuccess
import at.connyduck.calladapter.networkresult.recover
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.EmojisEntity
import com.keylesspalace.tusky.db.InstanceInfoEntity
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.entity.InstanceV1
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.isHttpNotFound
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
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
    suspend fun getUpdatedInstanceInfoOrFallback(): InstanceInfo = withContext(Dispatchers.IO) {
        fetchRemoteInstanceInfo()
            .onSuccess { instanceInfoEntity ->
                dao.upsert(instanceInfoEntity)
            }
            .getOrElse { throwable ->
                Log.w(
                    TAG,
                    "failed to load instance, falling back to cache and default values",
                    throwable
                )
                dao.getInstanceInfo(instanceName)
            }
            .toInfoOrDefault()
    }

    /**
     * Returns information about the instance.
     * Will always try to fetch the most up-to-date data from the api, falls back to cache in case it is not available.
     * Never throws, returns defaults of vanilla Mastodon in case of error.
     */
    suspend fun getCachedInstanceInfoOrFallback(): InstanceInfo = withContext(Dispatchers.IO) {
        dao.getInstanceInfo(instanceName)?.toInfoOrDefault()
            ?: fetchRemoteInstanceInfo()
                .onSuccess { dao.upsert(it) }
                .getOrElse { throwable ->
                    Log.w(
                        TAG,
                        "failed to load instance, falling back to default values",
                        throwable
                    )
                    null
                }
                .toInfoOrDefault()
    }

    private suspend fun fetchRemoteInstanceInfo(): NetworkResult<InstanceInfoEntity> {
        return api.getInstance()
            .map { it.toEntity() }
            .recover { t ->
                if (t.isHttpNotFound()) {
                    api.getInstanceV1().map { it.toInfoEntity() }.getOrThrow()
                } else {
                    throw t
                }
            }
    }

    private fun InstanceInfoEntity?.toInfoOrDefault() = InstanceInfo(
        maxChars = this?.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
        pollMaxOptions = this?.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
        pollMaxLength = this?.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
        pollMinDuration = this?.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
        pollMaxDuration = this?.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
        charactersReservedPerUrl = this?.charactersReservedPerUrl
            ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
        videoSizeLimit = this?.videoSizeLimit ?: DEFAULT_VIDEO_SIZE_LIMIT,
        imageSizeLimit = this?.imageSizeLimit ?: DEFAULT_IMAGE_SIZE_LIMIT,
        imageMatrixLimit = this?.imageMatrixLimit ?: DEFAULT_IMAGE_MATRIX_LIMIT,
        maxMediaAttachments = this?.maxMediaAttachments
            ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
        maxFields = this?.maxFields ?: DEFAULT_MAX_ACCOUNT_FIELDS,
        maxFieldNameLength = this?.maxFieldNameLength,
        maxFieldValueLength = this?.maxFieldValueLength,
        version = this?.version,
        translationEnabled = this?.translationEnabled
    )

    private fun Instance.toEntity() = InstanceInfoEntity(
        instance = instanceName,
        maximumTootCharacters = this.configuration?.statuses?.maxCharacters
            ?: DEFAULT_CHARACTER_LIMIT,
        maxPollOptions = this.configuration?.polls?.maxOptions ?: DEFAULT_MAX_OPTION_COUNT,
        maxPollOptionLength = this.configuration?.polls?.maxCharactersPerOption
            ?: DEFAULT_MAX_OPTION_LENGTH,
        minPollDuration = this.configuration?.polls?.minExpirationSeconds
            ?: DEFAULT_MIN_POLL_DURATION,
        maxPollDuration = this.configuration?.polls?.maxExpirationSeconds
            ?: DEFAULT_MAX_POLL_DURATION,
        charactersReservedPerUrl = this.configuration?.statuses?.charactersReservedPerUrl
            ?: DEFAULT_CHARACTERS_RESERVED_PER_URL,
        version = this.version,
        videoSizeLimit = this.configuration?.mediaAttachments?.videoSizeLimitBytes?.toInt()
            ?: DEFAULT_VIDEO_SIZE_LIMIT,
        imageSizeLimit = this.configuration?.mediaAttachments?.imageSizeLimitBytes?.toInt()
            ?: DEFAULT_IMAGE_SIZE_LIMIT,
        imageMatrixLimit = this.configuration?.mediaAttachments?.imagePixelCountLimit?.toInt()
            ?: DEFAULT_IMAGE_MATRIX_LIMIT,
        maxMediaAttachments = this.configuration?.statuses?.maxMediaAttachments
            ?: DEFAULT_MAX_MEDIA_ATTACHMENTS,
        maxFields = this.pleroma?.metadata?.fieldLimits?.maxFields,
        maxFieldNameLength = this.pleroma?.metadata?.fieldLimits?.nameLength,
        maxFieldValueLength = this.pleroma?.metadata?.fieldLimits?.valueLength,
        translationEnabled = this.configuration?.translation?.enabled
    )

    private fun InstanceV1.toInfoEntity() =
        InstanceInfoEntity(
            instance = instanceName,
            maximumTootCharacters = this.configuration?.statuses?.maxCharacters
                ?: this.maxTootChars,
            maxPollOptions = this.configuration?.polls?.maxOptions
                ?: this.pollConfiguration?.maxOptions,
            maxPollOptionLength = this.configuration?.polls?.maxCharactersPerOption
                ?: this.pollConfiguration?.maxOptionChars,
            minPollDuration = this.configuration?.polls?.minExpiration
                ?: this.pollConfiguration?.minExpiration,
            maxPollDuration = this.configuration?.polls?.maxExpiration
                ?: this.pollConfiguration?.maxExpiration,
            charactersReservedPerUrl = this.configuration?.statuses?.charactersReservedPerUrl,
            version = this.version,
            videoSizeLimit = this.configuration?.mediaAttachments?.videoSizeLimit
                ?: this.uploadLimit,
            imageSizeLimit = this.configuration?.mediaAttachments?.imageSizeLimit
                ?: this.uploadLimit,
            imageMatrixLimit = this.configuration?.mediaAttachments?.imageMatrixLimit,
            maxMediaAttachments = this.configuration?.statuses?.maxMediaAttachments
                ?: this.maxMediaAttachments,
            maxFields = this.pleroma?.metadata?.fieldLimits?.maxFields,
            maxFieldNameLength = this.pleroma?.metadata?.fieldLimits?.nameLength,
            maxFieldValueLength = this.pleroma?.metadata?.fieldLimits?.valueLength,
            translationEnabled = null,
        )

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
