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
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.FollowingAccountListAdapter.FollowState.*
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.AuthorizeFollow
import com.pkmmte.view.CircularImageView
import com.squareup.picasso.Picasso

class FollowingAccountListAdapter(
        private val authorizeFollowList: MutableList<AuthorizeFollow>,
        private val onFollowingAccountSelectedListener: OnFollowingAccountSelectedListener
) : RecyclerView.Adapter<FollowingAccountListAdapter.FollowingAccountListViewHolder>() {

    enum class FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED
    }

    override fun getItemCount(): Int {
        return authorizeFollowList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingAccountListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_following_account, parent, false) as RelativeLayout
        return FollowingAccountListViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowingAccountListViewHolder, position: Int) {
        val follow = authorizeFollowList[position]

        Picasso.with(holder.layout.context).load(follow.accountEntity.profilePictureUrl)
                .error(R.drawable.avatar_default)
                .placeholder(R.drawable.avatar_default)
                .into(holder.avatar)

        holder.displayName.text = follow.accountEntity.displayName
        holder.username.text = follow.accountEntity.username

        holder.followButton.setText(when (follow.followState) {
            NOT_FOLLOWING -> R.string.action_follow
            FOLLOWING -> R.string.action_unfollow
            REQUESTED -> R.string.state_follow_requested
        })

        holder.followButton.setOnClickListener {
            onFollowingAccountSelectedListener.onFollowingAccountSelected(follow)
        }
    }

    fun updateAccount(accountEntity: AccountEntity, followState: FollowState, account: Account? = null, anyPendingTransaction: Boolean = false) {
        val position = authorizeFollowList.indexOfFirst { it.accountEntity == accountEntity }
        if (position >= 0) {
            var changed = false
            val oldAuthorizeFollow = authorizeFollowList[position]

            if (oldAuthorizeFollow.followState != followState) {
                authorizeFollowList[position].followState = followState

                changed = true
            }

            if (oldAuthorizeFollow.anyPendingTransaction != anyPendingTransaction) {
                authorizeFollowList[position].anyPendingTransaction = anyPendingTransaction

                changed = true
            }

            if (changed) {
                notifyItemChanged(position)
            }
        } else if (account != null) {
            authorizeFollowList.add(AuthorizeFollow(accountEntity, account, followState, anyPendingTransaction))

            notifyItemInserted(authorizeFollowList.size - 1)
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
    fun onFollowingAccountSelected(authorizeFollow: AuthorizeFollow)
}
