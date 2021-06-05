/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.conversation

import android.util.Log
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
import java.lang.Exception
import javax.inject.Inject

class ConversationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val database: AppDatabase,
    private val accountManager: AccountManager,
    private val api: MastodonApi
) : RxAwareViewModel() {

    @ExperimentalPagingApi
    val conversationFlow = Pager(
        config = PagingConfig(pageSize = 10, enablePlaceholders = false, initialLoadSize = 20),
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
        viewModelScope.launch {
            try {
                api.deleteConversation(conversationId = conversation.id)

                database.conversationDao().delete(conversation)
            } catch (e: Exception) {
                Log.w("ConversationsViewModel", "failed to delete conversation", e)
            }
        }
    }

    fun muteConversation(conversation: ConversationEntity) {
        viewModelScope.launch {
            val newStatus = timelineCases.muteConversation(conversation.lastStatus.toStatus(), !conversation.lastStatus.muted).await()

            val newConversation = conversation.copy(
                lastStatus = newStatus.toEntity()
            )

            database.conversationDao().insert(newConversation)
        }
    }

    suspend fun saveConversationToDb(conversation: ConversationEntity) {
        database.conversationDao().insert(conversation)
    }

}
