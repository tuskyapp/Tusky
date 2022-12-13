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
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getServerErrorMessage
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

/**
 * Created by charlag on 3/24/18.
 */

class TimelineCases @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val eventHub: EventHub
) {

    /**
     * Unused yet but can be use for cancellation later. It's always a good idea to save
     * Disposables.
     */
    private val cancelDisposable = CompositeDisposable()

    suspend fun reblog(statusId: String, reblog: Boolean): NetworkResult<Status> {
        val call = if (reblog) {
            mastodonApi.reblogStatus(statusId)
        } else {
            mastodonApi.unreblogStatus(statusId)
        }

        if (call.isSuccess) {
            eventHub.dispatch(ReblogEvent(statusId, reblog))
        }

        return call
    }

    /** Wrapper to call `reblog` from Java code. */
    // TODO: Delete this when there are no Java callers.
    // TODO: Delete org.jetbrains.kotlinx:kotlinx-coroutines-jdk8 from build.gradle too
    fun reblogFromJava(statusId: String, reblog: Boolean): CompletableFuture<NetworkResult<Status>> =
        CoroutineScope(Dispatchers.IO).future { reblog(statusId, reblog) }

    suspend fun favourite(statusId: String, favourite: Boolean): NetworkResult<Status> {
        val call = if (favourite) {
            mastodonApi.favouriteStatus(statusId)
        } else {
            mastodonApi.unfavouriteStatus(statusId)
        }

        if (call.isSuccess) {
            eventHub.dispatch(FavoriteEvent(statusId, favourite))
        }

        return call
    }

    /** Wrapper to call `reblog` from Java code. */
    // TODO: Delete this when there are no Java callers.
    // TODO: Delete org.jetbrains.kotlinx:kotlinx-coroutines-jdk8 from build.gradle too
    fun favouriteFromJava(statusId: String, favourite: Boolean): CompletableFuture<NetworkResult<Status>> =
        CoroutineScope(Dispatchers.IO).future { favourite(statusId, favourite) }

    suspend fun bookmark(statusId: String, bookmark: Boolean): NetworkResult<Status> {
        val call = if (bookmark) {
            mastodonApi.bookmarkStatus(statusId)
        } else {
            mastodonApi.unbookmarkStatus(statusId)
        }

        if (call.isSuccess) {
            eventHub.dispatch(BookmarkEvent(statusId, bookmark))
        }

        return call
    }

    /** Wrapper to call `reblog` from Java code. */
    // TODO: Delete this when there are no Java callers.
    // TODO: Delete org.jetbrains.kotlinx:kotlinx-coroutines-jdk8 from build.gradle too
    fun bookmarkFromJava(statusId: String, bookmark: Boolean): CompletableFuture<NetworkResult<Status>> =
        CoroutineScope(Dispatchers.IO).future { bookmark(statusId, bookmark) }

    suspend fun muteConversation(statusId: String, mute: Boolean): NetworkResult<Status> {
        val result = if (mute) {
            mastodonApi.muteConversation(statusId)
        } else {
            mastodonApi.unmuteConversation(statusId)
        }
        if (result.isSuccess) {
            eventHub.dispatch(MuteConversationEvent(statusId, mute))
        }
        return result
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
        val result = mastodonApi.deleteStatus(statusId)
        result.fold(
            {
                eventHub.dispatch(StatusDeletedEvent(statusId))
            },
            {
                Log.w(TAG, "Failed to delete status", it)
            }
        )
        return result
    }

    suspend fun pin(statusId: String, pin: Boolean): NetworkResult<Status> {
        val result = if (pin) {
            mastodonApi.pinStatus(statusId)
        } else {
            mastodonApi.unpinStatus(statusId)
        }
        result.fold(
            {
                eventHub.dispatch(PinEvent(statusId, pin))
            },
            {
                Log.w(TAG, "Failed to change pin state", it)
            }
        )
        return result
    }

    fun voteInPoll(statusId: String, pollId: String, choices: List<Int>): Single<Poll> {
        if (choices.isEmpty()) {
            return Single.error(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).doAfterSuccess {
            eventHub.dispatch(PollVoteEvent(statusId, it))
        }
    }

    private fun <T : Any> convertError(e: Throwable): Single<T> {
        return Single.error(TimelineError(e.getServerErrorMessage()))
    }

    companion object {
        private const val TAG = "TimelineCases"
    }
}

class TimelineError(message: String?) : RuntimeException(message)
