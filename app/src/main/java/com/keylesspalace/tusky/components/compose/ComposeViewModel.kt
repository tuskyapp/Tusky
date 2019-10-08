package com.keylesspalace.tusky.components.compose

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.adapter.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.service.TootToSend
import com.keylesspalace.tusky.util.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.util.*
import javax.inject.Inject

open class RxAwareViewModel : ViewModel() {
    private val disposables = CompositeDisposable()

    fun Disposable.autoDispose() = disposables.add(this)

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }
}


class ComposeViewModel
@Inject constructor(
        private val api: MastodonApi,
        private val accountManager: AccountManager,
        private val mediaUploader: MediaUploader,
        private val serviceClient: ServiceClient
) : RxAwareViewModel() {
    private val instance: LiveData<Instance?> = api.getInstance()
            .map { listOf(it) }
            .onErrorReturnItem(listOf())
            .toLiveData()
            .map { it.firstOrNull() }


    val instanceParams: LiveData<ComposeInstanceParams> = instance.map { instance ->
        ComposeInstanceParams(
                instance?.maxTootChars ?: 500,
                instance?.pollLimits?.maxOptions ?: DEFAULT_MAX_OPTION_COUNT,
                instance?.pollLimits?.maxOptionChars ?: DEFAULT_MAX_OPTION_LENGTH,
                instance?.version?.let { VersionUtils(it).supportsScheduledToots() } ?: false
        )
    }
    val emoji: LiveData<List<Emoji>> = api.getCustomEmojis()
            .map { emojiList ->
                emojiList.sortedBy { it.shortcode.toLowerCase(Locale.ROOT) }
            }
            .onErrorReturnItem(listOf())
            .toLiveData()
    val markMediaAsSensitive =
            mutableLiveData(accountManager.activeAccount?.defaultMediaSensitivity ?: false)

    fun toggleMarkSensitive() {
        this.markMediaAsSensitive.value = !this.markMediaAsSensitive.value!!
    }

    val statusVisibility = mutableLiveData(Status.Visibility.UNKNOWN)
    val hideStatustext = mutableLiveData(false)
    val poll: MutableLiveData<NewPoll?> = mutableLiveData(null)

    val media = mutableLiveData<List<QueuedMedia>>(listOf())
    private val mediaToDisposable = mutableMapOf<Long, Disposable>()

    fun addMediaToQueue(type: QueuedMedia.Type, uri: Uri, mediaSize: Long) {
        val mediaItem = QueuedMedia(System.currentTimeMillis(), uri, type, mediaSize)
        media.value = media.value!! + mediaItem
        mediaToDisposable[mediaItem.localId] = mediaUploader
                .uploadMedia(mediaItem)
                .subscribe { event ->
                    val item = media.value!!.find { it.localId == mediaItem.localId }
                            ?: return@subscribe
                    val newMediaItem = when (event) {
                        is UploadEvent.ProgressEvent ->
                            item.copy(uploadPercent = event.percentage)
                        is UploadEvent.FinishedEvent ->
                            item.copy(id = event.attachment.id, uploadPercent = -1)
                    }
                    synchronized(media) {
                        val mediaValue = media.value!!
                        val index = mediaValue.indexOfFirst { it.localId == newMediaItem.localId }
                        media.postValue(if (index == -1) {
                            mediaValue + newMediaItem
                        } else {
                            mediaValue.toMutableList().also { it[index] = newMediaItem }
                        })
                    }
                }
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        mediaToDisposable[item.localId]?.dispose()
        media.value = media.value!!.withoutFirstWhich { it.localId == item.localId }
    }

    fun deleteDraft() {
        // TODO
    }

    fun saveDraft() {
        // TODO
//        val mediaUris = ArrayList<String>()
//        val mediaDescriptions = ArrayList<String?>()
//        for (item in mediaQueued) {
//            mediaUris.add(item.uri.toString())
//            mediaDescriptions.add(item.description)
//        }
//
//        saveTootHelper!!.saveToot(composeEditField.text.toString(),
//                composeContentWarningField.text.toString(),
//                composeOptions?.savedJsonUrls,
//                mediaUris,
//                mediaDescriptions,
//                savedTootUid,
//                inReplyToId,
//                composeOptions?.replyingStatusContent,
//                composeOptions?.replyingStatusAuthor,
//                viewModel.statusVisibility.value!!,
//                viewModel.poll.value)
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
        return media
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

                    val tootToSend = TootToSend(
                            content,
                            spoilerText,
                            statusVisibility.value!!.serverString(),
                            mediaUris.isNotEmpty() && markMediaAsSensitive.value!!,
                            mediaIds,
                            mediaUris.map { it.toString() },
                            mediaDescriptions,
                            scheduledAt = null, // TODO
                            inReplyToId = null,
                            poll = null,
                            replyingStatusContent = null,
                            replyingStatusAuthorUsername = null,
                            savedJsonUrls = null,
                            accountId = accountManager.activeAccount!!.id,
                            savedTootUid = 0,
                            idempotencyKey = randomAlphanumericString(16),
                            retries = 0
                    )
                    serviceClient.sendToot(tootToSend)
                }
    }

    fun updateDescription(item: QueuedMedia, description: String) {
        media.value = media.value!!.replacedFirstWhich(item.copy(description = description)) {
            it.localId == item.localId
        }
        media.observeForever(object : Observer<List<QueuedMedia>> {
            override fun onChanged(mediaItems: List<QueuedMedia>) {
                val updatedItem = mediaItems.find { it.localId == item.localId }
                if (updatedItem == null) {
                    media.removeObserver(this)
                } else if (updatedItem.id != null) {
                    api.updateMedia(updatedItem.id, description)
                            .subscribe()
                            .autoDispose()
                    media.removeObserver(this)
                }
            }
        })
    }


    fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        // TODO: pull it out of here
        when (token[0]) {
            '@' -> {
                return try {
                    val accountList = api
                            .searchAccounts(token.substring(1), false, 20, null)
                            .blockingGet()
                    accountList.map<Account, ComposeAutoCompleteAdapter.AutocompleteResult> { account: Account -> ComposeAutoCompleteAdapter.AccountResult(account) }
                } catch (e: Throwable) {
                    emptyList()
                }
            }
            '#' -> {
                return try {
                    val (_, _, hashtags) = api.searchObservable(token, null, false, null, null, null).blockingGet()
                    hashtags.map { hashtag -> ComposeAutoCompleteAdapter.HashtagResult(hashtag) }
                } catch (e: Throwable) {
                    Log.e(TAG, String.format("Autocomplete search for %s failed.", token), e)
                    emptyList()
                }
            }
            ':' -> {
                val emojiList = emoji.value ?: return emptyList()

                val incomplete = token.substring(1).toLowerCase(Locale.ROOT)
                val results = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                val resultsInside = ArrayList<ComposeAutoCompleteAdapter.AutocompleteResult>()
                for (emoji in emojiList) {
                    val shortcode = emoji.shortcode.toLowerCase(Locale.ROOT)
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

private const val DEFAULT_MAX_OPTION_COUNT = 4
private const val DEFAULT_MAX_OPTION_LENGTH = 25

data class ComposeInstanceParams(
        val maxChars: Int,
        val pollMaxOptions: Int,
        val pollMaxLength: Int,
        val supportsScheduled: Boolean
)