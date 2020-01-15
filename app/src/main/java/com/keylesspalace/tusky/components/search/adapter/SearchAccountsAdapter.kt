/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.search.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.AccountViewHolder
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.interfaces.LinkListener

class SearchAccountsAdapter(private val linkListener: LinkListener)
    : PagedListAdapter<Account, RecyclerView.ViewHolder>(ACCOUNT_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_account, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        getItem(position)?.let { item ->
            (holder as AccountViewHolder).apply {
                setupWithAccount(item)
                setupLinkListener(linkListener)
            }
        }
    }

    companion object {

        val ACCOUNT_COMPARATOR = object : DiffUtil.ItemCallback<Account>() {
            override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean =
                    oldItem.deepEquals(newItem)

            override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean =
                    oldItem.id == newItem.id
        }

    }

}