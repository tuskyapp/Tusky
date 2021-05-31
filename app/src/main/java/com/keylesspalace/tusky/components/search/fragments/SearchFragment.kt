/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.search.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.components.search.SearchViewModel
import com.keylesspalace.tusky.databinding.FragmentSearchBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class SearchFragment<T: Any> : Fragment(R.layout.fragment_search),
        LinkListener, Injectable, SwipeRefreshLayout.OnRefreshListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    protected val viewModel: SearchViewModel by activityViewModels { viewModelFactory }

    protected val binding by viewBinding(FragmentSearchBinding::bind)

    private var snackbarErrorRetry: Snackbar? = null

    abstract fun createAdapter(): PagingDataAdapter<T, *>

    abstract val data: Flow<PagingData<T>>
    protected lateinit var adapter: PagingDataAdapter<T, *>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initAdapter()
        setupSwipeRefreshLayout()
        subscribeObservables()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun subscribeObservables() {
        viewLifecycleOwner.lifecycleScope.launch {
            data.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->

            if (loadState.refresh is LoadState.Error) {
                showError()
            }

            binding.searchProgressBar.visible(loadState.refresh == LoadState.Loading)

            if (loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                binding.searchNoResultsText.show()
            } else {
                binding.searchNoResultsText.hide()
            }
        }
    }

    private fun initAdapter() {
        binding.searchRecyclerView.addItemDecoration(DividerItemDecoration(binding.searchRecyclerView.context, DividerItemDecoration.VERTICAL))
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(binding.searchRecyclerView.context)
        adapter = createAdapter()
        binding.searchRecyclerView.adapter = adapter
        binding.searchRecyclerView.setHasFixedSize(true)
        (binding.searchRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun showError() {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make(binding.root, R.string.failed_search, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(R.string.action_retry) {
                snackbarErrorRetry = null
                viewModel.retryAllSearches()
            }
            snackbarErrorRetry?.show()
        }
    }

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) = startActivity(ViewTagActivity.getIntent(requireContext(), tag))

    override fun onViewUrl(url: String) {
        bottomSheetActivity?.viewUrl(url)
    }

    protected val bottomSheetActivity
        get() = (activity as? BottomSheetActivity)

    override fun onRefresh() {

        // Dismissed here because the RecyclerView bottomProgressBar is shown as soon as the retry begins.
        binding.swipeRefreshLayout.post {
            binding.swipeRefreshLayout.isRefreshing = false
        }
        viewModel.retryAllSearches()
    }
}
