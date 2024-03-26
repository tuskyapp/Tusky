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

package com.keylesspalace.tusky.components.notifications

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.google.gson.Gson
import com.keylesspalace.tusky.components.timeline.Placeholder
import com.keylesspalace.tusky.components.timeline.toEntity
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.NotificationDataEntity
import com.keylesspalace.tusky.db.TimelineStatusEntity
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.network.MastodonApi
import retrofit2.HttpException

@OptIn(ExperimentalPagingApi::class)
class NotificationsRemoteMediator(
    accountManager: AccountManager,
    private val api: MastodonApi,
    private val db: AppDatabase,
    private val gson: Gson,
    var excludes: Set<Notification.Type>
) : RemoteMediator<Int, NotificationDataEntity>() {

    init {
        println(excludes)
    }

    private var initialRefresh = false

    private val notificationsDao = db.notificationsDao()
    private val timelineDao = db.timelineDao()
    private val activeAccount = accountManager.activeAccount!!

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, NotificationDataEntity>
    ): MediatorResult {
        if (!activeAccount.isLoggedIn()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        try {
            var dbEmpty = false

            val topPlaceholderId = if (loadType == LoadType.REFRESH) {
                notificationsDao.getTopPlaceholderId(activeAccount.id)
            } else {
                null // don't execute the query if it is not needed
            }

            if (!initialRefresh && loadType == LoadType.REFRESH) {
                val topId = notificationsDao.getTopId(activeAccount.id)
                topId?.let { cachedTopId ->
                    val notificationResponse = api.notifications(
                        maxId = cachedTopId,
                        // so already existing placeholders don't get accidentally overwritten
                        sinceId = topPlaceholderId,
                        limit = state.config.pageSize,
                        excludes = excludes
                    )

                    val notifications = notificationResponse.body()
                    if (notificationResponse.isSuccessful && notifications != null) {
                        db.withTransaction {
                            replaceNotificationRange(notifications, state)
                        }
                    }
                }
                initialRefresh = true
                dbEmpty = topId == null
            }

            val notificationResponse = when (loadType) {
                LoadType.REFRESH -> {
                    api.notifications(sinceId = topPlaceholderId, limit = state.config.pageSize, excludes = excludes)
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val maxId = state.pages.findLast { it.data.isNotEmpty() }?.data?.lastOrNull()?.id
                    api.notifications(maxId = maxId, limit = state.config.pageSize, excludes = excludes)
                }
            }

            val notifications = notificationResponse.body()
            if (!notificationResponse.isSuccessful || notifications == null) {
                return MediatorResult.Error(HttpException(notificationResponse))
            }

            db.withTransaction {
                val overlappedNotifications = replaceNotificationRange(notifications, state)

                /* In case we loaded a whole page and there was no overlap with existing statuses,
                   we insert a placeholder because there might be even more unknown statuses */
                if (loadType == LoadType.REFRESH && overlappedNotifications == 0 && notifications.size == state.config.pageSize && !dbEmpty) {
                    /* This overrides the last of the newly loaded statuses with a placeholder
                       to guarantee the placeholder has an id that exists on the server as not all
                       servers handle client generated ids as expected */
                    notificationsDao.insertNotification(
                        Placeholder(notifications.last().id, loading = false).toNotificationEntity(activeAccount.id)
                    )
                }
            }
            return MediatorResult.Success(endOfPaginationReached = notifications.isEmpty())
        } catch (e: Exception) {
            return ifExpected(e) {
                Log.w(TAG, "Failed to load notifications", e)
                MediatorResult.Error(e)
            }
        }
    }

    /**
     * Deletes all notifications in a given range and inserts new notifications.
     * This is necessary so notifications that have been deleted on the server are cleaned up.
     * Should be run in a transaction as it executes multiple db updates
     * @param notifications the new notifications
     * @return the number of old notifications that have been cleared from the database
     */
    private suspend fun replaceNotificationRange(notifications: List<Notification>, state: PagingState<Int, NotificationDataEntity>): Int {
        val overlappedNotifications = if (notifications.isNotEmpty()) {
            notificationsDao.deleteRange(activeAccount.id, notifications.last().id, notifications.first().id)
        } else {
            0
        }

        for (notification in notifications) {
            timelineDao.insertAccount(notification.account.toEntity(activeAccount.id, gson))
            notification.report?.let {
                timelineDao.insertAccount(it.targetAccount.toEntity(activeAccount.id, gson))
                notificationsDao.insertReport(it.toEntity(activeAccount.id))
            }

            // check if we already have one of the newly loaded statuses cached locally
            // in case we do, copy the local state (expanded, contentShowing, contentCollapsed) over so it doesn't get lost
            var oldStatus: TimelineStatusEntity? = null
            for (page in state.pages) {
                oldStatus = page.data.find { s ->
                    s.id == notification.id
                }?.status
                if (oldStatus != null) break
            }

            notification.status?.let {
                val expanded = oldStatus?.expanded ?: activeAccount.alwaysOpenSpoiler
                val contentShowing = oldStatus?.contentShowing ?: activeAccount.alwaysShowSensitiveMedia || !it.actionableStatus.sensitive
                val contentCollapsed = oldStatus?.contentCollapsed ?: true

                timelineDao.insertAccount(it.account.toEntity(activeAccount.id, gson))

                timelineDao.insertStatus(
                    it.toEntity(
                        tuskyAccountId = activeAccount.id,
                        gson = gson,
                        expanded = expanded,
                        contentShowing = contentShowing,
                        contentCollapsed = contentCollapsed
                    )
                )
            }

            notificationsDao.insertNotification(
                notification.toEntity(
                    activeAccount.id
                )
            )
        }
        return overlappedNotifications
    }

    companion object {
        private const val TAG = "NotificationsRM"
    }
}
