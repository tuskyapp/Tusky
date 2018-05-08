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
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.network.MastodonApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import javax.inject.Inject

class AuthorizeFollowActivity : BaseActivity(), Injectable, OnFollowingAccountSelectedListener {

    @Inject
    lateinit var mastodonApi : MastodonApi

    lateinit var recyclerView: RecyclerView
    lateinit var cancelButton: AppCompatButton
    lateinit var adapter: FollowingAccountListAdapter

    private var toFollowUsername: String? = null

    private val followingAccounts: MutableList<Pair<AccountEntity, FollowingAccountListAdapter.FollowState>> =
            emptyList<Pair<AccountEntity, FollowingAccountListAdapter.FollowState>>().toMutableList()

    private fun broadcast(action: String, id: String) {
        val intent = Intent(action)
        intent.putExtra("id", id)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onFollowingAccountSelected(account: AccountEntity, position: Int) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authorize_follow)



        if (intent.hasExtra("username")) {
            toFollowUsername = intent.getStringExtra("username")
        } else if (intent.scheme == "web+mastodon" && intent.data.host == "follow") {
            toFollowUsername = intent.data.getQueryParameter("uri").removePrefix("acct:")
        }

        if (toFollowUsername == null) {
            finish()
        }

        recyclerView = findViewById(R.id.selection_frame)
        cancelButton = findViewById(R.id.cancel_button)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = FollowingAccountListAdapter(followingAccounts, this)
        recyclerView.adapter = adapter

        cancelButton.setOnClickListener({ finish() })

        for (account in accountManager.getAllAccountsOrderedByActive()) {
            loadAccount(account)
        }
    }

    private fun loadAccount(account: AccountEntity)  {
        val cb = object : Callback<List<Account>> {

            override fun onResponse(call: Call<List<Account>>, response: Response<List<Account>>) {
                val result = response.body()?.get(0)
                if (response.isSuccessful && result != null) {
                    queryAccount(account, result)
                }
            }

            override fun onFailure(call: Call<List<Account>>, t: Throwable?) {

            }

        }

        mastodonApi.searchAccounts(account.domain, toFollowUsername, true, 1).enqueue(cb)
    }

    private fun queryAccount(account: AccountEntity, toFollowAcc: Account) {

        val cb = object : Callback<List<Relationship>> {

            override fun onResponse(call: Call<List<Relationship>>, response: Response<List<Relationship>>) {
                val relationship = response.body()?.get(0)
                if (response.isSuccessful && relationship != null) {
                    val followState = when {
                        relationship.following -> FollowingAccountListAdapter.FollowState.FOLLOWING
                        relationship.requested -> FollowingAccountListAdapter.FollowState.REQUESTED
                        else -> FollowingAccountListAdapter.FollowState.NOT_FOLLOWING
                    }

                    followingAccounts.add(account to followState)
                    (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(account, followState)
                } else {
                }
            }

            override fun onFailure(call: Call<List<Relationship>>, t: Throwable?) {

            }
        }

        mastodonApi.relationships(account.accessToken, account.domain, listOf(toFollowAcc.id)).enqueue(cb)
    }

}
