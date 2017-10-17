/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

public class FollowRequestsAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_FOLLOW_REQUEST = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public FollowRequestsAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_FOLLOW_REQUEST: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_follow_request, parent, false);
                return new FollowRequestViewHolder(view);
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
            FollowRequestViewHolder holder = (FollowRequestViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            holder.setupActionListener(accountActionListener);
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
            return VIEW_TYPE_FOLLOW_REQUEST;
        }
    }

    static class FollowRequestViewHolder extends RecyclerView.ViewHolder {
        private CircularImageView avatar;
        private TextView username;
        private TextView displayName;
        private ImageButton accept;
        private ImageButton reject;
        private String id;

        FollowRequestViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.follow_request_avatar);
            username = itemView.findViewById(R.id.follow_request_username);
            displayName = itemView.findViewById(R.id.follow_request_display_name);
            accept = itemView.findViewById(R.id.follow_request_accept);
            reject = itemView.findViewById(R.id.follow_request_reject);
        }

        void setupWithAccount(Account account) {
            id = account.id;
            displayName.setText(account.getDisplayName());
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.username);
            username.setText(formattedUsername);
            Picasso.with(avatar.getContext())
                    .load(account.avatar)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        void setupActionListener(final AccountActionListener listener) {
            accept.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onRespondToFollowRequest(true, id, position);
                    }
                }
            });
            reject.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onRespondToFollowRequest(false, id, position);
                    }
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
