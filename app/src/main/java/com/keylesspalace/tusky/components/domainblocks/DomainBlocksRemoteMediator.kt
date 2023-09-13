package com.keylesspalace.tusky.components.domainblocks

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class DomainBlocksRemoteMediator(
    private val api: MastodonApi,
    private val repository: DomainBlocksRepository
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

    private suspend fun request(loadType: LoadType): Response<List<String>>? {
        return when (loadType) {
            LoadType.PREPEND -> null
            LoadType.APPEND -> api.domainBlocks(maxId = repository.nextKey)
            LoadType.REFRESH -> {
                repository.nextKey = null
                repository.domains.clear()
                api.domainBlocks()
            }
        }
    }

    private fun applyResponse(response: Response<List<String>>): MediatorResult {
        val tags = response.body()
        if (!response.isSuccessful || tags == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        repository.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")
        repository.domains.addAll(tags)
        repository.invalidate()

        return MediatorResult.Success(endOfPaginationReached = repository.nextKey == null)
    }
}
