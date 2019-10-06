package com.keylesspalace.tusky.components.compose

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.keylesspalace.tusky.adapter.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.components.compose.ComposeActivity.QueuedMedia
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.*
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.VersionUtils
import com.keylesspalace.tusky.util.map
import com.keylesspalace.tusky.util.toLiveData
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
        private val mediaUploader: MediaUploader
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
    fun addMediaToQueue(type: QueuedMedia.Type, uri: Uri, mediaSize: Long) {
        val mediaItem = QueuedMedia(System.currentTimeMillis(), uri, type, mediaSize)
//        media.value = media.value!! + mediaItem
        mediaUploader
                .uploadMedia(mediaItem)
                .subscribe { event ->
                    val newMediaItem = when (event) {
                        is UploadEvent.ProgressEvent ->
                            mediaItem.copy(uploadPercent = event.percentage)
                        is UploadEvent.FinishedEvent ->
                            mediaItem.copy(id = event.attachment.id, uploadPercent = -1)
                    }
                    synchronized(media) {
                        val mediaValue = media.value!!
                        val index = mediaValue.indexOfFirst { it.localId == newMediaItem.localId }
                        media.postValue(if (index == -1) {
                            mediaValue + newMediaItem
                        } else {
                            mediaValue.toMutableList().apply { this[index] = newMediaItem }
                        })
                    }
                }
                .autoDispose()
    }

    fun removeMediaFromQueue(item: QueuedMedia) {
        media.value = media.value!! - item
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