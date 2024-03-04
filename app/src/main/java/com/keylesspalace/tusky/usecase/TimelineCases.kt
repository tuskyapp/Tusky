/* Copyright 2018 charlag
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

package com.keylesspalace.tusky.usecase

import android.util.Log
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.Single
import com.keylesspalace.tusky.util.getServerErrorMessage
import javax.inject.Inject
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Created by charlag on 3/24/18.
 */

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) {

    suspend fun reblog(statusId: String, reblog: Boolean): Result<Status> {
        return if (reblog) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }.onSuccess { status ->
            if (status.reblog != null) {
                // when reblogging, the Mastodon Api does not return the reblogged status directly
                // but the newly created status with reblog set to the reblogged status
                eventHub.dispatch(StatusChangedEvent(status.reblog))
            } else {
                eventHub.dispatch(StatusChangedEvent(status))
            }
        }
    }

    fun reblogOld(statusId: String, reblog: Boolean): Single<Status> {
        return Single { reblog(statusId, reblog) }
    }

    suspend fun favourite(statusId: String, favourite: Boolean): Result<Status> {
        return if (favourite) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }.onSuccess { status ->
            eventHub.dispatch(StatusChangedEvent(status))
        }
    }

    fun favouriteOld(statusId: String, favourite: Boolean): Single<Status> {
        return Single { favourite(statusId, favourite) }
    }

    suspend fun bookmark(statusId: String, bookmark: Boolean): Result<Status> {
        return if (bookmark) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }.onSuccess { status ->
            eventHub.dispatch(StatusChangedEvent(status))
        }
    }

    fun bookmarkOld(statusId: String, bookmark: Boolean): Single<Status> {
        return Single { bookmark(statusId, bookmark) }
    }

    suspend fun muteConversation(statusId: String, mute: Boolean): Result<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess {
            eventHub.dispatch(MuteConversationEvent(statusId, mute))
        }
    }

    suspend fun mute(statusId: String, notifications: Boolean, duration: Int?) {
        try {
            mastodonApi.muteAccount(statusId, notifications, duration)
            eventHub.dispatch(MuteEvent(statusId))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to mute account", t)
        }
    }

    suspend fun block(statusId: String) {
        try {
            mastodonApi.blockAccount(statusId)
            eventHub.dispatch(BlockEvent(statusId))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to block account", t)
        }
    }

    suspend fun delete(statusId: String): Result<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Log.w(TAG, "Failed to delete status", it) }
    }

    suspend fun pin(statusId: String, pin: Boolean): Result<Status> {
        return if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }.fold({ status ->
            eventHub.dispatch(StatusChangedEvent(status))
            Result.success(status)
        }, { e ->
            Log.w(TAG, "Failed to change pin state", e)
            Result.failure(TimelineError(e.getServerErrorMessage()))
        })
    }

    suspend fun voteInPoll(
        statusId: String,
        pollId: String,
        choices: List<Int>
    ): Result<Poll> {
        if (choices.isEmpty()) {
            return Result.failure(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).onSuccess { poll ->
            eventHub.dispatch(PollVoteEvent(statusId, poll))
        }
    }

    fun voteInPollOld(statusId: String, pollId: String, choices: List<Int>): Single<Poll> {
        return Single { voteInPoll(statusId, pollId, choices) }
    }

    fun acceptFollowRequestOld(accountId: String): Single<Relationship> {
        return Single { mastodonApi.authorizeFollowRequest(accountId) }
    }

    fun rejectFollowRequestOld(accountId: String): Single<Relationship> {
        return Single { mastodonApi.rejectFollowRequest(accountId) }
    }

    fun notificationsOld(
        maxId: String?,
        sinceId: String?,
        limit: Int?,
        excludes: Set<Notification.Type>?
    ): Single<Response<List<Notification>>> {
        return Single { runCatching { mastodonApi.notifications(maxId, sinceId, limit, excludes) } }
    }

    fun clearNotificationsOld(): Single<ResponseBody> {
        return Single { mastodonApi.clearNotifications() }
    }

    companion object {
        private const val TAG = "TimelineCases"
    }
}

class TimelineError(message: String?) : RuntimeException(message)
