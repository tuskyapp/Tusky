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

import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter.FollowState.*
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.pkmmte.view.CircularImageView
import com.squareup.picasso.Picasso

class FollowingAccountListAdapter(private val accountList: MutableList<Pair<AccountEntity, FollowState>>,
                                  private val onFollowingAccountSelectedListener: OnFollowingAccountSelectedListener)
    : RecyclerView.Adapter<FollowingAccountListAdapter.FollowingAccountListViewHolder>() {

    enum class FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED
    }

    override fun getItemCount(): Int {
        return accountList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingAccountListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_following_account, parent, false) as RelativeLayout
        return FollowingAccountListViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowingAccountListViewHolder, position: Int) {
        val account = accountList[position].first
        val followState = accountList[position].second

        Picasso.with(holder.layout.context).load(account.profilePictureUrl)
                .error(R.drawable.avatar_default)
                .placeholder(R.drawable.avatar_default)
                .into(holder.avatar)

        holder.displayName.text = account.displayName
        holder.username.text = account.username

        holder.followButton.setText(when (followState) {
            NOT_FOLLOWING -> R.string.action_follow
            FOLLOWING -> R.string.action_unfollow
            REQUESTED -> R.string.state_follow_requested
        })

        holder.followButton.setOnClickListener {
            onFollowingAccountSelectedListener.onFollowingAccountSelected(account, position)
        }
    }

    fun updateAccount(account: AccountEntity, followState: FollowingAccountListAdapter.FollowState) {
        val position = accountList.indexOfFirst { it.first == account }
        if (position in 0 until accountList.size) {
            accountList[position] = account to followState

            notifyItemChanged(position)
        } else {
            accountList.add(account to followState)

            notifyItemInserted(accountList.size - 1)
        }
    }

    class FollowingAccountListViewHolder(val layout: RelativeLayout) : RecyclerView.ViewHolder(layout) {
        val avatar: CircularImageView = layout.findViewById(R.id.account_avatar)
        val displayName: TextView = layout.findViewById(R.id.account_display_name)
        val username: TextView = layout.findViewById(R.id.account_username)
        val followButton: AppCompatButton = layout.findViewById(R.id.follow_btn)
    }

}

interface OnFollowingAccountSelectedListener {
    fun onFollowingAccountSelected(account: AccountEntity, position: Int)
}
