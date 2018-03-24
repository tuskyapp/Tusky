package com.keylesspalace.tusky.network

import android.content.Context
import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.receiver.TimelineReceiver
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.support.annotation.NonNull
import okhttp3.ResponseBody




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
        private val broadcasrManager: LocalBroadcastManager
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

        val call: Call<Status>
        call = if (favourite) {
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
        broadcasrManager.sendBroadcast(intent)
    }

    override fun block(id: String) {
        val call = mastodonApi.blockAccount(id)
        call.enqueue(object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: retrofit2.Response<Relationship>) {}

            override fun onFailure(call: Call<Relationship>, t: Throwable) {}
        })
        val intent = Intent(TimelineReceiver.Types.BLOCK_ACCOUNT)
        intent.putExtra("id", id)
        broadcasrManager.sendBroadcast(intent)
    }

    override fun delete(id: String) {
        val call = mastodonApi.deleteStatus(id)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {}

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
        })
    }

}