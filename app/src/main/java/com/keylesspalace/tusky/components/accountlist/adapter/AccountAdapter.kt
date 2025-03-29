/* Copyright 2021 Tusky Contributors.
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
package com.keylesspalace.tusky.components.accountlist.adapter

import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.components.accountlist.AccountViewData
import com.keylesspalace.tusky.interfaces.AccountActionListener

abstract class AccountAdapter<AVH : RecyclerView.ViewHolder>(
    protected val accountActionListener: AccountActionListener,
    protected val animateAvatar: Boolean,
    protected val animateEmojis: Boolean,
    protected val showBotOverlay: Boolean
) : PagingDataAdapter<AccountViewData, AVH>(TimelineAccountDifferCallback) {

    companion object {
        private val TimelineAccountDifferCallback = object : DiffUtil.ItemCallback<AccountViewData>() {
            override fun areItemsTheSame(
                oldItem: AccountViewData,
                newItem: AccountViewData
            ): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: AccountViewData,
                newItem: AccountViewData
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
