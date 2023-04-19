/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemLoadStateFooterViewBinding
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
class TimelineLoadStateViewHolder(
    private val binding: ItemLoadStateFooterViewBinding,
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
        fun create(parent: ViewGroup, retry: () -> Unit): TimelineLoadStateViewHolder {
            val binding = ItemLoadStateFooterViewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return TimelineLoadStateViewHolder(binding, retry)
        }
    }
}
