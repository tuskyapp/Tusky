package com.keylesspalace.tusky.components.notifications.requests

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.preference.notificationpolicies.NotificationPoliciesActivity
import com.keylesspalace.tusky.databinding.ActivityNotificationRequestsBinding
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationRequestsActivity : BaseActivity() {

    private val viewModel: NotificationRequestsViewModel by viewModels()

    private val binding by viewBinding(ActivityNotificationRequestsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.filtered_notifications_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setupAdapter().let { adapter ->
            setupRecyclerView(adapter)

            lifecycleScope.launch {
                viewModel.pager.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                Snackbar.make(
                    binding.root,
                    error.getErrorString(this@NotificationRequestsActivity),
                    LENGTH_LONG
                ).show()
            }
        }

    }

    private fun setupRecyclerView(adapter: NotificationRequestsAdapter) {
        binding.notificationRequestsView.adapter = adapter
        binding.notificationRequestsView.setHasFixedSize(true)
        binding.notificationRequestsView.layoutManager = LinearLayoutManager(this)
        binding.notificationRequestsView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        (binding.notificationRequestsView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun setupAdapter(): NotificationRequestsAdapter {
        return NotificationRequestsAdapter(
            viewModel::acceptNotificationRequest,
            viewModel::dismissNotificationRequest,
            true,
            true
        ).apply {
            addLoadStateListener { loadState ->
                binding.notificationRequestsProgressBar.visible(
                    loadState.refresh == LoadState.Loading && itemCount == 0
                )

                if (loadState.refresh is LoadState.Error) {
                    binding.notificationRequestsView.hide()
                    binding.notificationRequestsMessageView.show()
                    val errorState = loadState.refresh as LoadState.Error
                    binding.notificationRequestsMessageView.setup(errorState.error) { retry() }
                    Log.w(TAG, "error loading notification requests", errorState.error)
                } else {
                    binding.notificationRequestsView.show()
                    binding.notificationRequestsMessageView.hide()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationRequestsActivity"
        fun newIntent(context: Context) = Intent(context, NotificationRequestsActivity::class.java)
    }
}
