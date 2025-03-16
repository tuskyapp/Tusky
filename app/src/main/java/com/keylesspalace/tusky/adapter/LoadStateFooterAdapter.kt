package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import com.keylesspalace.tusky.databinding.ItemNetworkStateBinding
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.visible

class LoadStateFooterAdapter(
    private val retryCallback: () -> Unit
) : LoadStateAdapter<BindingHolder<ItemNetworkStateBinding>>() {

    override fun onBindViewHolder(
        holder: BindingHolder<ItemNetworkStateBinding>,
        loadState: LoadState
    ) {
        val binding = holder.binding
        binding.progressBar.visible(loadState == LoadState.Loading)
        binding.retryButton.visible(loadState is LoadState.Error)
        val msg = if (loadState is LoadState.Error) {
            loadState.error.message
        } else {
            null
        }
        binding.errorMsg.visible(msg != null)
        binding.errorMsg.text = msg
        binding.retryButton.setOnClickListener {
            retryCallback()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): BindingHolder<ItemNetworkStateBinding> {
        val binding = ItemNetworkStateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingHolder(binding)
    }
}
