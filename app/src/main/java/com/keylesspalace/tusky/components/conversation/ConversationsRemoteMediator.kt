package com.keylesspalace.tusky.components.conversation

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException

@OptIn(ExperimentalPagingApi::class)
class ConversationsRemoteMediator(
    private val accountId: Long,
    private val api: MastodonApi,
    private val db: AppDatabase
) : RemoteMediator<Int, ConversationEntity>() {

    private var nextKey: String? = null

    private var order: Int = 0

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationEntity>
    ): MediatorResult {

        if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        if (loadType == LoadType.REFRESH) {
            nextKey = null
            order = 0
        }

        try {
            val conversationsResponse = api.getConversations(maxId = nextKey, limit = state.config.pageSize)

            val conversations = conversationsResponse.body()
            if (!conversationsResponse.isSuccessful || conversations == null) {
                return MediatorResult.Error(HttpException(conversationsResponse))
            }

            db.withTransaction {

                if (loadType == LoadType.REFRESH) {
                    db.conversationDao().deleteForAccount(accountId)
                }

                val linkHeader = conversationsResponse.headers()["Link"]
                val links = HttpHeaderLink.parse(linkHeader)
                nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

                db.conversationDao().insert(
                    conversations
                        .filterNot { it.lastStatus == null }
                        .map {
                            it.toEntity(accountId, order++)
                        }
                )
            }
            return MediatorResult.Success(endOfPaginationReached = nextKey == null)
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}
