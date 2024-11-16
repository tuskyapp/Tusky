package com.keylesspalace.tusky.components.notifications.requests

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class NotificationRequestsRemoteMediator(
    private val api: MastodonApi,
    private val viewModel: NotificationRequestsViewModel
) : RemoteMediator<String, NotificationRequest>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, NotificationRequest>
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): Response<List<NotificationRequest>>? {
        return when (loadType) {
            LoadType.PREPEND -> null
            LoadType.APPEND -> api.getNotificationRequests(maxId = viewModel.nextKey)
            LoadType.REFRESH -> {
                viewModel.nextKey = null
                viewModel.requestData.clear()
                api.getNotificationRequests()
            }
        }
    }

    private fun applyResponse(response: Response<List<NotificationRequest>>): MediatorResult {
        val tags = response.body()
        if (!response.isSuccessful || tags == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")
        viewModel.requestData.addAll(tags)
        viewModel.currentSource?.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }
}
