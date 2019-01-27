package com.keylesspalace.tusky.components.conversation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.Transformations.map
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Listing
import com.keylesspalace.tusky.util.NetworkState
import javax.inject.Inject

class ConversationsViewModel  @Inject constructor(
            private val repository: ConversationsRepository,
            private val mastodonApi: MastodonApi,
            private val eventHub: EventHub,
            private val database: AppDatabase
    ): ViewModel() {

    private val accountId = MutableLiveData<Long>()

    private val repoResult: LiveData<Listing<ConversationEntity>> = map(accountId) {
        repository.conversations(it, 30)
    }

    val conversations: LiveData<PagedList<ConversationEntity>> = Transformations.switchMap(repoResult) { it.pagedList }
    val networkState: LiveData<NetworkState>  = Transformations.switchMap(repoResult) { it.networkState }
    val refreshState: LiveData<NetworkState> = Transformations.switchMap(repoResult) { it.refreshState }

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
        conversations.value?.getOrNull(position)?.let {
            val conversation = it.copy(lastStatus = it.lastStatus.copy(favourited = favourite))
            database.conversationDao().insert(conversation)
        }

    }


}