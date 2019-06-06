package com.keylesspalace.tusky.components.report.fragments


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
import androidx.paging.PagedList
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
import com.keylesspalace.tusky.components.report.adapter.AdapterClickHandler
import com.keylesspalace.tusky.components.report.adapter.StatusesAdapter
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import kotlinx.android.synthetic.main.fragment_report_statuses.*
import javax.inject.Inject


class ReportStatusesFragment : Fragment(), Injectable, AdapterClickHandler {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var accountManager: AccountManager

    private lateinit var viewModel: ReportViewModel

    private lateinit var adapter: StatusesAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var snackbarErrorRetry: Snackbar? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)[ReportViewModel::class.java]
    }

    override fun showMedia(v: View?, status: Status?, idx: Int) {
        status?.actionableStatus?.let { actionable ->
            when (actionable.attachments[idx].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE -> {
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

        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)

        val account = accountManager.activeAccount
        val mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true


        adapter = StatusesAdapter(useAbsoluteTime, mediaPreviewEnabled, viewModel.selectedIds, viewModel.statusViewState, this)

        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        viewModel.statuses.observe(viewLifecycleOwner, Observer<PagedList<Status>> {
            adapter.submitList(it)
        })

        viewModel.networkStateAfter.observe(viewLifecycleOwner, Observer {
            progressBarBottom.visibility = if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING) View.VISIBLE else View.GONE
            if (it?.status == com.keylesspalace.tusky.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateBefore.observe(viewLifecycleOwner, Observer {
            progressBarTop.visibility = if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING) View.VISIBLE else View.GONE
            if (it?.status == com.keylesspalace.tusky.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateRefresh.observe(viewLifecycleOwner, Observer {
            progressBarLoading.visibility = if (it?.status == com.keylesspalace.tusky.util.Status.RUNNING && !swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
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
            if (viewModel.selectedIds.isEmpty()) {
                Snackbar.make(swipeRefreshLayout, R.string.error_report_too_few_statuses, Snackbar.LENGTH_LONG).show()
            } else {
                viewModel.navigateTo(Screen.Note)
            }
        }
    }
    override fun checkedChanged(status: Status, isChecked: Boolean) {
        viewModel.changedStatusChecked(status,isChecked)
    }

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) = startActivity(ViewTagActivity.getIntent(requireContext(), tag))

    override fun onViewUrl(url: String?) = viewModel.checkClickedUrl(url)

    companion object {
        fun newInstance() = ReportStatusesFragment()
    }

}
