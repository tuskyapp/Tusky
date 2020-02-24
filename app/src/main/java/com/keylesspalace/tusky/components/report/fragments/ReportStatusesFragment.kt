/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.AccountActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.ViewTagActivity
import com.keylesspalace.tusky.components.report.ReportViewModel
import com.keylesspalace.tusky.components.report.Screen
import com.keylesspalace.tusky.components.report.adapter.AdapterHandler
import com.keylesspalace.tusky.components.report.adapter.StatusesAdapter
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import kotlinx.android.synthetic.main.fragment_report_statuses.*
import javax.inject.Inject

class ReportStatusesFragment : Fragment(), Injectable, AdapterHandler {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var accountManager: AccountManager

    private val viewModel: ReportViewModel by viewModels({ requireActivity() }) { viewModelFactory }

    private lateinit var adapter: StatusesAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var snackbarErrorRetry: Snackbar? = null

    override fun showMedia(v: View?, status: Status?, idx: Int) {
        status?.actionableStatus?.let { actionable ->
            when (actionable.attachments[idx].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments,
                            idx)
                    if (v != null) {
                        val url = actionable.attachments[idx].url
                        ViewCompat.setTransitionName(v, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                                v, url)
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_statuses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handleClicks()
        initStatusesView()
        setupSwipeRefreshLayout()
    }

    private fun setupSwipeRefreshLayout() {
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(swipeRefreshLayout.context, android.R.attr.colorBackground))

        swipeRefreshLayout.setOnRefreshListener {
            snackbarErrorRetry?.dismiss()
            viewModel.refreshStatuses()
        }
    }

    private fun initStatusesView() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val statusDisplayOptions = StatusDisplayOptions(
                animateAvatars = false,
                mediaPreviewEnabled = accountManager.activeAccount?.mediaPreviewEnabled ?: true,
                useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false),
                showBotOverlay = false,
                useBlurhash = preferences.getBoolean("useBlurhash", true)
        )

        adapter = StatusesAdapter(statusDisplayOptions,
                viewModel.statusViewState, this)

        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        viewModel.statuses.observe(viewLifecycleOwner, Observer<PagedList<Status>> {
            adapter.submitList(it)
        })

        viewModel.networkStateAfter.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING)
                progressBarBottom.show()
            else
                progressBarBottom.hide()

            if (it?.status == com.keylesspalace.tusky.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateBefore.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING)
                progressBarTop.show()
            else
                progressBarTop.hide()

            if (it?.status == com.keylesspalace.tusky.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateRefresh.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING && !swipeRefreshLayout.isRefreshing)
                progressBarLoading.show()
            else
                progressBarLoading.hide()

            if (it?.status != com.keylesspalace.tusky.util.Status.RUNNING)
                swipeRefreshLayout.isRefreshing = false
            if (it?.status == com.keylesspalace.tusky.util.Status.FAILED)
                showError(it.msg)
        })
    }

    private fun showError(@Suppress("UNUSED_PARAMETER") msg: String?) {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make(swipeRefreshLayout, R.string.failed_fetch_statuses, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(R.string.action_retry) {
                viewModel.retryStatusLoad()
            }
            snackbarErrorRetry?.show()
        }
    }


    private fun handleClicks() {
        buttonCancel.setOnClickListener {
            viewModel.navigateTo(Screen.Back)
        }

        buttonContinue.setOnClickListener {
            viewModel.navigateTo(Screen.Note)
        }
    }

    override fun setStatusChecked(status: Status, isChecked: Boolean) {
        viewModel.setStatusChecked(status, isChecked)
    }

    override fun isStatusChecked(id: String): Boolean {
        return viewModel.isStatusChecked(id)
    }

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) = startActivity(ViewTagActivity.getIntent(requireContext(), tag))

    override fun onViewUrl(url: String?) = viewModel.checkClickedUrl(url)

    companion object {
        fun newInstance() = ReportStatusesFragment()
    }
}
