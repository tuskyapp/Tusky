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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.keylesspalace.tusky.adapter.LoadStateFooterAdapter
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
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import javax.inject.Inject
import kotlin.getValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    private val viewModel: AccountListViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<AccountListViewModel.Factory> { factory ->
                factory.create(
                    type = requireArguments().getSerializableCompat(ARG_TYPE)!!,
                    accountId = requireArguments().getString(ARG_ID)
                )
            }
        }
    )

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

        binding.recyclerView.adapter = adapter.withLoadStateFooter(LoadStateFooterAdapter(adapter::retry))

        binding.swipeRefreshLayout.setOnRefreshListener { adapter.refresh() }

        lifecycleScope.launch {
            viewModel.domainPager.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            binding.progressBar.visible(
                loadState.refresh == LoadState.Loading && adapter.itemCount == 0
            )

            if (loadState.refresh is LoadState.Error) {
                binding.recyclerView.hide()
                binding.messageView.show()
                val errorState = loadState.refresh as LoadState.Error
                binding.messageView.setup(errorState.error) { adapter.retry() }
                Log.w(TAG, "error loading blocked domains", errorState.error)
            } else if (loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                binding.recyclerView.hide()
                binding.messageView.show()
                binding.messageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty)
            } else {
                binding.recyclerView.show()
                binding.messageView.hide()
            }
        }
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
        //viewModel.onMute(mute, id, position, notifications)
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
        val unblockedUser = null

        if (unblockedUser != null) {
            Snackbar.make(
                binding.recyclerView,
                R.string.confirmation_unblocked,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.action_undo) {
                   // blocksAdapter.addItem(unblockedUser, position)
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
     //   followRequestsAdapter.removeItem(position)
    }



    private fun onFetchAccountsSuccess(
        adapter: AccountAdapter<*>,
        accounts: List<TimelineAccount>,
        linkHeader: String?
    ) {
     //   adapter.setBottomLoading(false)
        binding.swipeRefreshLayout.isRefreshing = false

        val links = HttpHeaderLink.parse(linkHeader)
        val next = HttpHeaderLink.findByRelationType(links, "next")
        val fromId = next?.uri?.getQueryParameter("max_id")

        if (adapter.itemCount > 0) {
      //      adapter.addItems(accounts)
        } else {
      //      adapter.update(accounts)
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
               // this.fetchAccounts(adapter, null)
            }
        }
    }

    companion object {
        private const val TAG = "AccountListFragment"
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
