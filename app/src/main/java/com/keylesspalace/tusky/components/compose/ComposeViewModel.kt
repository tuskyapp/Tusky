/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.compose

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.components.compose.ComposeAutoCompleteAdapter.AutocompleteResult
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfo
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.service.MediaToSend
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.service.StatusToSend
import com.keylesspalace.tusky.util.randomAlphanumericString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(FlowPreview::class)
class ComposeViewModel @Inject constructor(
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val mediaUploader: MediaUploader,
    private val serviceClient: ServiceClient,
    private val draftHelper: DraftHelper,
    instanceInfoRepo: InstanceInfoRepository
) : ViewModel() {

    private var replyingStatusAuthor: String? = null
    private var replyingStatusContent: String? = null
    internal var startingText: String? = null
    internal var postLanguage: String? = null
    private var draftId: Int = 0
    private var scheduledTootId: String? = null
    private var startingContentWarning: String = ""
    private var inReplyToId: String? = null
    private var originalStatusId: String? = null
    private var startingVisibility: Status.Visibility = Status.Visibility.UNKNOWN

    private var contentWarningStateChanged: Boolean = false
    private var modifiedInitialState: Boolean = false
    private var hasScheduledTimeChanged: Boolean = false

    val instanceInfo: SharedFlow<InstanceInfo> = instanceInfoRepo::getInstanceInfo.asFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val emoji: SharedFlow<List<Emoji>> = instanceInfoRepo::getEmojis.asFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val markMediaAsSensitive: MutableStateFlow<Boolean> =
        MutableStateFlow(accountManager.activeAccount?.defaultMediaSensitivity ?: false)

    val statusVisibility: MutableStateFlow<Status.Visibility> = MutableStateFlow(Status.Visibility.UNKNOWN)
    val showContentWarning: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val poll: MutableStateFlow<NewPoll?> = MutableStateFlow(null)
    val scheduledAt: MutableStateFlow<String?> = MutableStateFlow(null)

    val media: MutableStateFlow<List<QueuedMedia>> = MutableStateFlow(emptyList())
    val uploadError = MutableSharedFlow<Throwable>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    lateinit var composeKind: ComposeActivity.ComposeKind

    // Used in ComposeActivity to pass state to result function when cropImage contract inflight
    var cropImageItemOld: QueuedMedia? = null

    private var setupComplete = false

    suspend fun pickMedia(mediaUri: Uri, description: String? = null, focus: Attachment.Focus? = null): Result<QueuedMedia> = withContext(Dispatchers.IO) {
        try {
            val (type, uri, size) = mediaUploader.prepareMedia(mediaUri, instanceInfo.first())
            val mediaItems = media.value
            if (type != QueuedMedia.Type.IMAGE &&
                mediaItems.isNotEmpty() &&
                mediaItems[0].type == QueuedMedia.Type.IMAGE
            ) {
                Result.failure(VideoOrImageException())
            } else {
                val queuedMedia = addMediaToQueue(type, uri, size, description, focus)
                Result.success(queuedMedia)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMediaToQueue(
        type: QueuedMedia.Type,
        uri: Uri,
        mediaSize: Long,
        description: String? = null,
        focus: Attachment.Focus? = null,
        replaceItem: QueuedMedia? = null
    ): QueuedMedia {
        var stashMediaItem: QueuedMedia? = null

        media.updateAndGet { mediaValue ->
            val mediaItem = QueuedMedia(
                localId = mediaUploader.getNewLocalMediaId(),
                uri = uri,
                type = type,
                mediaSize = mediaSize,
                description = description,
                focus = focus,
                state = QueuedMedia.State.UPLOADING
            )
            stashMediaItem = mediaItem

            if (replaceItem != null) {
                mediaUploader.cancelUploadScope(replaceItem.localId)
                mediaValue.map {
                    if (it.localId == replaceItem.localId) mediaItem else it
                }
            } else { // Append
                mediaValue + mediaItem
            }
        }
        val mediaItem = stashMediaItem!! // stashMediaItem is always non-null and uncaptured at this point, but Kotlin doesn't know that

        viewModelScope.launch {
            mediaUploader
                .uploadMedia(mediaItem, instanceInfo.first())
                .collect { event ->
                    val item = media.value.find { it.localId == mediaItem.localId }
                        ?: return@collect
                    val newMediaItem = when (event) {
                        is UploadEvent.ProgressEvent ->
                            item.copy(uploadPercent = event.percentage)
                        is UploadEvent.FinishedEvent ->
                            item.copy(
                                id = event.mediaId,
                                uploadPercent = -1,
                                state = if (event.processed) { QueuedMedia.State.PROCESSED } else { QueuedMedia.State.UNPROCESSED }
                            )
                        is UploadEvent.ErrorEvent -> {
                            media.update { mediaValue -> mediaValue.filter { it.localId != mediaItem.localId } }
                            uploadError.emit(event.error)
                            return@collect
                        }
                    }
                    media.update { mediaValue ->
                        mediaValue.map { mediaItem ->
                            if (mediaItem.localId == newMediaItem.localId) {
                                newMediaItem
                            } else {
                                mediaItem
                            }
                        }
                    }
                }
        }
        return mediaItem
    }

    private fun addUploadedMedia(id: String, type: QueuedMedia.Type, uri: Uri, description: String?, focus: Attachment.Focus?) {
        media.update { mediaValue ->
            val mediaItem = QueuedMedia(
                localId = mediaUploader.getNewLocalMediaId(),
                uri = uri,
                type = type,
                mediaSize = 0,
                uploadPercent = -1,
                id = id,
                description = description,
                focus = focus,
                state = QueuedMedia.State.PUBLISHED
            )
            mediaValue + mediaItem
        }
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaUploader.cancelUploadScope(item.localId)
        media.update { mediaValue -> mediaValue.filter { it.localId != item.localId } }
    }

    fun toggleMarkSensitive() {
        this.markMediaAsSensitive.value = this.markMediaAsSensitive.value != true
    }

    fun didChange(content: String?, contentWarning: String?): Boolean {
        val textChanged = content.orEmpty() != startingText.orEmpty()
        val contentWarningChanged = contentWarning.orEmpty() != startingContentWarning
        val mediaChanged = media.value.isNotEmpty()
        val pollChanged = poll.value != null
        val didScheduledTimeChange = hasScheduledTimeChanged

        return modifiedInitialState || textChanged || contentWarningChanged || mediaChanged || pollChanged || didScheduledTimeChange
    }

    fun contentWarningChanged(value: Boolean) {
        showContentWarning.value = value
        contentWarningStateChanged = true
    }

    fun deleteDraft() {
        viewModelScope.launch {
            if (draftId != 0) {
                draftHelper.deleteDraftAndAttachments(draftId)
            }
        }
    }

    fun stopUploads() {
        mediaUploader.cancelUploadScope(*media.value.map { it.localId }.toIntArray())
    }

    fun shouldShowSaveDraftDialog(): Boolean {
        // if any of the media files need to be downloaded first it could take a while, so show a loading dialog
        return media.value.any { mediaValue ->
            mediaValue.uri.scheme == "https"
        }
    }

    suspend fun saveDraft(content: String, contentWarning: String) {
        val mediaUris: MutableList<String> = mutableListOf()
        val mediaDescriptions: MutableList<String?> = mutableListOf()
        val mediaFocus: MutableList<Attachment.Focus?> = mutableListOf()
        media.value.forEach { item ->
            mediaUris.add(item.uri.toString())
            mediaDescriptions.add(item.description)
            mediaFocus.add(item.focus)
        }

        draftHelper.saveDraft(
            draftId = draftId,
            accountId = accountManager.activeAccount?.id!!,
            inReplyToId = inReplyToId,
            content = content,
            contentWarning = contentWarning,
            sensitive = markMediaAsSensitive.value,
            visibility = statusVisibility.value,
            mediaUris = mediaUris,
            mediaDescriptions = mediaDescriptions,
            mediaFocus = mediaFocus,
            poll = poll.value,
            failedToSend = false,
            failedToSendAlert = false,
            scheduledAt = scheduledAt.value,
            language = postLanguage,
            statusId = originalStatusId,
        )
    }

    /**
     * Send status to the server.
     * Uses current state plus provided arguments.
     */
    suspend fun sendStatus(
        content: String,
        spoilerText: String
    ) {

        if (!scheduledTootId.isNullOrEmpty()) {
            api.deleteScheduledStatus(scheduledTootId!!)
        }

        val attachedMedia = media.value.map { item ->
            MediaToSend(
                localId = item.localId,
                id = item.id,
                uri = item.uri.toString(),
                description = item.description,
                focus = item.focus,
                processed = item.state == QueuedMedia.State.PROCESSED || item.state == QueuedMedia.State.PUBLISHED
            )
        }
        val tootToSend = StatusToSend(
            text = content,
            warningText = spoilerText,
            visibility = statusVisibility.value.serverString(),
            sensitive = attachedMedia.isNotEmpty() && (markMediaAsSensitive.value || showContentWarning.value),
            media = attachedMedia,
            scheduledAt = scheduledAt.value,
            inReplyToId = inReplyToId,
            poll = poll.value,
            replyingStatusContent = null,
            replyingStatusAuthorUsername = null,
            accountId = accountManager.activeAccount!!.id,
            draftId = draftId,
            idempotencyKey = randomAlphanumericString(16),
            retries = 0,
            language = postLanguage,
            statusId = originalStatusId
        )

        serviceClient.sendToot(tootToSend)
    }

    // Updates a QueuedMedia item arbitrarily, then sends description and focus to server
    private suspend fun updateMediaItem(localId: Int, mutator: (QueuedMedia) -> QueuedMedia): Boolean {
        val newMediaList = media.updateAndGet { mediaValue ->
            mediaValue.map { mediaItem ->
                if (mediaItem.localId == localId) {
                    mutator(mediaItem)
                } else {
                    mediaItem
                }
            }
        }

        if (!editing) {
            // Updates to media for already-published statuses need to go through the status edit api
            val updatedItem = newMediaList.find { it.localId == localId }
            if (updatedItem?.id != null) {
                val focus = updatedItem.focus
                val focusString = if (focus != null) "${focus.x},${focus.y}" else null
                return api.updateMedia(updatedItem.id, updatedItem.description, focusString)
                    .fold({
                        true
                    }, { throwable ->
                        Log.w(TAG, "failed to update media", throwable)
                        false
                    })
            }
        }
        return true
    }

    suspend fun updateDescription(localId: Int, description: String): Boolean {
        return updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(description = description)
        }
    }

    suspend fun updateFocus(localId: Int, focus: Attachment.Focus): Boolean {
        return updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(focus = focus)
        }
    }

    fun searchAutocompleteSuggestions(token: String): List<AutocompleteResult> {
        when (token[0]) {
            '@' -> {
                return api.searchAccountsSync(query = token.substring(1), limit = 10)
                    .fold({ accounts ->
                        accounts.map { AutocompleteResult.AccountResult(it) }
                    }, { e ->
                        Log.e(TAG, "Autocomplete search for $token failed.", e)
                        emptyList()
                    })
            }
            '#' -> {
                return api.searchSync(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
                    .fold({ searchResult ->
                        searchResult.hashtags.map { AutocompleteResult.HashtagResult(it.name) }
                    }, { e ->
                        Log.e(TAG, "Autocomplete search for $token failed.", e)
                        emptyList()
                    })
            }
            ':' -> {
                val emojiList = emoji.replayCache.firstOrNull() ?: return emptyList()
                val incomplete = token.substring(1)

                return emojiList.filter { emoji ->
                    emoji.shortcode.contains(incomplete, ignoreCase = true)
                }.sortedBy { emoji ->
                    emoji.shortcode.indexOf(incomplete, ignoreCase = true)
                }.map { emoji ->
                    AutocompleteResult.EmojiResult(emoji)
                }
            }
            else -> {
                Log.w(TAG, "Unexpected autocompletion token: $token")
                return emptyList()
            }
        }
    }

    fun setup(composeOptions: ComposeActivity.ComposeOptions?) {

        if (setupComplete) {
            return
        }

        composeKind = composeOptions?.kind ?: ComposeActivity.ComposeKind.NEW

        val preferredVisibility = accountManager.activeAccount!!.defaultPostPrivacy

        val replyVisibility = composeOptions?.replyVisibility ?: Status.Visibility.UNKNOWN
        startingVisibility = Status.Visibility.byNum(
            preferredVisibility.num.coerceAtLeast(replyVisibility.num)
        )

        inReplyToId = composeOptions?.inReplyToId

        modifiedInitialState = composeOptions?.modifiedInitialState == true

        val contentWarning = composeOptions?.contentWarning
        if (contentWarning != null) {
            startingContentWarning = contentWarning
        }
        if (!contentWarningStateChanged) {
            showContentWarning.value = !contentWarning.isNullOrBlank()
        }

        // recreate media list
        val draftAttachments = composeOptions?.draftAttachments
        if (draftAttachments != null) {
            // when coming from DraftActivity
            viewModelScope.launch {
                draftAttachments.forEach { attachment ->
                    pickMedia(attachment.uri, attachment.description, attachment.focus)
                }
            }
        } else composeOptions?.mediaAttachments?.forEach { a ->
            // when coming from redraft or ScheduledTootActivity
            val mediaType = when (a.type) {
                Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
                Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
                Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
            }
            addUploadedMedia(a.id, mediaType, a.url.toUri(), a.description, a.meta?.focus)
        }

        draftId = composeOptions?.draftId ?: 0
        scheduledTootId = composeOptions?.scheduledTootId
        originalStatusId = composeOptions?.statusId
        startingText = composeOptions?.content
        postLanguage = composeOptions?.language

        val tootVisibility = composeOptions?.visibility ?: Status.Visibility.UNKNOWN
        if (tootVisibility.num != Status.Visibility.UNKNOWN.num) {
            startingVisibility = tootVisibility
        }
        statusVisibility.value = startingVisibility
        val mentionedUsernames = composeOptions?.mentionedUsernames
        if (mentionedUsernames != null) {
            val builder = StringBuilder()
            for (name in mentionedUsernames) {
                builder.append('@')
                builder.append(name)
                builder.append(' ')
            }
            startingText = builder.toString()
        }

        scheduledAt.value = composeOptions?.scheduledAt

        composeOptions?.sensitive?.let { markMediaAsSensitive.value = it }

        val poll = composeOptions?.poll
        if (poll != null && composeOptions.mediaAttachments.isNullOrEmpty()) {
            this.poll.value = poll
        }
        replyingStatusContent = composeOptions?.replyingStatusContent
        replyingStatusAuthor = composeOptions?.replyingStatusAuthor

        setupComplete = true
    }

    fun updatePoll(newPoll: NewPoll) {
        poll.value = newPoll
    }

    fun updateScheduledAt(newScheduledAt: String?) {
        if (newScheduledAt != scheduledAt.value) {
            hasScheduledTimeChanged = true
        }

        scheduledAt.value = newScheduledAt
    }

    val editing: Boolean
        get() = !originalStatusId.isNullOrEmpty()

    private companion object {
        const val TAG = "ComposeViewModel"
    }
}

/**
 * Thrown when trying to add an image when video is already present or the other way around
 */
class VideoOrImageException : Exception()
