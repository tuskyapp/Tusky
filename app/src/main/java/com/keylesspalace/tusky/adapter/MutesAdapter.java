package com.keylesspalace.tusky.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

public class MutesAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_MUTED_USER = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    public MutesAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_MUTED_USER: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_muted_user, parent, false);
                return new MutesAdapter.MutedUserViewHolder(view);
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
            MutedUserViewHolder holder = (MutedUserViewHolder) viewHolder;
            holder.setupWithAccount(accountList.get(position));
            holder.setupActionListener(accountActionListener, true, position);
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
            return VIEW_TYPE_MUTED_USER;
        }
    }

    static class MutedUserViewHolder extends RecyclerView.ViewHolder {
        private CircularImageView avatar;
        private TextView username;
        private TextView displayName;
        private ImageButton unmute;
        private String id;

        MutedUserViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.muted_user_avatar);
            username = itemView.findViewById(R.id.muted_user_username);
            displayName = itemView.findViewById(R.id.muted_user_display_name);
            unmute = itemView.findViewById(R.id.muted_user_unmute);
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

        void setupActionListener(final AccountActionListener listener, final boolean muted,
                final int position) {
            unmute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onMute(!muted, id, position);
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
