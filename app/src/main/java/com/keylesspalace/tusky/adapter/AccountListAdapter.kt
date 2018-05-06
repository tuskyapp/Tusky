/* Copyright 2018 Jeremiasz Nelz
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

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountEntity
import com.pkmmte.view.CircularImageView
import com.squareup.picasso.Picasso

class AccountListAdapter(private val accountList: List<AccountEntity>,
                         private val onAccountSelectedListener : OnAccountSelectedListener)
    : RecyclerView.Adapter<AccountListAdapter.AccountListViewHolder>() {

    override fun getItemCount(): Int {
        return accountList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false) as RelativeLayout
        return AccountListViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountListViewHolder, position: Int) {
        val account = accountList[position]

        Picasso.with(holder.layout.context).load(account.profilePictureUrl)
                .error(R.drawable.avatar_default)
                .placeholder(R.drawable.avatar_default)
                .into(holder.avatar)

        holder.displayName.text = account.displayName
        holder.username.text = account.username

        holder.layout.setOnClickListener {
            onAccountSelectedListener.onAccountSelected(account)
        }
    }

    class AccountListViewHolder(val layout: RelativeLayout) : RecyclerView.ViewHolder(layout) {
        val avatar: CircularImageView = layout.findViewById(R.id.account_avatar)
        val displayName: TextView = layout.findViewById(R.id.account_display_name)
        val username: TextView = layout.findViewById(R.id.account_username)
    }

}

interface OnAccountSelectedListener {
    fun onAccountSelected(account: AccountEntity)
}
