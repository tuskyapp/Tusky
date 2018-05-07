/* Copyright 2018 Jeremiasz Nelz
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

package com.keylesspalace.tusky

import android.content.Intent
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter
import com.keylesspalace.tusky.adapter.OnFollowingAccountSelectedListener
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.receiver.TimelineReceiver
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AuthorizeFollowActivity : BaseActivity(), OnFollowingAccountSelectedListener {

    @Inject
    lateinit var mastodonApi : MastodonApi

    lateinit var recyclerView: RecyclerView
    lateinit var cancelButton: AppCompatButton
    lateinit var adapter: FollowingAccountListAdapter

    private val followingAccounts: List<Pair<AccountEntity, FollowingAccountListAdapter.FollowState>> = emptyList()

    private fun broadcast(action: String, id: String) {
        val intent = Intent(action)
        intent.putExtra("id", id)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onFollowingAccountSelected(account: AccountEntity) {
        var followState: FollowingAccountListAdapter.FollowState = FollowingAccountListAdapter.FollowState.NOT_FOLLOWING
        val cb = object : Callback<Relationship> {

            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {
                val relationship = response.body()
                if (response.isSuccessful && relationship != null) {
                    when {
                        relationship.following -> followState = FollowingAccountListAdapter.FollowState.FOLLOWING
                        relationship.requested -> followState = FollowingAccountListAdapter.FollowState.REQUESTED
                        else -> {
                            followState = FollowingAccountListAdapter.FollowState.NOT_FOLLOWING
                            broadcast(TimelineReceiver.Types.UNFOLLOW_ACCOUNT, account.accountId)
                        }
                    }
                } else {
                }
            }

            override fun onFailure(call: Call<Relationship>?, t: Throwable?) {
            }
        }

        followingAccounts.map { if (it.first == account) Pair(account, followState) else it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authorize_follow)

        recyclerView = findViewById(R.id.selection_frame)
        cancelButton = findViewById(R.id.cancel_button)

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FollowingAccountListAdapter(followingAccounts, this)
        recyclerView.adapter = adapter

        cancelButton.setOnClickListener({ finish() })

        loadAccounts()
    }

    private fun loadAccounts() {
        for (account in accountManager.getAllAccountsOrderedByActive()) {
            mastodonApi.followAccount()
        }
    }


}
