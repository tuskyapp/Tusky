package com.keylesspalace.tusky.components.timeline.viewmodel

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.gson.Gson
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.TimelineStatusWithAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.dec
import kotlinx.coroutines.rx3.await

@ExperimentalPagingApi
class CachedTimelineRemoteMediator(
    private val accountId: Long,
    private val api: MastodonApi,
    private val db: AppDatabase,
    private val gson: Gson
) : RemoteMediator<Int, TimelineStatusWithAccount>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, TimelineStatusWithAccount>
    ): MediatorResult {

        try {
            val statusResponse = when (loadType) {
                LoadType.REFRESH -> {
                    api.homeTimeline(limit = state.config.pageSize).await()
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = state.pages.findLast { it.data.isNotEmpty() }?.data?.lastOrNull()?.status?.serverId
                    api.homeTimeline(maxId = maxId, limit = state.config.pageSize).await()
                }
            }

            val statuses = statusResponse.body()!!

            val timelineDao = db.timelineDao()

            db.withTransaction {
                val overlappedStatuses = if (statuses.isNotEmpty()) {
                    timelineDao.deleteRange(accountId, statuses.last().id, statuses.first().id)
                } else {
                    0
                }

                for (status in statuses) {
                    timelineDao.insertAccount(status.account.toEntity(accountId, gson))
                    status.reblog?.account?.toEntity(accountId, gson)?.let { rebloggedAccount ->
                        timelineDao.insertAccount(rebloggedAccount)
                    }
                    timelineDao.insertStatus(status.toEntity(accountId, gson))
                }

                if (loadType == LoadType.REFRESH && overlappedStatuses == 0) {
                    timelineDao.insertStatus(
                        Placeholder(statuses.last().id.dec()).toEntity(accountId)
                    )
                }
            }
            return MediatorResult.Success(endOfPaginationReached = statuses.isEmpty())
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }

    override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH
}
