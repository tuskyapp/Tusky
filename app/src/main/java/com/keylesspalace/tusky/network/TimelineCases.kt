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

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.receiver.TimelineReceiver
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Created by charlag on 3/24/18.
 */

interface TimelineCases {
    fun reblogWithCallback(status: Status, reblog: Boolean, callback: Callback<Status>)
    fun favouriteWithCallback(status: Status, favourite: Boolean, callback: Callback<Status>)
    fun mute(id: String)
    fun block(id: String)
    fun delete(id: String)
}

class TimelineCasesImpl(
        private val mastodonApi: MastodonApi,
        private val broadcastManager: LocalBroadcastManager
) : TimelineCases {
    override fun reblogWithCallback(status: Status, reblog: Boolean, callback: Callback<Status>) {
        val id = status.actionableId

        val call = if (reblog) {
            mastodonApi.reblogStatus(id)
        } else {
            mastodonApi.unreblogStatus(id)
        }
        call.enqueue(callback)
    }

    override fun favouriteWithCallback(status: Status, favourite: Boolean, callback: Callback<Status>) {
        val id = status.actionableId

        val call = if (favourite) {
            mastodonApi.favouriteStatus(id)
        } else {
            mastodonApi.unfavouriteStatus(id)
        }
        call.enqueue(callback)
    }

    override fun mute(id: String) {
        val call = mastodonApi.muteAccount(id)
        call.enqueue(object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {}

            override fun onFailure(call: Call<Relationship>, t: Throwable) {}
        })
        val intent = Intent(TimelineReceiver.Types.MUTE_ACCOUNT)
        intent.putExtra("id", id)
        broadcastManager.sendBroadcast(intent)
    }

    override fun block(id: String) {
        val call = mastodonApi.blockAccount(id)
        call.enqueue(object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: retrofit2.Response<Relationship>) {}

            override fun onFailure(call: Call<Relationship>, t: Throwable) {}
        })
        val intent = Intent(TimelineReceiver.Types.BLOCK_ACCOUNT)
        intent.putExtra("id", id)
        broadcastManager.sendBroadcast(intent)
    }

    override fun delete(id: String) {
        val call = mastodonApi.deleteStatus(id)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {}

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
    }

}