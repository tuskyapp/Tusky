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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.EmptyPagingSource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

class ConversationsViewModel @Inject constructor(
    private val timelineCases: TimelineCases,
    private val database: AppDatabase,
    private val accountManager: AccountManager,
    private val api: MastodonApi
) : ViewModel() {

    @OptIn(ExperimentalPagingApi::class)
    val conversationFlow = Pager(
        config = PagingConfig(pageSize = 30),
        remoteMediator = ConversationsRemoteMediator(api, database, accountManager),
        pagingSourceFactory = {
            val activeAccount = accountManager.activeAccount
            if (activeAccount == null) {
                EmptyPagingSource()
            } else {
                database.conversationDao().conversationsForAccount(activeAccount.id)
            }
        }
    )
        .flow
        .map { pagingData ->
            pagingData.map { conversation -> conversation.toViewData() }
        }
        .cachedIn(viewModelScope)

    fun favourite(favourite: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                timelineCases.favourite(conversation.lastStatus.id, favourite).await()

                val newConversation = conversation.toEntity(
                    accountId = accountManager.activeAccount!!.id,
                    favourited = favourite
                )

                saveConversationToDb(newConversation)
            } catch (e: Exception) {
                Log.w(TAG, "failed to favourite status", e)
            }
        }
    }

    fun bookmark(bookmark: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                timelineCases.bookmark(conversation.lastStatus.id, bookmark).await()

                val newConversation = conversation.toEntity(
                    accountId = accountManager.activeAccount!!.id,
                    bookmarked = bookmark
                )

                saveConversationToDb(newConversation)
            } catch (e: Exception) {
                Log.w(TAG, "failed to bookmark status", e)
            }
        }
    }

    fun voteInPoll(choices: List<Int>, conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                val poll = timelineCases.voteInPoll(conversation.lastStatus.id, conversation.lastStatus.status.poll?.id!!, choices).await()
                val newConversation = conversation.toEntity(
                    accountId = accountManager.activeAccount!!.id,
                    poll = poll
                )

                saveConversationToDb(newConversation)
            } catch (e: Exception) {
                Log.w(TAG, "failed to vote in poll", e)
            }
        }
    }

    fun expandHiddenStatus(expanded: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toEntity(
                accountId = accountManager.activeAccount!!.id,
                expanded = expanded
            )
            saveConversationToDb(newConversation)
        }
    }

    fun collapseLongStatus(collapsed: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toEntity(
                accountId = accountManager.activeAccount!!.id,
                collapsed = collapsed
            )
            saveConversationToDb(newConversation)
        }
    }

    fun showContent(showing: Boolean, conversation: ConversationViewData) {
        viewModelScope.launch {
            val newConversation = conversation.toEntity(
                accountId = accountManager.activeAccount!!.id,
                showingHiddenContent = showing
            )
            saveConversationToDb(newConversation)
        }
    }

    fun remove(conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                api.deleteConversation(conversationId = conversation.id)

                database.conversationDao().delete(
                    id = conversation.id,
                    accountId = accountManager.activeAccount!!.id
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to delete conversation", e)
            }
        }
    }

    fun muteConversation(conversation: ConversationViewData) {
        viewModelScope.launch {
            try {
                timelineCases.muteConversation(
                    conversation.lastStatus.id,
                    !(conversation.lastStatus.status.muted ?: false)
                ).await()

                val newConversation = conversation.toEntity(
                    accountId = accountManager.activeAccount!!.id,
                    muted = !(conversation.lastStatus.status.muted ?: false)
                )

                database.conversationDao().insert(newConversation)
            } catch (e: Exception) {
                Log.w(TAG, "failed to mute conversation", e)
            }
        }
    }

    private suspend fun saveConversationToDb(conversation: ConversationEntity) {
        database.conversationDao().insert(conversation)
    }

    companion object {
        private const val TAG = "ConversationsViewModel"
    }
}
