package com.keylesspalace.tusky.components.search.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.search.SearchViewModel
import com.keylesspalace.tusky.databinding.FragmentSearchBinding
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class SearchFragment<T : Any> :
    Fragment(R.layout.fragment_search),
    LinkListener,
    SwipeRefreshLayout.OnRefreshListener,
    MenuProvider {

    @Inject
    lateinit var mastodonApi: MastodonApi

    protected val viewModel: SearchViewModel by activityViewModels()

    protected val binding by viewBinding(FragmentSearchBinding::bind)

    private var snackbarErrorRetry: Snackbar? = null

    abstract fun createAdapter(): PagingDataAdapter<T, *>

    abstract val data: Flow<PagingData<T>>
    protected var adapter: PagingDataAdapter<T, *>? = null

    private var currentQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = initAdapter()
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.searchRecyclerView.ensureBottomPadding()
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        subscribeObservables(adapter)
    }

    override fun onDestroyView() {
        // Clear the adapter to prevent leaking the View
        adapter = null
        snackbarErrorRetry = null
        super.onDestroyView()
    }

    private fun subscribeObservables(adapter: PagingDataAdapter<T, *>) {
        viewLifecycleOwner.lifecycleScope.launch {
            data.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->

            if (loadState.refresh is LoadState.Error) {
                showError(adapter)
            }

            val isNewSearch = currentQuery != viewModel.currentQuery

            binding.searchProgressBar.visible(
                loadState.refresh == LoadState.Loading && isNewSearch && !binding.swipeRefreshLayout.isRefreshing
            )
            binding.searchRecyclerView.visible(
                loadState.refresh is LoadState.NotLoading || !isNewSearch || binding.swipeRefreshLayout.isRefreshing
            )

            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
                currentQuery = viewModel.currentQuery
            }

            binding.progressBarBottom.visible(loadState.append == LoadState.Loading)

            binding.searchNoResultsText.visible(
                loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0 && viewModel.currentQuery.isNotEmpty()
            )
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_search, menu)
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

    private fun initAdapter(): PagingDataAdapter<T, *> {
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(binding.searchRecyclerView.context)
        val adapter = createAdapter()
        this.adapter = adapter
        binding.searchRecyclerView.adapter = adapter
        binding.searchRecyclerView.setHasFixedSize(true)
        (binding.searchRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        return adapter
    }

    private fun showError(adapter: PagingDataAdapter<T, *>) {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry =
                Snackbar.make(binding.root, R.string.failed_search, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_retry) {
                        snackbarErrorRetry = null
                        adapter.retry()
                    }.also {
                        it.show()
                    }
        }
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

    protected val bottomSheetActivity
        get() = (activity as? BottomSheetActivity)

    override fun onRefresh() {
        snackbarErrorRetry?.dismiss()
        snackbarErrorRetry = null
        adapter?.refresh()
    }
}
