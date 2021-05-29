package com.keylesspalace.tusky.components.conversation

import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.network.TimelineCases
import com.keylesspalace.tusky.util.RxAwareViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

class ConversationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val database: AppDatabase,
    private val accountManager: AccountManager,
    api: MastodonApi
) : RxAwareViewModel() {

    @ExperimentalPagingApi
    val conversationFlow = Pager(
        config = PagingConfig(pageSize = 10, enablePlaceholders = false),
        remoteMediator = ConversationsRemoteMediator(accountManager.activeAccount!!.id, api, database),
        pagingSourceFactory = { database.conversationDao().conversationsForAccount(accountManager.activeAccount!!.id) }
    )
        .flow
        .cachedIn(viewModelScope)

    fun favourite(favourite: Boolean, conversation: ConversationEntity) {
            viewModelScope.launch {
                timelineCases.favourite(conversation.lastStatus.toStatus(), favourite).await()

                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(favourited = favourite)
                )

                database.conversationDao().insert(newConversation)
            }
    }

    fun bookmark(bookmark: Boolean, conversation: ConversationEntity) {
            viewModelScope.launch {
                timelineCases.bookmark(conversation.lastStatus.toStatus(), bookmark).await()

                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(bookmarked = bookmark)
                )

                database.conversationDao().insert(newConversation)

            }
    }

    fun voteInPoll(choices: MutableList<Int>, conversation: ConversationEntity) {
            viewModelScope.launch {
                val poll = timelineCases.voteInPoll(conversation.lastStatus.toStatus(), choices).await()
                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(poll = poll)
                )

                database.conversationDao().insert(newConversation)
            }
    }

    fun expandHiddenStatus(expanded: Boolean, conversation: ConversationEntity) {
            viewModelScope.launch {
                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(expanded = expanded)
                )
                saveConversationToDb(newConversation)
            }
    }

    fun collapseLongStatus(collapsed: Boolean, conversation: ConversationEntity) {
            viewModelScope.launch {
                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(collapsed = collapsed)
                )
                saveConversationToDb(newConversation)
            }
    }

    fun showContent(showing: Boolean, conversation: ConversationEntity) {
            viewModelScope.launch {
                val newConversation = conversation.copy(
                    lastStatus = conversation.lastStatus.copy(showingHiddenContent = showing)
                )
                saveConversationToDb(newConversation)
            }
    }

    fun remove(conversation: ConversationEntity) {
        // TODO
    }

    suspend fun saveConversationToDb(conversation: ConversationEntity) {
        database.conversationDao().insert(conversation)
    }

}
