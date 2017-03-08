/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.HashSet;
import java.util.Set;

class BlocksAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_BLOCKED_USER = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private Set<Integer> unblockedAccountPositions;

    BlocksAdapter(AccountActionListener accountActionListener,
            FooterActionListener footerActionListener) {
        super(accountActionListener, footerActionListener);
        unblockedAccountPositions = new HashSet<>();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_BLOCKED_USER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_blocked_user, parent, false);
                return new BlockedUserViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_footer, parent, false);
                return new FooterViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (position < accountList.size()) {
            BlockedUserViewHolder holder = (BlockedUserViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            boolean blocked = !unblockedAccountPositions.contains(position);
            holder.setupActionListener(accountActionListener, blocked, position);
        } else {
            FooterViewHolder holder = (FooterViewHolder) viewHolder;
            holder.setState(footerState);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == accountList.size()) {
            return VIEW_TYPE_FOOTER;
        } else {
            return VIEW_TYPE_BLOCKED_USER;
        }
    }

    void setBlocked(boolean blocked, int position) {
        if (blocked) {
            unblockedAccountPositions.remove(position);
        } else {
            unblockedAccountPositions.add(position);
        }
        notifyItemChanged(position);
    }

    private static class BlockedUserViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private TextView username;
        private TextView displayName;
        private Button unblock;
        private String id;

        BlockedUserViewHolder(View itemView) {
            super(itemView);
            avatar = (ImageView) itemView.findViewById(R.id.blocked_user_avatar);
            displayName = (TextView) itemView.findViewById(R.id.blocked_user_display_name);
            username = (TextView) itemView.findViewById(R.id.blocked_user_username);
            unblock = (Button) itemView.findViewById(R.id.blocked_user_unblock);
        }

        void setupWithAccount(Account account) {
            id = account.id;
            displayName.setText(account.displayName);
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.username);
            username.setText(formattedUsername);
            Picasso.with(avatar.getContext())
                    .load(account.avatar)
                    .error(R.drawable.avatar_error)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        void setupActionListener(final AccountActionListener listener, final boolean blocked,
                final int position) {
            int unblockTextId;
            if (blocked) {
                unblockTextId = R.string.action_unblock;
            } else {
                unblockTextId = R.string.action_block;
            }
            unblock.setText(unblock.getContext().getString(unblockTextId));
            unblock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onBlock(!blocked, id, position);
                }
            });
            avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onViewAccount(id);
                }
            });
        }
    }
}
