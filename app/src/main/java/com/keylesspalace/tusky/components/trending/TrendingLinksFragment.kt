/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.trending

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.trending.viewmodel.InfallibleUiAction
import com.keylesspalace.tusky.components.trending.viewmodel.LoadState
import com.keylesspalace.tusky.components.trending.viewmodel.TrendingLinksViewModel
import com.keylesspalace.tusky.databinding.FragmentTrendingLinksBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.RefreshableFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

class TrendingLinksFragment :
    Fragment(R.layout.fragment_trending_links),
    OnRefreshListener,
    Injectable,
    ReselectableFragment,
    RefreshableFragment,
    MenuProvider {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: TrendingLinksViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(FragmentTrendingLinksBinding::bind)

    private lateinit var adapter: TrendingLinksAdapter

    private var talkBackWasEnabled = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.recyclerView.layoutManager = getLayoutManager(
            requireContext().resources.getInteger(R.integer.trending_column_count)
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        adapter = TrendingLinksAdapter(viewModel.statusDisplayOptions.value, ::onOpenLink)

        setupSwipeRefreshLayout()
        setupRecyclerView()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadState.collectLatest {
                when (it) {
                    LoadState.Initial -> {
                        viewModel.accept(InfallibleUiAction.Reload)
                    }
                    LoadState.Loading -> {
                        if (!binding.swipeRefreshLayout.isRefreshing) {
                            binding.progressBar.show()
                        } else {
                            binding.progressBar.hide()
                        }
                    }
                    is LoadState.Success -> {
                        adapter.submitList(it.data)
                        binding.progressBar.hide()
                        binding.swipeRefreshLayout.isRefreshing = false
                        if (it.data.isEmpty()) {
                            binding.messageView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty,
                                null
                            )
                            binding.messageView.show()
                        } else {
                            binding.messageView.hide()
                            binding.recyclerView.show()
                        }
                    }
                    is LoadState.Error -> {
                        binding.progressBar.hide()
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.recyclerView.hide()
                        if (adapter.itemCount != 0) {
                            val snackbar = Snackbar.make(
                                binding.root,
                                it.throwable.message ?: "Error",
                                Snackbar.LENGTH_INDEFINITE
                            )

                            if (it.throwable !is HttpException || it.throwable.code() != 404) {
                                snackbar.setAction("Retry") { viewModel.accept(InfallibleUiAction.Reload) }
                            }
                            snackbar.show()
                        } else {
                            if (it.throwable !is HttpException || it.throwable.code() != 404) {
                                binding.messageView.setup(it.throwable) {
                                    viewModel.accept(InfallibleUiAction.Reload)
                                }
                            } else {
                                binding.messageView.setup(it.throwable)
                            }
                            binding.messageView.show()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusDisplayOptions.collectLatest {
                adapter.statusDisplayOptions = it
            }
        }

        (activity as? ActionButtonActivity)?.actionButton?.hide()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = getLayoutManager(requireContext().resources.getInteger(R.integer.trending_column_count))
        binding.recyclerView.setHasFixedSize(true)
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.adapter = adapter
    }

    private fun getLayoutManager(columnCount: Int) = GridLayoutManager(context, columnCount)

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_trending_links, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt =
                    MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                refreshContent()
                true
            }
            else -> false
        }
    }

    override fun refreshContent() {
        binding.swipeRefreshLayout.isRefreshing = true
        onRefresh()
    }

    override fun onRefresh() = viewModel.accept(InfallibleUiAction.Reload)

    override fun onReselect() {
        if (isAdded) {
            binding.recyclerView.layoutManager?.scrollToPosition(0)
            binding.recyclerView.stopScroll()
        }
    }

    private fun onOpenLink(url: String) = requireContext().openLink(url)

    override fun onResume() {
        super.onResume()
        val a11yManager =
            ContextCompat.getSystemService(requireContext(), AccessibilityManager::class.java)

        val wasEnabled = talkBackWasEnabled
        talkBackWasEnabled = a11yManager?.isEnabled == true
        Log.d(TAG, "talkback was enabled: $wasEnabled, now $talkBackWasEnabled")
        if (talkBackWasEnabled && !wasEnabled) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    companion object {
        private const val TAG = "TrendingLinksFragment"

        fun newInstance() = TrendingLinksFragment()
    }
}
