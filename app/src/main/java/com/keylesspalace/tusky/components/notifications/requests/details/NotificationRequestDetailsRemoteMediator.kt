/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.components.notifications.requests.details

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.keylesspalace.tusky.components.notifications.toViewData
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.viewdata.NotificationViewData
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalPagingApi::class)
class NotificationRequestDetailsRemoteMediator(
    private val viewModel: NotificationRequestDetailsViewModel
) : RemoteMediator<String, NotificationViewData>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<String, NotificationViewData>
    ): MediatorResult {
        return try {
            val response = request(loadType)
                ?: return MediatorResult.Success(endOfPaginationReached = true)

            return applyResponse(response)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun request(loadType: LoadType): Response<List<Notification>>? = when (loadType) {
        LoadType.PREPEND -> null
        LoadType.APPEND -> viewModel.api.notifications(maxId = viewModel.nextKey, accountId = viewModel.accountId)
        LoadType.REFRESH -> {
            viewModel.nextKey = null
            viewModel.notificationData.clear()
            viewModel.api.notifications(accountId = viewModel.accountId)
        }
    }

    private fun applyResponse(response: Response<List<Notification>>): MediatorResult {
        val notifications = response.body()
        if (!response.isSuccessful || notifications == null) {
            return MediatorResult.Error(HttpException(response))
        }

        val links = HttpHeaderLink.parse(response.headers()["Link"])
        viewModel.nextKey = HttpHeaderLink.findByRelationType(links, "next")?.uri?.getQueryParameter("max_id")

        val alwaysShowSensitiveMedia = viewModel.accountManager.activeAccount?.alwaysShowSensitiveMedia == true
        val alwaysOpenSpoiler = viewModel.accountManager.activeAccount?.alwaysOpenSpoiler == false
        val notificationData = notifications.map { notification ->
            notification.toViewData(
                isShowingContent = alwaysShowSensitiveMedia,
                isExpanded = alwaysOpenSpoiler,
                true
            )
        }

        viewModel.notificationData.addAll(notificationData)
        viewModel.currentSource?.invalidate()

        return MediatorResult.Success(endOfPaginationReached = viewModel.nextKey == null)
    }
}
