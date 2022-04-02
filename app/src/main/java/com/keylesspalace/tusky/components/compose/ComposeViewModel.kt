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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.InstanceEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.NewPoll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.service.StatusToSend
import com.keylesspalace.tusky.util.Either
import com.keylesspalace.tusky.util.RxAwareViewModel
import com.keylesspalace.tusky.util.VersionUtils
import com.keylesspalace.tusky.util.combineLiveData
import com.keylesspalace.tusky.util.filter
import com.keylesspalace.tusky.util.map
import com.keylesspalace.tusky.util.randomAlphanumericString
import com.keylesspalace.tusky.util.toLiveData
import com.keylesspalace.tusky.util.withoutFirstWhich
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.rxSingle
import java.util.Locale
import javax.inject.Inject

class ComposeViewModel @Inject constructor(
    private val api: MastodonApi,
    private val accountManager: AccountManager,
    private val mediaUploader: MediaUploader,
    private val serviceClient: ServiceClient,
    private val draftHelper: DraftHelper,
    private val db: AppDatabase
) : RxAwareViewModel() {

    private var replyingStatusAuthor: String? = null
    private var replyingStatusContent: String? = null
    internal var startingText: String? = null
    private var draftId: Int = 0
    private var scheduledTootId: String? = null
    private var startingContentWarning: String = ""
    private var inReplyToId: String? = null
    private var startingVisibility: Status.Visibility = Status.Visibility.UNKNOWN

    private var contentWarningStateChanged: Boolean = false
    private var modifiedInitialState: Boolean = false

    private val instance: MutableLiveData<InstanceEntity?> = MutableLiveData(null)

    val instanceParams: LiveData<ComposeInstanceParams> = instance.map { instance ->
        ComposeInstanceParams(
            maxChars = instance?.maximumTootCharacters ?: DEFAULT_CHARACTER_LIMIT,
            pollMaxOptions = instance?.maxPollOptions ?: DEFAULT_MAX_OPTION_COUNT,
            pollMaxLength = instance?.maxPollOptionLength ?: DEFAULT_MAX_OPTION_LENGTH,
            pollMinDuration = instance?.minPollDuration ?: DEFAULT_MIN_POLL_DURATION,
            pollMaxDuration = instance?.maxPollDuration ?: DEFAULT_MAX_POLL_DURATION,
            charactersReservedPerUrl = instance?.charactersReservedPerUrl ?: DEFAULT_MAXIMUM_URL_LENGTH,
            supportsScheduled = instance?.version?.let { VersionUtils(it).supportsScheduledToots() } ?: false
        )
    }
    val emoji: MutableLiveData<List<Emoji>?> = MutableLiveData()
    val markMediaAsSensitive =
        mutableLiveData(accountManager.activeAccount?.defaultMediaSensitivity ?: false)

    val statusVisibility = mutableLiveData(Status.Visibility.UNKNOWN)
    val showContentWarning = mutableLiveData(false)
    val setupComplete = mutableLiveData(false)
    val poll: MutableLiveData<NewPoll?> = mutableLiveData(null)
    val scheduledAt: MutableLiveData<String?> = mutableLiveData(null)

    val media = mutableLiveData<List<QueuedMedia>>(listOf())
    val uploadError = MutableLiveData<Throwable>()

    private val mediaToDisposable = mutableMapOf<Long, Disposable>()

    private val isEditingScheduledToot get() = !scheduledTootId.isNullOrEmpty()

    init {

        Single.zip(
            api.getCustomEmojis(),
            rxSingle {
                api.getInstance().getOrThrow()
            }
        ) { emojis, instance ->
            InstanceEntity(
                instance = accountManager.activeAccount?.domain!!,
                emojiList = emojis,
                maximumTootCharacters = instance.configuration?.statuses?.maxCharacters ?: instance.maxTootChars,
                maxPollOptions = instance.configuration?.polls?.maxOptions ?: instance.pollConfiguration?.maxOptions,
                maxPollOptionLength = instance.configuration?.polls?.maxCharactersPerOption ?: instance.pollConfiguration?.maxOptionChars,
                minPollDuration = instance.configuration?.polls?.minExpiration ?: instance.pollConfiguration?.minExpiration,
                maxPollDuration = instance.configuration?.polls?.maxExpiration ?: instance.pollConfiguration?.maxExpiration,
                charactersReservedPerUrl = instance.configuration?.statuses?.charactersReservedPerUrl,
                version = instance.version
            )
        }
            .doOnSuccess {
                db.instanceDao().insertOrReplace(it)
            }
            .onErrorResumeNext {
                db.instanceDao().loadMetadataForInstance(accountManager.activeAccount?.domain!!)
            }
            .subscribe(
                { instanceEntity ->
                    emoji.postValue(instanceEntity.emojiList)
                    instance.postValue(instanceEntity)
                },
                { throwable ->
                    // this can happen on network error when no cached data is available
                    Log.w(TAG, "error loading instance data", throwable)
                }
            )
            .autoDispose()
    }

    fun pickMedia(uri: Uri, description: String? = null): LiveData<Either<Throwable, QueuedMedia>> {
        // We are not calling .toLiveData() here because we don't want to stop the process when
        // the Activity goes away temporarily (like on screen rotation).
        val liveData = MutableLiveData<Either<Throwable, QueuedMedia>>()
        mediaUploader.prepareMedia(uri)
            .map { (type, uri, size) ->
                val mediaItems = media.value!!
                if (type != QueuedMedia.Type.IMAGE &&
                    mediaItems.isNotEmpty() &&
                    mediaItems[0].type == QueuedMedia.Type.IMAGE
                ) {
                    throw VideoOrImageException()
                } else {
                    addMediaToQueue(type, uri, size, description)
                }
            }
            .subscribe(
                { queuedMedia ->
                    liveData.postValue(Either.Right(queuedMedia))
                },
                { error ->
                    liveData.postValue(Either.Left(error))
                }
            )
            .autoDispose()
        return liveData
    }

    private fun addMediaToQueue(
        type: QueuedMedia.Type,
        uri: Uri,
        mediaSize: Long,
        description: String? = null
    ): QueuedMedia {
        val mediaItem = QueuedMedia(
            localId = System.currentTimeMillis(),
            uri = uri,
            type = type,
            mediaSize = mediaSize,
            description = description
        )
        media.value = media.value!! + mediaItem
        mediaToDisposable[mediaItem.localId] = mediaUploader
            .uploadMedia(mediaItem)
            .subscribe(
                { event ->
                    val item = media.value?.find { it.localId == mediaItem.localId }
                        ?: return@subscribe
                    val newMediaItem = when (event) {
                        is UploadEvent.ProgressEvent ->
                            item.copy(uploadPercent = event.percentage)
                        is UploadEvent.FinishedEvent ->
                            item.copy(id = event.mediaId, uploadPercent = -1)
                    }
                    synchronized(media) {
                        val mediaValue = media.value!!
                        val index = mediaValue.indexOfFirst { it.localId == newMediaItem.localId }
                        media.postValue(
                            if (index == -1) {
                                mediaValue + newMediaItem
                            } else {
                                mediaValue.toMutableList().also { it[index] = newMediaItem }
                            }
                        )
                    }
                },
                { error ->
                    media.postValue(media.value?.filter { it.localId != mediaItem.localId } ?: emptyList())
                    uploadError.postValue(error)
                }
            )
        return mediaItem
    }

    private fun addUploadedMedia(id: String, type: QueuedMedia.Type, uri: Uri, description: String?) {
        val mediaItem = QueuedMedia(System.currentTimeMillis(), uri, type, 0, -1, id, description)
        media.value = media.value!! + mediaItem
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaToDisposable[item.localId]?.dispose()
        media.value = media.value!!.withoutFirstWhich { it.localId == item.localId }
    }

    fun toggleMarkSensitive() {
        this.markMediaAsSensitive.value = this.markMediaAsSensitive.value != true
    }

    fun didChange(content: String?, contentWarning: String?): Boolean {

        val textChanged = !(
            content.isNullOrEmpty() ||
                startingText?.startsWith(content.toString()) ?: false
            )

        val contentWarningChanged = showContentWarning.value!! &&
            !contentWarning.isNullOrEmpty() &&
            !startingContentWarning.startsWith(contentWarning.toString())
        val mediaChanged = !media.value.isNullOrEmpty()
        val pollChanged = poll.value != null

        return modifiedInitialState || textChanged || contentWarningChanged || mediaChanged || pollChanged
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

    fun saveDraft(content: String, contentWarning: String) {
        viewModelScope.launch {
            val mediaUris: MutableList<String> = mutableListOf()
            val mediaDescriptions: MutableList<String?> = mutableListOf()
            media.value?.forEach { item ->
                mediaUris.add(item.uri.toString())
                mediaDescriptions.add(item.description)
            }

            draftHelper.saveDraft(
                draftId = draftId,
                accountId = accountManager.activeAccount?.id!!,
                inReplyToId = inReplyToId,
                content = content,
                contentWarning = contentWarning,
                sensitive = markMediaAsSensitive.value!!,
                visibility = statusVisibility.value!!,
                mediaUris = mediaUris,
                mediaDescriptions = mediaDescriptions,
                poll = poll.value,
                failedToSend = false
            )
        }
    }

    /**
     * Send status to the server.
     * Uses current state plus provided arguments.
     * @return LiveData which will signal once the screen can be closed or null if there are errors
     */
    fun sendStatus(
        content: String,
        spoilerText: String
    ): LiveData<Unit> {

        val deletionObservable = if (isEditingScheduledToot) {
            rxSingle { api.deleteScheduledStatus(scheduledTootId.toString()) }.toObservable().map { }
        } else {
            Observable.just(Unit)
        }.toLiveData()

        val sendObservable = media
            .filter { items -> items.all { it.uploadPercent == -1 } }
            .map {
                val mediaIds = ArrayList<String>()
                val mediaUris = ArrayList<Uri>()
                val mediaDescriptions = ArrayList<String>()
                for (item in media.value!!) {
                    mediaIds.add(item.id!!)
                    mediaUris.add(item.uri)
                    mediaDescriptions.add(item.description ?: "")
                }

                val tootToSend = StatusToSend(
                    text = content,
                    warningText = spoilerText,
                    visibility = statusVisibility.value!!.serverString(),
                    sensitive = mediaUris.isNotEmpty() && (markMediaAsSensitive.value!! || showContentWarning.value!!),
                    mediaIds = mediaIds,
                    mediaUris = mediaUris.map { it.toString() },
                    mediaDescriptions = mediaDescriptions,
                    scheduledAt = scheduledAt.value,
                    inReplyToId = inReplyToId,
                    poll = poll.value,
                    replyingStatusContent = null,
                    replyingStatusAuthorUsername = null,
                    accountId = accountManager.activeAccount!!.id,
                    draftId = draftId,
                    idempotencyKey = randomAlphanumericString(16),
                    retries = 0
                )

                serviceClient.sendToot(tootToSend)
            }

        return combineLiveData(deletionObservable, sendObservable) { _, _ -> }
    }

    fun updateDescription(localId: Long, description: String): LiveData<Boolean> {
        val newList = media.value!!.toMutableList()
        val index = newList.indexOfFirst { it.localId == localId }
        if (index != -1) {
            newList[index] = newList[index].copy(description = description)
        }
        media.value = newList
        val completedCaptioningLiveData = MutableLiveData<Boolean>()
        media.observeForever(object : Observer<List<QueuedMedia>> {
            override fun onChanged(mediaItems: List<QueuedMedia>) {
                val updatedItem = mediaItems.find { it.localId == localId }
                if (updatedItem == null) {
                    media.removeObserver(this)
                } else if (updatedItem.id != null) {
                    api.updateMedia(updatedItem.id, description)
                        .subscribe(
                            {
                                completedCaptioningLiveData.postValue(true)
                            },
                            {
                                completedCaptioningLiveData.postValue(false)
                            }
                        )
                        .autoDispose()
                    media.removeObserver(this)
                }
            }
        })
        return completedCaptioningLiveData
    }

    fun searchAutocompleteSuggestions(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        when (token[0]) {
            '@' -> {
                return try {
                    api.searchAccounts(query = token.substring(1), limit = 10)
                        .blockingGet()
                        .map { ComposeAutoCompleteAdapter.AccountResult(it) }
                } catch (e: Throwable) {
                    Log.e(TAG, String.format("Autocomplete search for %s failed.", token), e)
                    emptyList()
                }
            }
            '#' -> {
                return try {
                    api.searchObservable(query = token, type = SearchType.Hashtag.apiParameter, limit = 10)
                        .blockingGet()
                        .hashtags
                        .map { ComposeAutoCompleteAdapter.HashtagResult(it) }
                } catch (e: Throwable) {
                    Log.e(TAG, String.format("Autocomplete search for %s failed.", token), e)
                    emptyList()
                }
            }
            ':' -> {
                val emojiList = emoji.value ?: return emptyList()

                val incomplete = token.substring(1).lowercase(Locale.ROOT)
                val results = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                val resultsInside = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                for (emoji in emojiList) {
                    val shortcode = emoji.shortcode.lowercase(Locale.ROOT)
                    if (shortcode.startsWith(incomplete)) {
                        results.add(ComposeAutoCompleteAdapter.EmojiResult(emoji))
                    } else if (shortcode.indexOf(incomplete, 1) != -1) {
                        resultsInside.add(ComposeAutoCompleteAdapter.EmojiResult(emoji))
                    }
                }
                if (results.isNotEmpty() && resultsInside.isNotEmpty()) {
                    results.add(ComposeAutoCompleteAdapter.ResultSeparator())
                }
                results.addAll(resultsInside)
                return results
            }
            else -> {
                Log.w(TAG, "Unexpected autocompletion token: $token")
                return emptyList()
            }
        }
    }

    fun setup(composeOptions: ComposeActivity.ComposeOptions?) {

        if (setupComplete.value == true) {
            return
        }

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
            draftAttachments.forEach { attachment -> pickMedia(attachment.uri, attachment.description) }
        } else composeOptions?.mediaAttachments?.forEach { a ->
            // when coming from redraft or ScheduledTootActivity
            val mediaType = when (a.type) {
                Attachment.Type.VIDEO, Attachment.Type.GIFV -> QueuedMedia.Type.VIDEO
                Attachment.Type.UNKNOWN, Attachment.Type.IMAGE -> QueuedMedia.Type.IMAGE
                Attachment.Type.AUDIO -> QueuedMedia.Type.AUDIO
            }
            addUploadedMedia(a.id, mediaType, a.url.toUri(), a.description)
        }

        draftId = composeOptions?.draftId ?: 0
        scheduledTootId = composeOptions?.scheduledTootId
        startingText = composeOptions?.content

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
    }

    fun updatePoll(newPoll: NewPoll) {
        poll.value = newPoll
    }

    fun updateScheduledAt(newScheduledAt: String?) {
        scheduledAt.value = newScheduledAt
    }

    override fun onCleared() {
        for (uploadDisposable in mediaToDisposable.values) {
            uploadDisposable.dispose()
        }
        super.onCleared()
    }

    private companion object {
        const val TAG = "ComposeViewModel"
    }
}

fun <T> mutableLiveData(default: T) = MutableLiveData<T>().apply { value = default }

const val DEFAULT_CHARACTER_LIMIT = 500
private const val DEFAULT_MAX_OPTION_COUNT = 4
private const val DEFAULT_MAX_OPTION_LENGTH = 50
private const val DEFAULT_MIN_POLL_DURATION = 300
private const val DEFAULT_MAX_POLL_DURATION = 604800

// Mastodon only counts URLs as this long in terms of status character limits
const val DEFAULT_MAXIMUM_URL_LENGTH = 23

data class ComposeInstanceParams(
    val maxChars: Int,
    val pollMaxOptions: Int,
    val pollMaxLength: Int,
    val pollMinDuration: Int,
    val pollMaxDuration: Int,
    val charactersReservedPerUrl: Int,
    val supportsScheduled: Boolean
)

/**
 * Thrown when trying to add an image when video is already present or the other way around
 */
class VideoOrImageException : Exception()
