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
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.PollShowResultsEvent
import com.keylesspalace.tusky.appstore.PollVoteEvent
import com.keylesspalace.tusky.appstore.StatusChangedEvent
import com.keylesspalace.tusky.appstore.StatusDeletedEvent
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.Translation
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getServerErrorMessage
import java.util.Locale
import javax.inject.Inject

/**
 * Created by charlag on 3/24/18.
 */

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) {

    suspend fun reblog(statusId: String, reblog: Boolean, visibility: Status.Visibility = Status.Visibility.PUBLIC): NetworkResult<Status> {
        return if (reblog) {
            mastodonApi.reblogStatus(statusId, visibility.stringValue)
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

    suspend fun favourite(statusId: String, favourite: Boolean): NetworkResult<Status> {
        return if (favourite) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }.onSuccess { status ->
            eventHub.dispatch(StatusChangedEvent(status))
        }
    }

    suspend fun bookmark(statusId: String, bookmark: Boolean): NetworkResult<Status> {
        return if (bookmark) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }.onSuccess { status ->
            eventHub.dispatch(StatusChangedEvent(status))
        }
    }

    suspend fun muteConversation(statusId: String, mute: Boolean): NetworkResult<Status> {
        return if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }.onSuccess { status ->
            eventHub.dispatch(StatusChangedEvent(status))
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

    suspend fun delete(statusId: String, deleteMedia: Boolean): NetworkResult<DeletedStatus> {
        return mastodonApi.deleteStatus(statusId, deleteMedia)
            .onSuccess { eventHub.dispatch(StatusDeletedEvent(statusId)) }
            .onFailure { Log.w(TAG, "Failed to delete status", it) }
    }

    suspend fun pin(statusId: String, pin: Boolean): NetworkResult<Status> {
        return if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }.fold({ status ->
            eventHub.dispatch(StatusChangedEvent(status))
            NetworkResult.success(status)
        }, { e ->
            Log.w(TAG, "Failed to change pin state", e)
            NetworkResult.failure(TimelineError(e.getServerErrorMessage()))
        })
    }

    suspend fun voteInPoll(
        statusId: String,
        pollId: String,
        choices: List<Int>
    ): NetworkResult<Poll> {
        if (choices.isEmpty()) {
            return NetworkResult.failure(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).onSuccess { poll ->
            eventHub.dispatch(PollVoteEvent(statusId, poll))
        }
    }

    suspend fun showPollResults(statusId: String) {
        eventHub.dispatch(PollShowResultsEvent(statusId))
    }

    suspend fun translate(
        statusId: String
    ): NetworkResult<Translation> {
        return mastodonApi.translate(statusId, Locale.getDefault().language)
    }

    companion object {
        private const val TAG = "TimelineCases"
    }
}

class TimelineError(message: String?) : RuntimeException(message)
