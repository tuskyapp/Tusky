/* Copyright 2025 Tusky Contributors.
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

package com.keylesspalace.tusky.components.accountlist

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import at.connyduck.calladapter.networkresult.getOrElse
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Type
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class AccountListRemoteMediator(
    private val api: MastodonApi,
    private val viewModel: AccountListViewModel,
    private val fetchRelationships: Boolean
) : RemoteMediator<String, AccountViewData>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, AccountViewData>
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): Response<List<TimelineAccount>>? = when (loadType) {
        LoadType.PREPEND -> null
        LoadType.APPEND -> getFetchCallByListType(fromId = viewModel.nextKey)
        LoadType.REFRESH -> {
            viewModel.nextKey = null
            viewModel.accounts.clear()
            getFetchCallByListType(null)
        }
    }

    private suspend fun applyResponse(response: Response<List<TimelineAccount>>): MediatorResult {
        val accounts = response.body()
        if (!response.isSuccessful || accounts == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

        val relationships = if (fetchRelationships) {
            api.relationships(accounts.map { it.id }).getOrElse { e ->
                return MediatorResult.Error(e)
            }
        } else {
            emptyList()
        }

        val viewModels = accounts.map { account ->
            account.toViewData(
                mutingNotifications = relationships.find { it.id == account.id }?.mutingNotifications == true
            )
        }

        viewModel.accounts.addAll(viewModels)
        viewModel.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }

    private fun requireId(type: Type, id: String?): String = requireNotNull(id) { "id must not be null for type " + type.name }

    private suspend fun getFetchCallByListType(fromId: String?): Response<List<TimelineAccount>> = when (viewModel.type) {
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
