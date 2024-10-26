package com.keylesspalace.tusky.components.preference.notificationpolicies

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_LONG
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.preference.notificationpolicies.NotificationPoliciesViewModel.State
import com.keylesspalace.tusky.databinding.ActivityNotificationPolicyBinding
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationPoliciesActivity : BaseActivity() {

    private val viewModel: NotificationPoliciesViewModel by viewModels()

    private val binding by viewBinding(ActivityNotificationPolicyBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.notification_policies_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.progressBar.visible(state is State.Loading)
                binding.preferenceFragment.visible(state is State.Loaded)
                binding.messageView.visible(state !is State.Loading && state !is State.Loaded)
                when (state) {
                    is State.Loading -> {}
                    State.GenericError ->
                        binding.messageView.setup(R.drawable.errorphant_error, R.string.error_generic) { viewModel.loadPolicy() }

                    is State.Loaded -> { }
                    State.NetworkError ->
                        binding.messageView.setup(R.drawable.errorphant_offline, R.string.error_network) { viewModel.loadPolicy() }

                    State.Unsupported ->
                        binding.messageView.setup(R.drawable.errorphant_error, R.string.notification_policies_not_supported) { viewModel.loadPolicy() }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                Snackbar.make(
                    binding.root,
                    error.getErrorString(this@NotificationPoliciesActivity),
                    LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, NotificationPoliciesActivity::class.java)
    }
}
