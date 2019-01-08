/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import tech.bigfig.roma.R;
import tech.bigfig.roma.entity.Account;
import tech.bigfig.roma.interfaces.AccountActionListener;
import tech.bigfig.roma.util.CustomEmojiHelper;
import com.squareup.picasso.Picasso;

public class FollowRequestsAdapter extends AccountAdapter {

    public FollowRequestsAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_ACCOUNT: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_follow_request, parent, false);
                return new FollowRequestViewHolder(view);
            }
            case VIEW_TYPE_FOOTER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_footer, parent, false);
                return new LoadingFooterViewHolder(view);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_ACCOUNT) {
            FollowRequestViewHolder holder = (FollowRequestViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            holder.setupActionListener(accountActionListener);
        }
    }

    static class FollowRequestViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
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
            id = account.getId();
            CharSequence emojifiedName = CustomEmojiHelper.emojifyString(account.getName(), account.getEmojis(), displayName);
            displayName.setText(emojifiedName);
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.getUsername());
            username.setText(formattedUsername);
            Picasso.with(avatar.getContext())
                    .load(account.getAvatar())
                    .placeholder(R.drawable.avatar_default)
                    .into(avatar);
        }

        void setupActionListener(final AccountActionListener listener) {
            accept.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRespondToFollowRequest(true, id, position);
                }
            });
            reject.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRespondToFollowRequest(false, id, position);
                }
            });
            avatar.setOnClickListener(v -> listener.onViewAccount(id));
        }
    }
}
