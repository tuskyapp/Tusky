package com.keylesspalace.tusky.components.search.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
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
import com.keylesspalace.tusky.util.*
import javax.inject.Inject

abstract class SearchFragment<T> : Fragment(R.layout.fragment_search),
        LinkListener, Injectable, SwipeRefreshLayout.OnRefreshListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    protected val viewModel: SearchViewModel by activityViewModels { viewModelFactory }

    protected val binding by viewBinding(FragmentSearchBinding::bind)

    private var snackbarErrorRetry: Snackbar? = null

    abstract fun createAdapter(): PagedListAdapter<T, *>

    abstract val networkStateRefresh: LiveData<NetworkState>
    abstract val networkState: LiveData<NetworkState>
    abstract val data: LiveData<PagedList<T>>
    protected lateinit var adapter: PagedListAdapter<T, *>

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
        data.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        networkStateRefresh.observe(viewLifecycleOwner) {

            binding.searchProgressBar.visible(it == NetworkState.LOADING)

            if (it.status == Status.FAILED) {
                showError()
            }
            checkNoData()
        }

        networkState.observe(viewLifecycleOwner) {

            binding.progressBarBottom.visible(it == NetworkState.LOADING)

            if (it.status == Status.FAILED) {
                showError()
            }
        }
    }

    private fun checkNoData() {
        showNoData(adapter.itemCount == 0)
    }

    private fun initAdapter() {
        binding.searchRecyclerView.addItemDecoration(DividerItemDecoration(binding.searchRecyclerView.context, DividerItemDecoration.VERTICAL))
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(binding.searchRecyclerView.context)
        adapter = createAdapter()
        binding.searchRecyclerView.adapter = adapter
        binding.searchRecyclerView.setHasFixedSize(true)
        (binding.searchRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun showNoData(isEmpty: Boolean) {
        if (isEmpty && networkStateRefresh.value == NetworkState.LOADED) {
            binding.searchNoResultsText.show()
        } else {
            binding.searchNoResultsText.hide()
        }
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
