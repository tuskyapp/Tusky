package com.keylesspalace.tusky.components.accountlist

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Type
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class AccountListRemoteMediator(
    private val api: MastodonApi,
    private val viewModel: AccountListViewModel
) : RemoteMediator<String, TimelineAccount>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, TimelineAccount>
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): Response<List<TimelineAccount>>? {
        return when (loadType) {
            LoadType.PREPEND -> null
            LoadType.APPEND -> getFetchCallByListType(fromId = viewModel.nextKey)
            LoadType.REFRESH -> {
                viewModel.nextKey = null
                viewModel.accounts.clear()
                getFetchCallByListType(null)
            }
        }
    }

    private fun applyResponse(response: Response<List<TimelineAccount>>): MediatorResult {
        val tags = response.body()
        if (!response.isSuccessful || tags == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")
        viewModel.accounts.addAll(tags)
        viewModel.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }

    private fun requireId(type: Type, id: String?): String {
        return requireNotNull(id) { "id must not be null for type " + type.name }
    }

    private suspend fun getFetchCallByListType(fromId: String?): Response<List<TimelineAccount>> {
        return when (viewModel.type) {
            Type.FOLLOWS -> {
                val accountId = requireId(viewModel.type, viewModel.accountId)
                api.accountFollowing(accountId, fromId)
            }
            Type.FOLLOWERS -> {
                val accountId = requireId(viewModel.type, viewModel.accountId)
                api.accountFollowers(accountId, fromId)
            }
            Type.BLOCKS -> api.blocks(fromId)
            Type.MUTES -> api.mutes(fromId)
            Type.FOLLOW_REQUESTS -> api.followRequests(fromId)
            Type.REBLOGGED -> {
                val statusId = requireId(viewModel.type, viewModel.accountId)
                api.statusRebloggedBy(statusId, fromId)
            }
            Type.FAVOURITED -> {
                val statusId = requireId(viewModel.type, viewModel.accountId)
                api.statusFavouritedBy(statusId, fromId)
            }
        }
    }
}
