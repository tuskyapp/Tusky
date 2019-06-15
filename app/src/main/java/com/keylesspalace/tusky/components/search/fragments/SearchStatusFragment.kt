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
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.components.search.SearchType
import com.keylesspalace.tusky.components.search.SearchViewModel
import com.keylesspalace.tusky.components.search.adapter.SearchStatusesAdapter
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AnchorActivity
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.Status
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import kotlinx.android.synthetic.main.fragment_search.*
import javax.inject.Inject

class SearchStatusFragment : SFragment(), StatusActionListener {
    private var snackbarErrorRetry: Snackbar? = null
    private lateinit var statusesAdapter: SearchStatusesAdapter

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
        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)
        val showBotOverlay = preferences.getBoolean("showBotOverlay", true)
        val animateAvatar = preferences.getBoolean("animateGifAvatars", false)

        val account = accountManager.activeAccount
        viewModel.alwaysShowSensitiveMedia = account?.alwaysShowSensitiveMedia ?: false
        val mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true

        searchRecyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(view.context)
        statusesAdapter = SearchStatusesAdapter(useAbsoluteTime, mediaPreviewEnabled, showBotOverlay, animateAvatar, this)
        searchRecyclerView.adapter = statusesAdapter

        subscribeObservables()

    }

    private fun subscribeObservables() {
        viewModel.statuses.observe(viewLifecycleOwner, Observer {
            statusesAdapter.submitList(it)
            showNoData(it.isEmpty(), viewModel.networkStateStatusRefresh.value == NetworkState.LOADED)
        })

        viewModel.networkStateStatusRefresh.observe(viewLifecycleOwner, Observer {
            if (it == NetworkState.LOADING)
                searchProgressBar.show()
            else
                searchProgressBar.hide()

            if (it.status == Status.FAILED)
                showError(it.msg)

            showNoData(statusesAdapter.itemCount == 0, viewModel.networkStateStatusRefresh.value == NetworkState.LOADED)
        })

        viewModel.networkStateStatus.observe(viewLifecycleOwner, Observer {
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

    override fun onViewTag(tag: String) = startActivity(ViewTagActivity.getIntent(requireContext(), tag))

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        //TODO
    }

    override fun onReply(position: Int) {
        statusesAdapter.getItem(position)?.first?.let { status ->
            reply(status)
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        //TODO
    }

    override fun onMore(view: View, position: Int) {
        statusesAdapter.getItem(position)?.first?.let {
            more(it, view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        statusesAdapter.getItem(position)?.first?.actionableStatus?.let { actionable ->
            when (actionable.attachments[attachmentIndex].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments,
                            attachmentIndex)
                    if (view != null) {
                        val url = actionable.attachments[attachmentIndex].url
                        ViewCompat.setTransitionName(view, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                                view, url)
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }
                Attachment.Type.UNKNOWN -> {
                }
            }

        }

    }

    override fun onViewThread(position: Int) {
        statusesAdapter.getItem(position)?.first?.let {
            viewThread(it)
        }
    }

    override fun onOpenReblog(position: Int) {
        statusesAdapter.getItem(position)?.first?.let {
            openReblog(it)
        }
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onLoadMore(position: Int) {
        //Ignore
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeItem(position: Int) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
            return SearchStatusFragment()
                    .apply {
                        arguments = Bundle()
                                .apply {
                                    putSerializable(SEARCH_TYPE, type)
                                }
                    }
        }
    }

}
