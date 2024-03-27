package com.keylesspalace.tusky.components.domainblocks

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentDomainBlocksBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.observe
import com.keylesspalace.tusky.util.observeLatest
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import javax.inject.Inject

class DomainBlocksFragment : Fragment(R.layout.fragment_domain_blocks), Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val binding by viewBinding(FragmentDomainBlocksBinding::bind)

    private val viewModel: DomainBlocksViewModel by viewModels { viewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DomainBlocksAdapter(viewModel::unblock)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(view.context, DividerItemDecoration.VERTICAL)
        )
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(view.context)

        viewModel.uiEvents.observe(viewLifecycleOwner) { event ->
            showSnackbar(event)
        }

        viewModel.domainPager.observeLatest { pagingData ->
            adapter.submitData(pagingData)
        }

        adapter.addLoadStateListener { loadState ->
            binding.progressBar.visible(
                loadState.refresh == LoadState.Loading && adapter.itemCount == 0
            )

            if (loadState.refresh is LoadState.Error) {
                binding.recyclerView.hide()
                binding.messageView.show()
                val errorState = loadState.refresh as LoadState.Error
                binding.messageView.setup(errorState.error) { adapter.retry() }
                Log.w(TAG, "error loading blocked domains", errorState.error)
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

    private fun showSnackbar(event: SnackbarEvent) {
        val message = if (event.throwable == null) {
            getString(event.message, event.domain)
        } else {
            Log.w(TAG, event.throwable)
            val error = event.throwable.localizedMessage ?: getString(R.string.ui_error_unknown)
            getString(event.message, event.domain, error)
        }

        Snackbar.make(binding.recyclerView, message, Snackbar.LENGTH_LONG)
            .setTextMaxLines(5)
            .setAction(event.actionText, event.action)
            .show()
    }

    companion object {
        private const val TAG = "DomainBlocksFragment"
    }
}
