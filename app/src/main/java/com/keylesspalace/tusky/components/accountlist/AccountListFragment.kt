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
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.PostLookupFallbackBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.adapter.LoadStateFooterAdapter
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity.Type
import com.keylesspalace.tusky.components.accountlist.adapter.BlocksAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowRequestsAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.FollowRequestsHeaderAdapter
import com.keylesspalace.tusky.components.accountlist.adapter.MutesAdapter
import com.keylesspalace.tusky.databinding.FragmentAccountListBinding
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.settings.PrefKeys
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountListFragment :
    Fragment(R.layout.fragment_account_list),
    AccountActionListener,
    LinkListener {

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

        binding.recyclerView.adapter = adapter.withLoadStateFooter(LoadStateFooterAdapter(adapter::retry))

        binding.swipeRefreshLayout.setOnRefreshListener { adapter.refresh() }

        lifecycleScope.launch {
            viewModel.accountPager.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                val message = if (event.throwable != null) {
                    getString(event.message, event.user, event.throwable.message ?: getString(R.string.error_generic))
                } else {
                    getString(event.message, event.user)
                }
                Snackbar.make(binding.recyclerView, message, Snackbar.LENGTH_LONG)
                    .setAction(event.actionText, event.action)
                    .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar, eventType: Int) {
                            viewModel.consumeEvent(event)
                        }
                    })
                    .show()
            }
        }

        adapter.addLoadStateListener { loadState ->
            binding.progressBar.visible(
                loadState.refresh == LoadState.Loading && adapter.itemCount == 0
            )

            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            if (loadState.refresh is LoadState.Error) {
                binding.recyclerView.hide()
                binding.messageView.show()
                val errorState = loadState.refresh as LoadState.Error
                binding.messageView.setup(errorState.error) { adapter.retry() }
                Log.w(TAG, "error loading accounts", errorState.error)
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
        if (mute) {
            viewModel.mute(id, notifications)
        } else {
            viewModel.unmute(id)
        }
    }

    override fun onBlock(block: Boolean, id: String, position: Int) {
        viewModel.unblock(id)
    }

    override fun onRespondToFollowRequest(accept: Boolean, id: String, position: Int) {
        viewModel.respondToFollowRequest(accept, id)
    }

    companion object {
        private const val TAG = "AccountListFragment"
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"

        fun newInstance(type: Type, id: String? = null): AccountListFragment = AccountListFragment().apply {
            arguments = Bundle(2).apply {
                putSerializable(ARG_TYPE, type)
                putString(ARG_ID, id)
            }
        }
    }
}
