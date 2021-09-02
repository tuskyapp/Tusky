package com.keylesspalace.tusky.components.timeline.viewmodel

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.dec
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.StatusViewData

@ExperimentalPagingApi
class NetworkTimelineRemoteMediator(
    private val viewModel: NetworkTimelineViewModel
) : RemoteMediator<String, StatusViewData>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, StatusViewData>
    ): MediatorResult {

        try {
            val statusResponse = when (loadType) {
                LoadType.REFRESH -> {
                    viewModel.fetchStatusesForKind(null, null, limit = state.config.pageSize)
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = viewModel.nextKey
                    viewModel.fetchStatusesForKind(maxId, null, limit = state.config.pageSize)
                }
            }

            val statuses = statusResponse.body()!!

            val data = statuses.map { status ->
                status.toViewData(false, false) //todo
            }

            val overlappedStatuses = if (statuses.isNotEmpty()) {
                viewModel.statusData.removeAll { statusViewData ->
                    statuses.find { status -> status.id == statusViewData.asStatusOrNull()?.id } != null
                }
            } else {
                false
            }

            if (loadType == LoadType.REFRESH) {

                viewModel.statusData.addAll(0, data)

                if (!overlappedStatuses) {
                    viewModel.statusData.add(statuses.size,StatusViewData.Placeholder(statuses.last().id.dec(), false))
                }

            } else {
                val linkHeader = statusResponse.headers()["Link"]
                val links = HttpHeaderLink.parse(linkHeader)
                val nextId = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

                val topId = state.firstItemOrNull()?.asStatusOrNull()?.id

                Log.d("TimelineMediator", " topId: $topId")
                Log.d("TimelineMediator", "nextId: $nextId")

                viewModel.nextKey = nextId

                viewModel.statusData.addAll(data)
            }

            viewModel.currentSource?.invalidate()
            return MediatorResult.Success(endOfPaginationReached = statuses.isEmpty())
        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }

    override suspend fun initialize() = InitializeAction.SKIP_INITIAL_REFRESH
}
