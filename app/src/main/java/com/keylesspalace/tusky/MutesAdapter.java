package com.keylesspalace.tusky;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Account;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

import java.util.HashSet;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;

class MutesAdapter extends AccountAdapter {
    private static final int VIEW_TYPE_MUTED_USER = 0;
    private static final int VIEW_TYPE_FOOTER = 1;

    private Set<Integer> unmutedAccountPositions;

    MutesAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
        unmutedAccountPositions = new HashSet<>();
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
            boolean muted = !unmutedAccountPositions.contains(position);
            holder.setupActionListener(accountActionListener, muted, position);
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

    void setMuted(boolean muted, int position) {
        if (muted) {
            unmutedAccountPositions.remove(position);
        } else {
            unmutedAccountPositions.add(position);
        }
        notifyItemChanged(position);
    }

    static class MutedUserViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.muted_user_avatar) CircularImageView avatar;
        @BindView(R.id.muted_user_username) TextView username;
        @BindView(R.id.muted_user_display_name) TextView displayName;
        @BindView(R.id.muted_user_unmute) ImageButton unmute;

        private String id;

        MutedUserViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        void setupWithAccount(Account account) {
            id = account.id;
            displayName.setText(account.getDisplayName());
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.username);
            username.setText(formattedUsername);
            Picasso.with(avatar.getContext())
                    .load(account.avatar)
                    .error(R.drawable.avatar_error)
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
