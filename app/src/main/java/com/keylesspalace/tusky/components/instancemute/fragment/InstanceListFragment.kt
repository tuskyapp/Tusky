package com.keylesspalace.tusky.components.instancemute.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.followedtags.FollowedTagsActivity
import com.keylesspalace.tusky.components.instancemute.InstanceMuteEvent
import com.keylesspalace.tusky.components.instancemute.InstanceMuteViewModel
import com.keylesspalace.tusky.components.instancemute.adapter.DomainMutesAdapter
import com.keylesspalace.tusky.databinding.FragmentInstanceListBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

class InstanceListFragment : Fragment(R.layout.fragment_instance_list), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val binding by viewBinding(FragmentInstanceListBinding::bind)

    private val viewModel: InstanceMuteViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DomainMutesAdapter(viewModel::unmute)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(view.context)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is InstanceMuteEvent.UnmuteError -> showUnmuteError(event.domain)
                    is InstanceMuteEvent.MuteError -> showMuteError(event.domain)
                    is InstanceMuteEvent.UnmuteSuccess -> showUnmuteSuccess(event.domain)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pager.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            binding.instanceProgressBar.visible(loadState.refresh == LoadState.Loading && adapter.itemCount == 0)

            if (loadState.refresh is LoadState.Error) {
                binding.recyclerView.hide()
                binding.messageView.show()
                val errorState = loadState.refresh as LoadState.Error
                binding.messageView.setup(errorState.error) { adapter.retry() }
                Log.w(FollowedTagsActivity.TAG, "error loading followed hashtags", errorState.error)
            } else if (loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0) {
                binding.recyclerView.hide()
                binding.messageView.show()
                binding.messageView.setup(R.drawable.elephant_friend_empty, R.string.message_empty)
            } else {
                binding.recyclerView.show()
                binding.messageView.hide()
            }
        }
    }

    private fun showUnmuteError(domain: String) {
        showSnackbar(
            getString(R.string.error_unmuting_domain, domain),
            R.string.action_retry
        ) { viewModel.unmute(domain) }
    }

    private fun showMuteError(domain: String) {
        showSnackbar(
            getString(R.string.error_muting_domain, domain),
            R.string.action_retry
        ) { viewModel.mute(domain) }
    }

    private fun showUnmuteSuccess(domain: String) {
        showSnackbar(
            getString(R.string.confirmation_domain_unmuted, domain),
            R.string.action_undo
        ) { viewModel.mute(domain) }
    }

    private fun showSnackbar(message: String, actionText: Int, action: (View) -> Unit) {
        Snackbar.make(binding.recyclerView, message, Snackbar.LENGTH_LONG)
            .setAction(actionText, action)
            .show()
    }
}
