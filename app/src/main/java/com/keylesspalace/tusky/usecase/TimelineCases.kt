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
import at.connyduck.calladapter.networkresult.NetworkResult
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import at.connyduck.calladapter.networkresult.onSuccess
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.BookmarkEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.FavoriteEvent
import com.keylesspalace.tusky.appstore.MuteConversationEvent
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PinEvent
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.ReblogEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getServerErrorMessage
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

/**
 * Created by charlag on 3/24/18.
 */

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) {

    suspend fun reblog(statusId: String, reblog: Boolean): NetworkResult<Status> {
        return if (reblog) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(ReblogEvent(statusId, reblog))
        }
    }

    suspend fun favourite(statusId: String, favourite: Boolean): NetworkResult<Status> {
        return if (favourite) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(FavoriteEvent(statusId, favourite))
        }
    }

    suspend fun bookmark(statusId: String, bookmark: Boolean): NetworkResult<Status> {
        return if (bookmark) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }.onSuccess {
            eventHub.dispatch(BookmarkEvent(statusId, bookmark))
        }
    }

    suspend fun muteConversation(statusId: String, mute: Boolean): NetworkResult<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess {
            eventHub.dispatch(MuteConversationEvent(statusId, mute))
        }
    }

    fun reblogOld(statusId: String, reblog: Boolean): Single<Status> {
        val call = if (reblog) {
            mastodonApi.reblogStatusOld(statusId)
        } else {
            mastodonApi.unreblogStatusOld(statusId)
        }
        return call.doAfterSuccess {
            eventHub.dispatchOld(ReblogEvent(statusId, reblog))
        }
    }

    fun favouriteOld(statusId: String, favourite: Boolean): Single<Status> {
        val call = if (favourite) {
            mastodonApi.favouriteStatusOld(statusId)
        } else {
            mastodonApi.unfavouriteStatusOld(statusId)
        }
        return call.doAfterSuccess {
            eventHub.dispatchOld(FavoriteEvent(statusId, favourite))
        }
    }

    fun bookmarkOld(statusId: String, bookmark: Boolean): Single<Status> {
        val call = if (bookmark) {
            mastodonApi.bookmarkStatusOld(statusId)
        } else {
            mastodonApi.unbookmarkStatusOld(statusId)
        }
        return call.doAfterSuccess {
            eventHub.dispatchOld(BookmarkEvent(statusId, bookmark))
        }
    }

    fun muteConversationOld(statusId: String, mute: Boolean): Single<Status> {
        val call = if (mute) {
            mastodonApi.muteConversationOld(statusId)
        } else {
            mastodonApi.unmuteConversationOld(statusId)
        }
        return call.doAfterSuccess {
            eventHub.dispatchOld(MuteConversationEvent(statusId, mute))
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

    suspend fun delete(statusId: String): NetworkResult<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Log.w(TAG, "Failed to delete status", it) }
    }

    suspend fun pin(statusId: String, pin: Boolean): NetworkResult<Status> {
        return if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }.fold({ status ->
            eventHub.dispatch(PinEvent(statusId, pin))
            NetworkResult.success(status)
        }, { e ->
            Log.w(TAG, "Failed to change pin state", e)
            NetworkResult.failure(TimelineError(e.getServerErrorMessage()))
        })
    }

    suspend fun voteInPoll(statusId: String, pollId: String, choices: List<Int>): NetworkResult<Poll> {
        if (choices.isEmpty()) {
            return NetworkResult.failure(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).onSuccess { poll ->
            eventHub.dispatch(PollVoteEvent(statusId, poll))
        }
    }

    fun voteInPollOld(statusId: String, pollId: String, choices: List<Int>): Single<Poll> {
        if (choices.isEmpty()) {
            return Single.error(IllegalStateException())
        }

        return mastodonApi.voteInPollOld(pollId, choices).doAfterSuccess {
            eventHub.dispatchOld(PollVoteEvent(statusId, it))
        }
    }

    fun acceptFollowRequest(accountId: String): Single<Relationship> {
        return mastodonApi.authorizeFollowRequest(accountId)
    }

    fun rejectFollowRequest(accountId: String): Single<Relationship> {
        return mastodonApi.rejectFollowRequest(accountId)
    }

    companion object {
        private const val TAG = "TimelineCases"
    }
}

class TimelineError(message: String?) : RuntimeException(message)
