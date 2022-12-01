package com.keylesspalace.tusky.components.followedtags

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class FollowedTagsRemoteMediator(
    private val api: MastodonApi,
    private val viewModel: FollowedTagsViewModel,
) : RemoteMediator<String, String>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, String>
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): Response<List<HashTag>>? {
        return when (loadType) {
            LoadType.PREPEND -> null
            LoadType.APPEND -> api.followedTags(maxId = viewModel.nextKey)
            LoadType.REFRESH -> {
                viewModel.nextKey = null
                viewModel.tags.clear()
                api.followedTags()
            }
        }
    }

    private fun applyResponse(response: Response<List<HashTag>>): MediatorResult {
        val tags = response.body()
        if (!response.isSuccessful || tags == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")
        viewModel.tags.addAll(tags)
        viewModel.currentSource?.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }
}
