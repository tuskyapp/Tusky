package com.keylesspalace.tusky.components.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemNotificationsLoadStateFooterViewBinding
import java.net.SocketTimeoutException

/**
 * Display the header/footer loading state to the user.
 *
 * Either:
 *
 * 1. A page is being loaded, display a progress view, or
 * 2. An error occurred, display an error message with a "retry" button
 *
 * @param retry function to invoke if the user clicks the "retry" button
 */
class NotificationsLoadStateViewHolder(
    private val binding: ItemNotificationsLoadStateFooterViewBinding,
    retry: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    init {
        binding.retryButton.setOnClickListener { retry.invoke() }
    }

    fun bind(loadState: LoadState) {
        if (loadState is LoadState.Error) {
            val ctx = binding.root.context
            binding.errorMsg.text = when (loadState.error) {
                is SocketTimeoutException -> ctx.getString(R.string.socket_timeout_exception)
                // Other exceptions to consider:
                // - UnknownHostException, default text is:
                //   Unable to resolve "%s": No address associated with hostname
                else -> loadState.error.localizedMessage
            }
        }
        binding.progressBar.isVisible = loadState is LoadState.Loading
        binding.retryButton.isVisible = loadState is LoadState.Error
        binding.errorMsg.isVisible = loadState is LoadState.Error
    }

    companion object {
        fun create(parent: ViewGroup, retry: () -> Unit): NotificationsLoadStateViewHolder {
            val binding = ItemNotificationsLoadStateFooterViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return NotificationsLoadStateViewHolder(binding, retry)
        }
    }
}
