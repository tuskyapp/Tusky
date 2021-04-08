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

package com.keylesspalace.tusky.network

import android.util.Log
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Status
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import java.lang.IllegalStateException

/**
 * Created by charlag on 3/24/18.
 */

interface TimelineCases {
    fun reblog(status: Status, reblog: Boolean): Single<Status>
    fun favourite(status: Status, favourite: Boolean): Single<Status>
    fun bookmark(status: Status, bookmark: Boolean): Single<Status>
    fun mute(id: String, notifications: Boolean, duration: Int)
    fun block(id: String)
    fun delete(id: String): Single<DeletedStatus>
    fun pin(status: Status, pin: Boolean)
    fun voteInPoll(status: Status, choices: List<Int>): Single<Poll>
    fun muteConversation(status: Status, mute: Boolean): Single<Status>
}

class TimelineCasesImpl(
        private val mastodonApi: MastodonApi,
        private val eventHub: EventHub
) : TimelineCases {

    /**
     * Unused yet but can be use for cancellation later. It's always a good idea to save
     * Disposables.
     */
    private val cancelDisposable = CompositeDisposable()

    override fun reblog(status: Status, reblog: Boolean): Single<Status> {
        val id = status.actionableId

        val call = if (reblog) {
            mastodonApi.reblogStatus(id)
        } else {
            mastodonApi.unreblogStatus(id)
        }
        return call.doAfterSuccess {
            eventHub.dispatch(ReblogEvent(status.id, reblog))
        }
    }

    override fun favourite(status: Status, favourite: Boolean): Single<Status> {
        val id = status.actionableId

        val call = if (favourite) {
            mastodonApi.favouriteStatus(id)
        } else {
            mastodonApi.unfavouriteStatus(id)
        }
        return call.doAfterSuccess {
            eventHub.dispatch(FavoriteEvent(status.id, favourite))
        }
    }

    override fun bookmark(status: Status, bookmark: Boolean): Single<Status> {
        val id = status.actionableId

        val call = if (bookmark) {
            mastodonApi.bookmarkStatus(id)
        } else {
            mastodonApi.unbookmarkStatus(id)
        }
        return call.doAfterSuccess {
            eventHub.dispatch(BookmarkEvent(status.id, bookmark))
        }
    }

    override fun muteConversation(status: Status, mute: Boolean): Single<Status> {
        val id = status.actionableId

        val call = if (mute) {
            mastodonApi.muteConversation(id)
        } else {
            mastodonApi.unmuteConversation(id)
        }
        return call.doAfterSuccess {
            eventHub.dispatch(MuteConversationEvent(status.id, mute))
        }
    }

    override fun mute(id: String, notifications: Boolean, duration: Int) {
        mastodonApi.muteAccount(id, notifications, duration)
                .subscribe({
                    eventHub.dispatch(MuteEvent(id))
                }, { t ->
                    Log.w("Failed to mute account", t)
                })
                .addTo(cancelDisposable)
    }

    override fun block(id: String) {
        mastodonApi.blockAccount(id)
                .subscribe({
                    eventHub.dispatch(BlockEvent(id))
                }, { t ->
                    Log.w("Failed to block account", t)
                })
                .addTo(cancelDisposable)
    }

    override fun delete(id: String): Single<DeletedStatus> {
        return mastodonApi.deleteStatus(id)
                .doAfterSuccess {
                    eventHub.dispatch(StatusDeletedEvent(id))
                }
    }

    override fun pin(status: Status, pin: Boolean) {
        // Replace with extension method if we use RxKotlin
        (if (pin) mastodonApi.pinStatus(status.id) else mastodonApi.unpinStatus(status.id))
                .subscribe({ updatedStatus ->
                    status.pinned = updatedStatus.pinned
                }, {})
                .addTo(this.cancelDisposable)
    }

    override fun voteInPoll(status: Status, choices: List<Int>): Single<Poll> {
        val pollId = status.actionableStatus.poll?.id

        if(pollId == null || choices.isEmpty()) {
            return Single.error(IllegalStateException())
        }

        return mastodonApi.voteInPoll(pollId, choices).doAfterSuccess {
            eventHub.dispatch(PollVoteEvent(status.id, it))
        }
    }

}