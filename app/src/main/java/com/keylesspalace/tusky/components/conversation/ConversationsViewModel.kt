package com.keylesspalace.tusky.components.conversation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ConversationsViewModel @Inject constructor(
        private val repository: ConversationsRepository,
        private val timelineCases: TimelineCases,
        private val database: AppDatabase,
        private val accountManager: AccountManager
) : ViewModel() {

    private val repoResult = MutableLiveData<Listing<ConversationEntity>>()

    val conversations: LiveData<PagedList<ConversationEntity>> = Transformations.switchMap(repoResult) { it.pagedList }
    val networkState: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.networkState }
    val refreshState: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.refreshState }

    private val disposables = CompositeDisposable()

    fun load() {
        val accountId = accountManager.activeAccount?.id ?: return
        if (repoResult.value == null) {
            repository.refresh(accountId, false)
        }
        repoResult.value = repository.conversations(accountId)
    }

    fun refresh() {
        repoResult.value?.refresh?.invoke()
    }

    fun retry() {
        repoResult.value?.retry?.invoke()
    }

    fun favourite(favourite: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            timelineCases.favourite(conversation.lastStatus.toStatus(), favourite)
                    .flatMap {
                        val newConversation = conversation.copy(
                                lastStatus = conversation.lastStatus.copy(favourited = favourite)
                        )

                        database.conversationDao().insert(newConversation)
                    }
                    .subscribeOn(Schedulers.io())
                    .doOnError { t -> Log.w("ConversationViewModel", "Failed to favourite conversation", t) }
                    .onErrorReturnItem(0)
                    .subscribe()
                    .addTo(disposables)
        }

    }

    fun bookmark(bookmark: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            timelineCases.bookmark(conversation.lastStatus.toStatus(), bookmark)
                    .flatMap {
                        val newConversation = conversation.copy(
                                lastStatus = conversation.lastStatus.copy(bookmarked = bookmark)
                        )

                        database.conversationDao().insert(newConversation)
                    }
                    .subscribeOn(Schedulers.io())
                    .doOnError { t -> Log.w("ConversationViewModel", "Failed to bookmark conversation", t) }
                    .subscribe()
                    .addTo(disposables)
        }

    }

    fun voteInPoll(position: Int, choices: MutableList<Int>) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            timelineCases.voteInPoll(conversation.lastStatus.toStatus(), choices)
                    .flatMap { poll ->
                        val newConversation = conversation.copy(
                                lastStatus = conversation.lastStatus.copy(poll = poll)
                        )

                        database.conversationDao().insert(newConversation)
                    }
                    .subscribeOn(Schedulers.io())
                    .doOnError { t -> Log.w("ConversationViewModel", "Failed to favourite conversation", t) }
                    .onErrorReturnItem(0)
                    .subscribe()
                    .addTo(disposables)
        }

    }

    fun expandHiddenStatus(expanded: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(expanded = expanded)
            )
            saveConversationToDb(newConversation)
        }
    }

    fun collapseLongStatus(collapsed: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(collapsed = collapsed)
            )
            saveConversationToDb(newConversation)
        }
    }

    fun showContent(showing: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(showingHiddenContent = showing)
            )
            saveConversationToDb(newConversation)
        }
    }

    fun remove(position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            /* this is not ideal since deleting last toot from an conversation
               should not delete the conversation but show another toot of the conversation */
            timelineCases.delete(conversation.lastStatus.id)
                    .subscribeOn(Schedulers.io())
                    .doOnError { t -> Log.w("ConversationViewModel", "Failed to delete conversation", t) }
                    .subscribe()
            database.conversationDao().delete(conversation)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
        }
    }

    private fun saveConversationToDb(conversation: ConversationEntity) {
        database.conversationDao().insert(conversation)
                .subscribeOn(Schedulers.io())
                .subscribe()
    }

    override fun onCleared() {
        disposables.dispose()
    }

}