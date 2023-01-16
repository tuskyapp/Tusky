package com.keylesspalace.tusky.components.notifications

import android.view.ViewGroup
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter

class NotificationsLoadStateAdapter(
    private val retry: () -> Unit
) : LoadStateAdapter<NotificationsLoadStateViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        loadState: LoadState
    ): NotificationsLoadStateViewHolder {
        return NotificationsLoadStateViewHolder.create(parent, retry)
    }

    override fun onBindViewHolder(holder: NotificationsLoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }
}