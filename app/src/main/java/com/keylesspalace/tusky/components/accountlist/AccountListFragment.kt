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

package com.keylesspalace.tusky.components.accountlist

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.PostLookupFallbackBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Type
import com.keylesspalace.tusky.components.accountlist.adapter.AccountAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.BlocksAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowRequestsAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowRequestsHeaderAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.MutesAdapter
import com.keylesspalace.tusky.databinding.FragmentAccountListBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.HttpHeaderLink
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.getSerializableCompat
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.view.EndlessOnScrollListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Response

@AndroidEntryPoint
class AccountListFragment :
    Fragment(R.layout.fragment_account_list),
    AccountActionListener,
    LinkListener {

    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var preferences: SharedPreferences

    private val binding by viewBinding(FragmentAccountListBinding::bind)

    private lateinit var type: Type
    private var id: String? = null

    private var adapter: AccountAdapter<*>? = null
    private var fetching = false
    private var bottomId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getSerializableCompat(ARG_TYPE)!!
        id = requireArguments().getString(ARG_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.ensureBottomPadding()
        binding.recyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(view.context)
        binding.recyclerView.layoutManager = layoutManager
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL)
        )

        val animateAvatar = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
        val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true)

        val activeAccount = accountManager.activeAccount!!

        val adapter = when (type) {
            Type.BLOCKS -> BlocksAdapter(this, animateAvatar, animateEmojis, showBotOverlay)
            Type.MUTES -> MutesAdapter(this, animateAvatar, animateEmojis, showBotOverlay)
            Type.FOLLOW_REQUESTS -> {
                val headerAdapter = FollowRequestsHeaderAdapter(
                    instanceName = activeAccount.domain,
                    accountLocked = activeAccount.locked
                )
                val followRequestsAdapter =
                    FollowRequestsAdapter(this, this, animateAvatar, animateEmojis, showBotOverlay)
                binding.recyclerView.adapter = ConcatAdapter(headerAdapter, followRequestsAdapter)
                followRequestsAdapter
            }
            else -> FollowAdapter(this, animateAvatar, animateEmojis, showBotOverlay)
        }
        this.adapter = adapter
        if (binding.recyclerView.adapter == null) {
            binding.recyclerView.adapter = adapter
        }

        val scrollListener = object : EndlessOnScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                if (bottomId == null) {
                    return
                }
                fetchAccounts(adapter, bottomId)
            }
        }

        binding.recyclerView.addOnScrollListener(scrollListener)

        binding.swipeRefreshLayout.setOnRefreshListener { fetchAccounts(adapter) }

        fetchAccounts(adapter)
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        super.onDestroyView()
    }

    override fun onViewTag(tag: String) {
        activity?.startActivityWithSlideInAnimation(
            StatusListActivity.newHashtagIntent(requireContext(), tag)
        )
    }

    override fun onViewAccount(id: String) {
        activity?.startActivityWithSlideInAnimation(AccountActivity.getIntent(requireContext(), id))
    }

    override fun onViewUrl(url: String) {
        (activity as BottomSheetActivity?)?.viewUrl(url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
    }

    override fun onMute(mute: Boolean, id: String, position: Int, notifications: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!mute) {
                    api.unmuteAccount(id)
                } else {
                    api.muteAccount(id, notifications)
                }
                onMuteSuccess(mute, id, position, notifications)
            } catch (_: Throwable) {
                onMuteFailure(mute, id, notifications)
            }
        }
    }

    private fun onMuteSuccess(muted: Boolean, id: String, position: Int, notifications: Boolean) {
        val mutesAdapter = adapter as MutesAdapter
        if (muted) {
            mutesAdapter.updateMutingNotifications(id, notifications, position)
            return
        }
        val unmutedUser = mutesAdapter.removeItem(position)

        if (unmutedUser != null) {
            Snackbar.make(binding.recyclerView, R.string.confirmation_unmuted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo) {
                    mutesAdapter.addItem(unmutedUser, position)
                    onMute(true, id, position, notifications)
                }
                .show()
        }
    }

    private fun onMuteFailure(mute: Boolean, accountId: String, notifications: Boolean) {
        val verb = if (mute) {
            if (notifications) {
                "mute (notifications = true)"
            } else {
                "mute (notifications = false)"
            }
        } else {
            "unmute"
        }
        Log.e(TAG, "Failed to $verb account id $accountId")
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!block) {
                    api.unblockAccount(id)
                } else {
                    api.blockAccount(id)
                }
                onBlockSuccess(block, id, position)
            } catch (_: Throwable) {
                onBlockFailure(block, id)
            }
        }
    }

    private fun onBlockSuccess(blocked: Boolean, id: String, position: Int) {
        if (blocked) {
            return
        }
        val blocksAdapter = adapter as BlocksAdapter
        val unblockedUser = blocksAdapter.removeItem(position)

        if (unblockedUser != null) {
            Snackbar.make(
                binding.recyclerView,
                R.string.confirmation_unblocked,
                Snackbar.LENGTH_LONG
            )
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

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (accept) {
                api.authorizeFollowRequest(id)
            } else {
                api.rejectFollowRequest(id)
            }.fold(
                onSuccess = {
                    onRespondToFollowRequestSuccess(position)
                },
                onFailure = { throwable ->
                    val verb = if (accept) {
                        "accept"
                    } else {
                        "reject"
                    }
                    Log.e(TAG, "Failed to $verb account id $id.", throwable)
                }
            )
        }
    }

    private fun onRespondToFollowRequestSuccess(position: Int) {
        val followRequestsAdapter = adapter as FollowRequestsAdapter
        followRequestsAdapter.removeItem(position)
    }

    private suspend fun getFetchCallByListType(fromId: String?): Response<List<TimelineAccount>> {
        return when (type) {
            Type.FOLLOWS -> {
                val accountId = requireId(type, id)
                api.accountFollowing(accountId, fromId)
            }
            Type.FOLLOWERS -> {
                val accountId = requireId(type, id)
                api.accountFollowers(accountId, fromId)
            }
            Type.BLOCKS -> api.blocks(fromId)
            Type.MUTES -> api.mutes(fromId)
            Type.FOLLOW_REQUESTS -> api.followRequests(fromId)
            Type.REBLOGGED -> {
                val statusId = requireId(type, id)
                api.statusRebloggedBy(statusId, fromId)
            }
            Type.FAVOURITED -> {
                val statusId = requireId(type, id)
                api.statusFavouritedBy(statusId, fromId)
            }
        }
    }

    private fun requireId(type: Type, id: String?): String {
        return requireNotNull(id) { "id must not be null for type " + type.name }
    }

    private fun fetchAccounts(adapter: AccountAdapter<*>, fromId: String? = null) {
        if (fetching) {
            return
        }
        fetching = true
        binding.swipeRefreshLayout.isRefreshing = true

        if (fromId != null) {
            binding.recyclerView.post { adapter.setBottomLoading(true) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = getFetchCallByListType(fromId)

                if (!response.isSuccessful) {
                    onFetchAccountsFailure(adapter, Exception(response.message()))
                    return@launch
                }

                val accountList = response.body()

                if (accountList == null) {
                    onFetchAccountsFailure(adapter, Exception(response.message()))
                    return@launch
                }

                val linkHeader = response.headers()["Link"]
                onFetchAccountsSuccess(adapter, accountList, linkHeader)
            } catch (exception: Exception) {
                if (exception is CancellationException) {
                    // Scope is cancelled, probably because the fragment is destroyed.
                    // We must not touch any views anymore, so rethrow the exception.
                    // (CancellationException in a cancelled scope is normal and will be ignored)
                    throw exception
                }
                onFetchAccountsFailure(adapter, exception)
            }
        }
    }

    private fun onFetchAccountsSuccess(
        adapter: AccountAdapter<*>,
        accounts: List<TimelineAccount>,
        linkHeader: String?
    ) {
        adapter.setBottomLoading(false)
        binding.swipeRefreshLayout.isRefreshing = false

        val links = HttpHeaderLink.parse(linkHeader)
        val next = HttpHeaderLink.findByRelationType(links, "next")
        val fromId = next?.uri?.getQueryParameter("max_id")

        if (adapter.itemCount > 0) {
            adapter.addItems(accounts)
        } else {
            adapter.update(accounts)
        }

        if (adapter is MutesAdapter) {
            fetchRelationships(adapter, accounts.map { it.id })
        }

        bottomId = fromId

        fetching = false

        if (adapter.itemCount == 0) {
            binding.messageView.show()
            binding.messageView.setup(
                R.drawable.elephant_friend_empty,
                R.string.message_empty,
                null
            )
        } else {
            binding.messageView.hide()
        }
    }

    private fun fetchRelationships(mutesAdapter: MutesAdapter, ids: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            api.relationships(ids)
                .fold(
                    onSuccess = { relationships ->
                        onFetchRelationshipsSuccess(mutesAdapter, relationships)
                    },
                    onFailure = { throwable ->
                        Log.e(TAG, "Fetch failure for relationships of accounts: $ids", throwable)
                    }
                )
        }
    }

    private fun onFetchRelationshipsSuccess(
        mutesAdapter: MutesAdapter,
        relationships: List<Relationship>
    ) {
        val mutingNotificationsMap = HashMap<String, Boolean>()
        relationships.map { mutingNotificationsMap.put(it.id, it.mutingNotifications) }
        mutesAdapter.updateMutingNotificationsMap(mutingNotificationsMap)
    }

    private fun onFetchAccountsFailure(adapter: AccountAdapter<*>, throwable: Throwable) {
        fetching = false
        binding.swipeRefreshLayout.isRefreshing = false
        Log.e(TAG, "Fetch failure", throwable)

        if (adapter.itemCount == 0) {
            binding.messageView.show()
            binding.messageView.setup(throwable) {
                binding.messageView.hide()
                this.fetchAccounts(adapter, null)
            }
        }
    }

    companion object {
        private const val TAG = "AccountList" // logging tag
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"

        fun newInstance(type: Type, id: String? = null): AccountListFragment {
            return AccountListFragment().apply {
                arguments = Bundle(3).apply {
                    putSerializable(ARG_TYPE, type)
                    putString(ARG_ID, id)
                }
            }
        }
    }
}
