/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread.edits

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.databinding.FragmentViewEditsBinding
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.util.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ViewEditsFragment :
    Fragment(R.layout.fragment_view_edits),
    LinkListener,
    OnRefreshListener,
    MenuProvider {

    @Inject
    lateinit var preferences: SharedPreferences

    private val viewModel: ViewEditsViewModel by viewModels()

    private val binding by viewBinding(FragmentViewEditsBinding::bind)

    private lateinit var statusId: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.swipeRefreshLayout.setOnRefreshListener(this)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        val divider = DividerItemDecoration(context, LinearLayout.VERTICAL)
        binding.recyclerView.addItemDecoration(divider)
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        statusId = requireArguments().getString(STATUS_ID_EXTRA)!!

        val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
        val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true)
        val avatarRadius: Int = requireContext().resources.getDimensionPixelSize(
            R.dimen.avatar_radius_48dp
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    EditsUiState.Initial -> {}
                    EditsUiState.Loading -> {
                        binding.recyclerView.hide()
                        binding.statusView.hide()
                        binding.initialProgressBar.show()
                    }
                    EditsUiState.Refreshing -> {}
                    is EditsUiState.Error -> {
                        Log.w(TAG, "failed to load edits", uiState.throwable)

                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.hide()
                        binding.statusView.show()
                        binding.initialProgressBar.hide()

                        when (uiState.throwable) {
                            is ViewEditsViewModel.MissingEditsException -> {
                                binding.statusView.setup(
                                    R.drawable.elephant_friend_empty,
                                    R.string.error_missing_edits
                                )
                            }
                            else -> {
                                binding.statusView.setup(uiState.throwable) {
                                    viewModel.loadEdits(statusId, force = true)
                                }
                            }
                        }
                    }
                    is EditsUiState.Success -> {
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.show()
                        binding.statusView.hide()
                        binding.initialProgressBar.hide()

                        binding.recyclerView.adapter = ViewEditsAdapter(
                            edits = uiState.edits,
                            animateEmojis = animateEmojis,
                            useBlurhash = useBlurhash,
                            listener = this@ViewEditsFragment
                        )

                        // Focus on the most recent version
                        (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPosition(
                            0
                        )

                        val account = uiState.edits.first().account
                        loadAvatar(
                            account.avatar,
                            binding.statusAvatar,
                            avatarRadius,
                            animateAvatars
                        )

                        binding.statusDisplayName.text = account.name.unicodeWrap().emojify(account.emojis, binding.statusDisplayName, animateEmojis)
                        binding.statusUsername.text = account.username
                    }
                }
            }
        }

        viewModel.loadEdits(statusId)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_view_edits, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                true
            }
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.title_edits)
    }

    override fun onRefresh() {
        viewModel.loadEdits(statusId, force = true, refreshing = true)
    }

    override fun onViewAccount(id: String) {
        bottomSheetActivity?.startActivityWithSlideInAnimation(
            AccountActivity.getIntent(requireContext(), id)
        )
    }

    override fun onViewTag(tag: String) {
        bottomSheetActivity?.startActivityWithSlideInAnimation(
            StatusListActivity.newHashtagIntent(requireContext(), tag)
        )
    }

    override fun onViewUrl(url: String) {
        bottomSheetActivity?.viewUrl(url)
    }

    private val bottomSheetActivity
        get() = (activity as? BottomSheetActivity)

    companion object {
        private const val TAG = "ViewEditsFragment"

        private const val STATUS_ID_EXTRA = "id"

        fun newInstance(statusId: String): ViewEditsFragment {
            val arguments = Bundle(1)
            val fragment = ViewEditsFragment()
            arguments.putString(STATUS_ID_EXTRA, statusId)
            fragment.arguments = arguments
            return fragment
        }
    }
}
