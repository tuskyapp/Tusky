package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.interfaces.LinkListener;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

class AccountViewHolder extends RecyclerView.ViewHolder {
    private View container;
    private TextView username;
    private TextView displayName;
    private CircularImageView avatar;
    private String id;

    AccountViewHolder(View itemView) {
        super(itemView);
        container = itemView.findViewById(R.id.account_container);
        username = (TextView) itemView.findViewById(R.id.account_username);
        displayName = (TextView) itemView.findViewById(R.id.account_display_name);
        avatar = (CircularImageView) itemView.findViewById(R.id.account_avatar);
    }

    void setupWithAccount(Account account) {
        id = account.id;
        String format = username.getContext().getString(R.string.status_username_format);
        String formattedUsername = String.format(format, account.username);
        username.setText(formattedUsername);
        displayName.setText(account.getDisplayName());
        Context context = avatar.getContext();
        Picasso.with(context)
                .load(account.avatar)
                .placeholder(R.drawable.avatar_default)
                .error(R.drawable.avatar_error)
                .into(avatar);
    }

    void setupActionListener(final AccountActionListener listener) {
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewAccount(id);
            }
        });
    }

    void setupLinkListener(final LinkListener listener) {
        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onViewAccount(id);
            }
        });
    }
}