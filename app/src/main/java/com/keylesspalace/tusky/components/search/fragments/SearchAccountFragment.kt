/* Copyright 2018 Conny Duck
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.components.search.SearchViewModel
import com.keylesspalace.tusky.components.search.adapter.SearchAccountsAdapter
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.interfaces.AnchorActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.Status
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import kotlinx.android.synthetic.main.fragment_search.*
import javax.inject.Inject

class SearchAccountFragment : Fragment(), Injectable, LinkListener {
    private var snackbarErrorRetry: Snackbar? = null
    private lateinit var accountsAdapter: SearchAccountsAdapter

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: SearchViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)[SearchViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        searchRecyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(view.context)
        accountsAdapter = SearchAccountsAdapter(this)
        searchRecyclerView.adapter = accountsAdapter

        subscribeObservables()

    }

    private fun subscribeObservables() {
        viewModel.accounts.observe(viewLifecycleOwner, Observer {
            accountsAdapter.submitList(it)
            showNoData(it.isEmpty(), viewModel.networkStateAccountRefresh.value == NetworkState.LOADED)
        })

        viewModel.networkStateAccountRefresh.observe(viewLifecycleOwner, Observer {
            if (it == NetworkState.LOADING)
                searchProgressBar.show()
            else
                searchProgressBar.hide()

            if (it.status == Status.FAILED)
                showError(it.msg)

            showNoData(accountsAdapter.itemCount == 0, viewModel.networkStateAccountRefresh.value == NetworkState.LOADED)
        })

        viewModel.networkStateAccount.observe(viewLifecycleOwner, Observer {
            if (it == NetworkState.LOADING)
                progressBarBottom.show()
            else
                progressBarBottom.hide()

            if (it.status == Status.FAILED)
                showError(it.msg)
        })

    }

    private fun showNoData(isEmpty: Boolean, isLoaded: Boolean) {
        if (isEmpty && isLoaded)
            searchNoResultsText.show()
        else
            searchNoResultsText.hide()
    }

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) {
        //Ignore
    }

    override fun onViewUrl(url: String?) {
        //Ignore
    }


    private fun showError(@Suppress("UNUSED_PARAMETER") msg: String?) {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make((activity as? AnchorActivity)?.getAnchor()
                    ?: layoutRoot, R.string.failed_search, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(R.string.action_retry) {
                viewModel.retryStatusSearch()
            }
            snackbarErrorRetry?.show()
        }
    }

    companion object {
        const val TAG = "SearchStatusFragment"
        private const val SEARCH_TYPE = "search.type"
        fun newInstance(type: SearchType): Fragment {
            return SearchAccountFragment()
                    .apply {
                        arguments = Bundle()
                                .apply {
                                    putSerializable(SEARCH_TYPE, type)
                                }
                    }
        }
    }

}
