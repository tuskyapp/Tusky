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
import com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeKind
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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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

    private var currentContent: String? = ""
    private var currentContentWarning: String? = ""

    val instanceInfo: SharedFlow<InstanceInfo> = instanceInfoRepo::getUpdatedInstanceInfoOrFallback.asFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val emoji: SharedFlow<List<Emoji>> = instanceInfoRepo::getEmojis.asFlow()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    private val _markMediaAsSensitive =
        MutableStateFlow(accountManager.activeAccount?.defaultMediaSensitivity ?: false)
    val markMediaAsSensitive: StateFlow<Boolean> = _markMediaAsSensitive.asStateFlow()

    private val _statusVisibility = MutableStateFlow(Status.Visibility.UNKNOWN)
    val statusVisibility: StateFlow<Status.Visibility> = _statusVisibility.asStateFlow()

    private val _showContentWarning = MutableStateFlow(false)
    val showContentWarning: StateFlow<Boolean> = _showContentWarning.asStateFlow()

    private val _poll = MutableStateFlow(null as NewPoll?)
    val poll: StateFlow<NewPoll?> = _poll.asStateFlow()

    private val _scheduledAt = MutableStateFlow(null as String?)
    val scheduledAt: StateFlow<String?> = _scheduledAt.asStateFlow()

    private val _media = MutableStateFlow(emptyList<QueuedMedia>())
    val media: StateFlow<List<QueuedMedia>> = _media.asStateFlow()

    private val _uploadError = MutableSharedFlow<Throwable>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val uploadError: SharedFlow<Throwable> = _uploadError.asSharedFlow()

    private val _closeConfirmation = MutableStateFlow(ConfirmationKind.NONE)
    val closeConfirmation: StateFlow<ConfirmationKind> = _closeConfirmation.asStateFlow()

    private lateinit var composeKind: ComposeKind

    // Used in ComposeActivity to pass state to result function when cropImage contract inflight
    var cropImageItemOld: QueuedMedia? = null

    private var setupComplete = false

    suspend fun pickMedia(
        mediaUri: Uri,
        description: String? = null,
        focus: Attachment.Focus? = null
    ): Result<QueuedMedia> = withContext(
        Dispatchers.IO
    ) {
        try {
            val (type, uri, size) = mediaUploader.prepareMedia(mediaUri, instanceInfo.first())
            val mediaItems = _media.value
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

        _media.update { mediaList ->
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
                mediaList.map {
                    if (it.localId == replaceItem.localId) mediaItem else it
                }
            } else { // Append
                mediaList + mediaItem
            }
        }
        val mediaItem = stashMediaItem!! // stashMediaItem is always non-null and uncaptured at this point, but Kotlin doesn't know that

        viewModelScope.launch {
            mediaUploader
                .uploadMedia(mediaItem, instanceInfo.first())
                .collect { event ->
                    val item = _media.value.find { it.localId == mediaItem.localId }
                        ?: return@collect
                    val newMediaItem = when (event) {
                        is UploadEvent.ProgressEvent ->
                            item.copy(uploadPercent = event.percentage)
                        is UploadEvent.FinishedEvent ->
                            item.copy(
                                id = event.mediaId,
                                uploadPercent = -1,
                                state = if (event.processed) {
                                    QueuedMedia.State.PROCESSED
                                } else {
                                    QueuedMedia.State.UNPROCESSED
                                }
                            )
                        is UploadEvent.ErrorEvent -> {
                            _media.update { mediaList -> mediaList.filter { it.localId != mediaItem.localId } }
                            _uploadError.emit(event.error)
                            return@collect
                        }
                    }
                    _media.update { mediaList ->
                        mediaList.map { mediaItem ->
                            if (mediaItem.localId == newMediaItem.localId) {
                                newMediaItem
                            } else {
                                mediaItem
                            }
                        }
                    }
                }
        }
        updateCloseConfirmation()
        return mediaItem
    }

    fun changeStatusVisibility(visibility: Status.Visibility) {
        _statusVisibility.value = visibility
    }

    private fun addUploadedMedia(
        id: String,
        type: QueuedMedia.Type,
        uri: Uri,
        description: String?,
        focus: Attachment.Focus?
    ) {
        _media.update { mediaList ->
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
            mediaList + mediaItem
        }
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaUploader.cancelUploadScope(item.localId)
        _media.update { mediaList -> mediaList.filter { it.localId != item.localId } }
        updateCloseConfirmation()
    }

    fun toggleMarkSensitive() {
        this._markMediaAsSensitive.value = this._markMediaAsSensitive.value != true
    }

    fun updateContent(newContent: String?) {
        currentContent = newContent
        updateCloseConfirmation()
    }

    fun updateContentWarning(newContentWarning: String?) {
        currentContentWarning = newContentWarning
        updateCloseConfirmation()
    }

    private fun updateCloseConfirmation() {
        val contentWarning = if (_showContentWarning.value) {
            currentContentWarning
        } else {
            ""
        }
        this._closeConfirmation.value = if (didChange(currentContent, contentWarning)) {
            when (composeKind) {
                ComposeKind.NEW -> if (isEmpty(currentContent, contentWarning)) {
                    ConfirmationKind.NONE
                } else {
                    ConfirmationKind.SAVE_OR_DISCARD
                }
                ComposeKind.EDIT_DRAFT -> if (isEmpty(currentContent, contentWarning)) {
                    ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_DRAFT
                } else {
                    ConfirmationKind.UPDATE_OR_DISCARD
                }
                ComposeKind.EDIT_POSTED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
                ComposeKind.EDIT_SCHEDULED -> ConfirmationKind.CONTINUE_EDITING_OR_DISCARD_CHANGES
            }
        } else {
            ConfirmationKind.NONE
        }
    }

    private fun didChange(content: String?, contentWarning: String?): Boolean {
        val textChanged = content.orEmpty() != startingText.orEmpty()
        val contentWarningChanged = contentWarning.orEmpty() != startingContentWarning
        val mediaChanged = _media.value.isNotEmpty()
        val pollChanged = _poll.value != null
        val didScheduledTimeChange = hasScheduledTimeChanged

        return modifiedInitialState || textChanged || contentWarningChanged || mediaChanged || pollChanged || didScheduledTimeChange
    }

    private fun isEmpty(content: String?, contentWarning: String?): Boolean {
        return !modifiedInitialState && (content.isNullOrBlank() && contentWarning.isNullOrBlank() && _media.value.isEmpty() && _poll.value == null)
    }

    fun contentWarningChanged(value: Boolean) {
        _showContentWarning.value = value
        contentWarningStateChanged = true
        updateCloseConfirmation()
    }

    fun deleteDraft() {
        viewModelScope.launch {
            if (draftId != 0) {
                draftHelper.deleteDraftAndAttachments(draftId)
            }
        }
    }

    fun stopUploads() {
        mediaUploader.cancelUploadScope(*_media.value.map { it.localId }.toIntArray())
    }

    fun shouldShowSaveDraftDialog(): Boolean {
        // if any of the media files need to be downloaded first it could take a while, so show a loading dialog
        return _media.value.any { mediaValue ->
            mediaValue.uri.scheme == "https"
        }
    }

    suspend fun saveDraft(content: String, contentWarning: String) {
        val mediaUris: MutableList<String> = mutableListOf()
        val mediaDescriptions: MutableList<String?> = mutableListOf()
        val mediaFocus: MutableList<Attachment.Focus?> = mutableListOf()
        for (item in _media.value) {
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
            sensitive = _markMediaAsSensitive.value,
            visibility = _statusVisibility.value,
            mediaUris = mediaUris,
            mediaDescriptions = mediaDescriptions,
            mediaFocus = mediaFocus,
            poll = _poll.value,
            failedToSend = false,
            failedToSendAlert = false,
            scheduledAt = _scheduledAt.value,
            language = postLanguage,
            statusId = originalStatusId
        )
    }

    /**
     * Send status to the server.
     * Uses current state plus provided arguments.
     */
    suspend fun sendStatus(content: String, spoilerText: String, accountId: Long) {
        if (!scheduledTootId.isNullOrEmpty()) {
            api.deleteScheduledStatus(scheduledTootId!!)
        }

        val attachedMedia = _media.value.map { item ->
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
            visibility = _statusVisibility.value.serverString(),
            sensitive = attachedMedia.isNotEmpty() && (_markMediaAsSensitive.value || _showContentWarning.value),
            media = attachedMedia,
            scheduledAt = _scheduledAt.value,
            inReplyToId = inReplyToId,
            poll = _poll.value,
            replyingStatusContent = null,
            replyingStatusAuthorUsername = null,
            accountId = accountId,
            draftId = draftId,
            idempotencyKey = randomAlphanumericString(16),
            retries = 0,
            language = postLanguage,
            statusId = originalStatusId
        )

        serviceClient.sendToot(tootToSend)
    }

    private fun updateMediaItem(localId: Int, mutator: (QueuedMedia) -> QueuedMedia) {
        _media.update { mediaList ->
            mediaList.map { mediaItem ->
                if (mediaItem.localId == localId) {
                    mutator(mediaItem)
                } else {
                    mediaItem
                }
            }
        }
    }

    fun updateDescription(localId: Int, description: String) {
        updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(description = description)
        }
    }

    fun updateFocus(localId: Int, focus: Attachment.Focus) {
        updateMediaItem(localId) { mediaItem ->
            mediaItem.copy(focus = focus)
        }
    }

    fun searchAutocompleteSuggestions(token: String): List<AutocompleteResult> {
        return when (token[0]) {
            '@' -> runBlocking {
                api.searchAccounts(query = token.substring(1), limit = 10)
                    .fold({ accounts ->
                        accounts.map { AutocompleteResult.AccountResult(it) }
                    }, { e ->
                        Log.e(TAG, "Autocomplete search for $token failed.", e)
                        emptyList()
                    })
            }
            '#' -> runBlocking {
                api.search(
                    query = token,
                    type = SearchType.Hashtag.apiParameter,
                    limit = 10
                )
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

                emojiList.filter { emoji ->
                    emoji.shortcode.contains(incomplete, ignoreCase = true)
                }.sortedBy { emoji ->
                    emoji.shortcode.indexOf(incomplete, ignoreCase = true)
                }.map { emoji ->
                    AutocompleteResult.EmojiResult(emoji)
                }
            }
            else -> {
                Log.w(TAG, "Unexpected autocompletion token: $token")
                emptyList()
            }
        }
    }

    fun setup(composeOptions: ComposeActivity.ComposeOptions?) {
        if (setupComplete) {
            return
        }

        composeKind = composeOptions?.kind ?: ComposeKind.NEW

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
            _showContentWarning.value = !contentWarning.isNullOrBlank()
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
        } else {
            composeOptions?.mediaAttachments?.forEach { a ->
                // when coming from redraft or ScheduledTootActivity
                val mediaType = when (a.type) {
                    Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
                    Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
                    Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
                }
                addUploadedMedia(a.id, mediaType, a.url.toUri(), a.description, a.meta?.focus)
            }
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
        _statusVisibility.value = startingVisibility
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

        _scheduledAt.value = composeOptions?.scheduledAt

        composeOptions?.sensitive?.let { _markMediaAsSensitive.value = it }

        val poll = composeOptions?.poll
        if (poll != null && composeOptions.mediaAttachments.isNullOrEmpty()) {
            this._poll.value = poll
        }
        replyingStatusContent = composeOptions?.replyingStatusContent
        replyingStatusAuthor = composeOptions?.replyingStatusAuthor

        updateCloseConfirmation()

        setupComplete = true
    }

    fun updatePoll(newPoll: NewPoll?) {
        _poll.value = newPoll
        updateCloseConfirmation()
    }

    fun updateScheduledAt(newScheduledAt: String?) {
        if (newScheduledAt != _scheduledAt.value) {
            hasScheduledTimeChanged = true
        }

        _scheduledAt.value = newScheduledAt
    }

    val editing: Boolean
        get() = !originalStatusId.isNullOrEmpty()

    private companion object {
        const val TAG = "ComposeViewModel"
    }

    enum class ConfirmationKind {
        NONE, // just close
        SAVE_OR_DISCARD,
        UPDATE_OR_DISCARD,
        CONTINUE_EDITING_OR_DISCARD_CHANGES, // editing post
        CONTINUE_EDITING_OR_DISCARD_DRAFT // edit draft
    }
}

/**
 * Thrown when trying to add an image when video is already present or the other way around
 */
class VideoOrImageException : Exception()
