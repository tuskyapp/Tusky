/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.fragment

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.AccountListActivity.Type
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.AccountAdapter
import com.keylesspalace.tusky.adapter.BlocksAdapter
import com.keylesspalace.tusky.adapter.FollowAdapter
import com.keylesspalace.tusky.adapter.FollowRequestsAdapter
import com.keylesspalace.tusky.adapter.MutesAdapter
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import kotlinx.android.synthetic.main.fragment_account_list.*

import javax.inject.Inject

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AccountListFragment : BaseFragment(), AccountActionListener, Injectable {

    @Inject
    lateinit var api: MastodonApi

    private lateinit var type: Type
    private var id: String? = null
    private lateinit var scrollListener: EndlessOnScrollListener
    private lateinit var adapter: AccountAdapter
    private var fetching = false
    private var bottomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getSerializable(ARG_TYPE) as Type
        id = arguments?.getString(ARG_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = layoutManager
        val divider = DividerItemDecoration(context, layoutManager.orientation)
        val drawable = ThemeUtils.getDrawable(context, R.attr.status_divider_drawable, R.drawable.status_divider_dark)
        divider.setDrawable(drawable)
        recyclerView.addItemDecoration(divider)

        adapter = when(type) {
            Type.BLOCKS -> BlocksAdapter(this)
            Type.MUTES -> MutesAdapter(this)
            Type.FOLLOW_REQUESTS -> FollowRequestsAdapter(this)
            else -> FollowAdapter(this)
        }
        recyclerView.adapter = adapter

        scrollListener = object : EndlessOnScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                if (bottomId == null) {
                    return
                }
                fetchAccounts(bottomId)
            }
        }

        recyclerView.addOnScrollListener(scrollListener)

        fetchAccounts()
    }

    override fun onViewAccount(id: String) {
        (activity as BaseActivity?)?.let {
            val intent = AccountActivity.getIntent(it, id)
            it.startActivityWithSlideInAnimation(intent)
        }
    }

    override fun onMute(mute: Boolean, id: String, position: Int) {
        val callback = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {
                if (response.isSuccessful) {
                    onMuteSuccess(mute, id, position)
                } else {
                    onMuteFailure(mute, id)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onMuteFailure(mute, id)
            }
        }

        val call = if (!mute) {
            api.unmuteAccount(id)
        } else {
            api.muteAccount(id)
        }
        callList.add(call)
        call.enqueue(callback)
    }

    private fun onMuteSuccess(muted: Boolean, id: String, position: Int) {
        if (muted) {
            return
        }
        val mutesAdapter = adapter as MutesAdapter
        val unmutedUser = mutesAdapter.removeItem(position)

        if(unmutedUser != null) {
            Snackbar.make(recyclerView, R.string.confirmation_unmuted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        mutesAdapter.addItem(unmutedUser, position)
                        onMute(true, id, position)
                    }
                    .show()
        }
    }

    private fun onMuteFailure(mute: Boolean, accountId: String) {
        val verb = if (mute) {
            "mute"
        } else {
            "unmute"
        }
        Log.e(TAG, "Failed to $verb account id $accountId")
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        val cb = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {
                if (response.isSuccessful) {
                    onBlockSuccess(block, id, position)
                } else {
                    onBlockFailure(block, id)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onBlockFailure(block, id)
            }
        }

        val call = if (!block) {
            api.unblockAccount(id)
        } else {
            api.blockAccount(id)
        }
        callList.add(call)
        call.enqueue(cb)
    }

    private fun onBlockSuccess(blocked: Boolean, id: String, position: Int) {
        if (blocked) {
            return
        }
        val blocksAdapter = adapter as BlocksAdapter
        val unblockedUser = blocksAdapter.removeItem(position)

        if(unblockedUser != null) {
            Snackbar.make(recyclerView, R.string.confirmation_unblocked, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        blocksAdapter.addItem(unblockedUser, position)
                        onBlock(true, id, position)
                    }
                    .show()
        }
    }

    private fun onBlockFailure(block: Boolean, accountId: String) {
        val verb = if (block) {
            "block"
        } else {
            "unblock"
        }
        Log.e(TAG, "Failed to $verb account accountId $accountId")
    }

    override fun onRespondToFollowRequest(accept: Boolean, accountId: String,
                                          position: Int) {

        val callback = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>, response: Response<Relationship>) {
                if (response.isSuccessful) {
                    onRespondToFollowRequestSuccess(position)
                } else {
                    onRespondToFollowRequestFailure(accept, accountId)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onRespondToFollowRequestFailure(accept, accountId)
            }
        }

        val call = if (accept) {
            api.authorizeFollowRequest(accountId)
        } else {
            api.rejectFollowRequest(accountId)
        }
        callList.add(call)
        call.enqueue(callback)
    }

    private fun onRespondToFollowRequestSuccess(position: Int) {
        val followRequestsAdapter = adapter as FollowRequestsAdapter
        followRequestsAdapter.removeItem(position)
    }

    private fun onRespondToFollowRequestFailure(accept: Boolean, accountId: String) {
        val verb = if (accept) {
            "accept"
        } else {
            "reject"
        }
        Log.e(TAG, "Failed to $verb account id $accountId.")
    }

    private fun getFetchCallByListType(type: Type, fromId: String?): Call<List<Account>> {
        return when (type) {
            Type.FOLLOWS -> api.accountFollowing(id, fromId)
            Type.FOLLOWERS -> api.accountFollowers(id, fromId)
            Type.BLOCKS -> api.blocks(fromId)
            Type.MUTES -> api.mutes(fromId)
            Type.FOLLOW_REQUESTS -> api.followRequests(fromId)
            Type.REBLOGGED -> api.statusRebloggedBy(id, fromId)
            Type.FAVOURITED -> api.statusFavouritedBy(id, fromId)
        }
    }

    private fun fetchAccounts(id: String? = null) {
        if (fetching) {
            return
        }
        fetching = true

        if (id != null) {
            recyclerView.post { adapter.setBottomLoading(true) }
        }

        val cb = object : Callback<List<Account>> {
            override fun onResponse(call: Call<List<Account>>, response: Response<List<Account>>) {
                val accountList = response.body()
                if (response.isSuccessful && accountList != null) {
                    val linkHeader = response.headers().get("Link")
                    onFetchAccountsSuccess(accountList, linkHeader)
                } else {
                    onFetchAccountsFailure(Exception(response.message()))
                }
            }

            override fun onFailure(call: Call<List<Account>>, t: Throwable) {
                onFetchAccountsFailure(t as Exception)
            }
        }
        val listCall = getFetchCallByListType(type, id)
        callList.add(listCall)
        listCall.enqueue(cb)
    }

    private fun onFetchAccountsSuccess(accounts: List<Account>, linkHeader: String?) {
        adapter.setBottomLoading(false)

        val links = HttpHeaderLink.parse(linkHeader)
        val next = HttpHeaderLink.findByRelationType(links, "next")
        val fromId = next?.uri?.getQueryParameter("max_id")

        if (adapter.itemCount > 0) {
            adapter.addItems(accounts)
        } else {
            adapter.update(accounts)
        }

        bottomId = fromId

        fetching = false

    }

    private fun onFetchAccountsFailure(exception: Exception) {
        fetching = false
        Log.e(TAG, "Fetch failure", exception)
    }

    companion object {
        private const val TAG = "AccountList" // logging tag
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"

        fun newInstance(type: Type, id: String? = null): AccountListFragment {
            return AccountListFragment().apply {
                arguments = Bundle(2).apply {
                    putSerializable(ARG_TYPE, type)
                    putString(ARG_ID, id)
                }
            }
        }
    }

}
