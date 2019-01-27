package com.keylesspalace.tusky.components.conversation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import javax.inject.Inject

class ConversationsViewModel  @Inject constructor(
        private val repository: ConversationsRepository,
        private val timelineCases: TimelineCases,
        private val database: AppDatabase
): ViewModel() {

    private val accountId = MutableLiveData<Long>()

    private val repoResult: LiveData<Listing<ConversationEntity>> = map(accountId) {
        repository.conversations(it, 30)
    }

    val conversations: LiveData<PagedList<ConversationEntity>> = Transformations.switchMap(repoResult) { it.pagedList }
    val networkState: LiveData<NetworkState>  = Transformations.switchMap(repoResult) { it.networkState }
    val refreshState: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.refreshState }

    private val disposables = CompositeDisposable()


    fun refresh() {
        repoResult.value?.refresh?.invoke()
    }

    fun load(accountId: Long) {
        this.accountId.value = accountId
    }

    fun retry() {
        val listing = repoResult.value
        listing?.retry?.invoke()
    }

    fun favourite(favourite: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            timelineCases.favourite(conversation.lastStatus.id, favourite)
                    .subscribe({
                        val newConversation = conversation.copy(
                                lastStatus = conversation.lastStatus.copy(favourited = favourite)
                        )
                        database.conversationDao().insert(newConversation)
                    }, { t -> Log.w("ConversationViewModel", "Failed to favourite conversation", t) })
                    .addTo(disposables)



        }

    }

    fun expandHiddenStatus(expanded: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->

            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(expanded = expanded)
            )
            database.conversationDao().insert(newConversation)
        }
    }

    fun collapseLongStatus(collapsed: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(collapsed = collapsed)
            )
            database.conversationDao().insert(newConversation)
        }
    }

    fun showContent(showing: Boolean, position: Int) {
        conversations.value?.getOrNull(position)?.let { conversation ->
            val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(showingHiddenContent = showing)
            )
            database.conversationDao().insert(newConversation)
        }
    }

    override fun onCleared() {
        disposables.dispose()
    }

}