/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import com.keylesspalace.tusky.databinding.ItemNetworkStateBinding
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.visible

class ConversationLoadStateAdapter(
    private val retryCallback: () -> Unit
) : LoadStateAdapter<BindingHolder<ItemNetworkStateBinding>>() {

    override fun onBindViewHolder(holder: BindingHolder<ItemNetworkStateBinding>, loadState: LoadState) {
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
        val binding = ItemNetworkStateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }
}
