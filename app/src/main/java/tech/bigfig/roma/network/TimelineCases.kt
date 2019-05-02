/* Copyright 2018 charlag
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.network

import io.reactivex.Single
import tech.bigfig.roma.entity.Relationship
import tech.bigfig.roma.entity.Status
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import tech.bigfig.roma.appstore.*
import java.lang.IllegalStateException

/**
 * Created by charlag on 3/24/18.
 */

interface TimelineCases {
    fun reblog(status: Status, reblog: Boolean): Single<Status>
    fun favourite(status: Status, favourite: Boolean): Single<Status>
    fun mute(id: String)
    fun block(id: String)
    fun delete(id: String)
    fun pin(status: Status, pin: Boolean)
    fun voteInPoll(status: Status, choices: List<Int>): Single<Poll>

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
            eventHub.dispatch(FavouriteEvent(status.id, favourite))
        }
    }

    override fun mute(id: String) {
        val call = mastodonApi.muteAccount(id)
        call.enqueue(object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {}

            override fun onFailure(call: Call<Relationship>, t: Throwable) {}
        })
        eventHub.dispatch(MuteEvent(id))
    }

    override fun block(id: String) {
        val call = mastodonApi.blockAccount(id)
        call.enqueue(object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {}

            override fun onFailure(call: Call<Relationship>, t: Throwable) {}
        })
        eventHub.dispatch(BlockEvent(id))

    }

    override fun delete(id: String) {
        val call = mastodonApi.deleteStatus(id)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {}

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
        eventHub.dispatch(StatusDeletedEvent(id))
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