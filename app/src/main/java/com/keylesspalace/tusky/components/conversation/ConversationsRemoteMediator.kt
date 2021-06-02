package com.keylesspalace.tusky.components.conversation

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi

@ExperimentalPagingApi
class ConversationsRemoteMediator(
    private val accountId: Long,
    private val api: MastodonApi,
    private val db: AppDatabase
) : RemoteMediator<Int, ConversationEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationEntity>
    ): MediatorResult {

        try {
            val conversationsResult = when (loadType) {
                LoadType.REFRESH -> {
                    api.getConversations(limit = state.config.initialLoadSize)
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = state.pages.findLast { it.data.isNotEmpty() }?.data?.lastOrNull()?.lastStatus?.id
                    api.getConversations(maxId = maxId, limit = state.config.pageSize)
                }
            }

            if (loadType == LoadType.REFRESH) {
                db.conversationDao().deleteForAccount(accountId)
            }
            db.conversationDao().insert(
                conversationsResult
                    .filterNot { it.lastStatus == null }
                    .map { it.toEntity(accountId) }
            )
            return MediatorResult.Success(endOfPaginationReached = conversationsResult.isEmpty())
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }

    override suspend fun initialize() = InitializeAction.LAUNCH_INITIAL_REFRESH
}
