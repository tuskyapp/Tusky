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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.databinding.ItemFooterBinding
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.removeDuplicates

/** Generic adapter with bottom loading indicator. */
abstract class AccountAdapter<AVH : RecyclerView.ViewHolder> internal constructor(
    protected val accountActionListener: AccountActionListener,
    protected val animateAvatar: Boolean,
    protected val animateEmojis: Boolean,
    protected val showBotOverlay: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder?>() {

    protected var accountList: MutableList<TimelineAccount> = mutableListOf()
    private var bottomLoading: Boolean = false

    override fun getItemCount(): Int {
        return accountList.size + if (bottomLoading) 1 else 0
    }

    abstract fun createAccountViewHolder(parent: ViewGroup): AVH

    abstract fun onBindAccountViewHolder(viewHolder: AVH, position: Int)

    final override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIEW_TYPE_ACCOUNT) {
            @Suppress("UNCHECKED_CAST")
            this.onBindAccountViewHolder(holder as AVH, position)
        }
    }

    final override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ACCOUNT -> this.createAccountViewHolder(parent)
            VIEW_TYPE_FOOTER -> this.createFooterViewHolder(parent)
            else -> error("Unknown item type: $viewType")
        }
    }

    private fun createFooterViewHolder(
        parent: ViewGroup,
    ): RecyclerView.ViewHolder {
        val binding = ItemFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == accountList.size && bottomLoading) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ACCOUNT
        }
    }

    fun update(newAccounts: List<TimelineAccount>) {
        accountList = removeDuplicates(newAccounts)
        notifyDataSetChanged()
    }

    fun addItems(newAccounts: List<TimelineAccount>) {
        val end = accountList.size
        val last = accountList[end - 1]
        if (newAccounts.none { it.id == last.id }) {
            accountList.addAll(newAccounts)
            notifyItemRangeInserted(end, newAccounts.size)
        }
    }

    fun setBottomLoading(loading: Boolean) {
        val wasLoading = bottomLoading
        if (wasLoading == loading) {
            return
        }
        bottomLoading = loading
        if (loading) {
            notifyItemInserted(accountList.size)
        } else {
            notifyItemRemoved(accountList.size)
        }
    }

    fun removeItem(position: Int): TimelineAccount? {
        if (position < 0 || position >= accountList.size) {
            return null
        }
        val account = accountList.removeAt(position)
        notifyItemRemoved(position)
        return account
    }

    fun addItem(account: TimelineAccount, position: Int) {
        if (position < 0 || position > accountList.size) {
            return
        }
        accountList.add(position, account)
        notifyItemInserted(position)
    }

    companion object {
        const val VIEW_TYPE_ACCOUNT = 0
        const val VIEW_TYPE_FOOTER = 1
    }
}
