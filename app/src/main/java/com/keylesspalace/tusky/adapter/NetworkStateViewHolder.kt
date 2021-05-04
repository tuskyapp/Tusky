/* Copyright 2019 Conny Duck
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

package com.keylesspalace.tusky.adapter

import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import com.keylesspalace.tusky.databinding.ItemNetworkStateBinding
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.util.Status
import com.keylesspalace.tusky.util.visible

class NetworkStateViewHolder(private val binding: ItemNetworkStateBinding,
                             private val retryCallback: () -> Unit)
: RecyclerView.ViewHolder(binding.root) {

    fun setUpWithNetworkState(state: NetworkState?, fullScreen: Boolean) {
        binding.progressBar.visible(state?.status == Status.RUNNING)
        binding.retryButton.visible(state?.status == Status.FAILED)
        binding.errorMsg.visible(state?.msg != null)
        binding.errorMsg.text = state?.msg
        binding.retryButton.setOnClickListener {
            retryCallback()
        }
        if(fullScreen) {
            binding.root.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            binding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

}