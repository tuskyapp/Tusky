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
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SimpleItemAnimator
import android.widget.Button
import android.widget.Toast
import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter
import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter.FollowState.*
import com.keylesspalace.tusky.adapter.OnFollowingAccountSelectedListener
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.AuthorizeFollow
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
    lateinit var cancelButton: Button
    lateinit var adapter: FollowingAccountListAdapter

    private var toFollowUsername: String? = null

    private fun broadcast(action: String, id: String) {
        val intent = Intent(action)
        intent.putExtra("id", id)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    override fun onFollowingAccountSelected(authorizeFollow: AuthorizeFollow) {
        (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(authorizeFollow.accountEntity, authorizeFollow.followState, anyPendingTransaction = true)

        val followCb = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {
                if (!response.isSuccessful || response.body() == null) {
                    onNetworkError()
                    authorizeFollow.followState
                    (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(authorizeFollow.accountEntity, authorizeFollow.followState, anyPendingTransaction = false)

                    return
                }

                val relationship = response.body()!!
                val newFollowState = when {
                    relationship.following -> FOLLOWING
                    relationship.requested -> REQUESTED
                    else -> NOT_FOLLOWING
                }

                (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(authorizeFollow.accountEntity, newFollowState, anyPendingTransaction = false)
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onNetworkError()
                (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(authorizeFollow.accountEntity, authorizeFollow.followState, anyPendingTransaction = false)
            }

        }

        if (!authorizeFollow.anyPendingTransaction) {
            when (authorizeFollow.followState) {
                NOT_FOLLOWING -> mastodonApi.followAccount(authorizeFollow.accountEntity.accessToken,
                        authorizeFollow.accountEntity.domain,
                        authorizeFollow.subjectAccount.id)
                        .enqueue(followCb)
                FOLLOWING -> mastodonApi.unfollowAccount(authorizeFollow.accountEntity.accessToken,
                        authorizeFollow.accountEntity.domain,
                        authorizeFollow.subjectAccount.id)
                        .enqueue(followCb)
                REQUESTED -> handleFollowRequest(authorizeFollow)
            }
        }

    }

    private fun onNetworkError() {
        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG)
                .show()
    }

    private fun handleFollowRequest(authorizeFollow: AuthorizeFollow) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        adapter = FollowingAccountListAdapter(mutableListOf(), this)
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
                } else {
                    onNetworkError()
                }
            }

            override fun onFailure(call: Call<List<Account>>, t: Throwable?) {
                onNetworkError()
            }

        }

        mastodonApi.searchAccounts(account.accessToken, account.domain, toFollowUsername, true, 1).enqueue(cb)
    }

    private fun queryAccount(accountEntity: AccountEntity, account: Account) {

        val cb = object : Callback<List<Relationship>> {

            override fun onResponse(call: Call<List<Relationship>>, response: Response<List<Relationship>>) {
                val relationship = response.body()?.get(0)
                if (response.isSuccessful && relationship != null) {
                    val followState = when {
                        relationship.following -> FOLLOWING
                        relationship.requested -> REQUESTED
                        else -> NOT_FOLLOWING
                    }

                    (recyclerView.adapter as FollowingAccountListAdapter).updateAccount(accountEntity, followState, account = account)
                } else {
                    onNetworkError()
                }
            }

            override fun onFailure(call: Call<List<Relationship>>, t: Throwable?) {
                onNetworkError()
            }
        }

        mastodonApi.relationships(accountEntity.accessToken, accountEntity.domain, listOf(account.id)).enqueue(cb)
    }

}
