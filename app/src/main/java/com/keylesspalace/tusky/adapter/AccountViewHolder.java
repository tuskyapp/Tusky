package com.keylesspalace.tusky.adapter;

import androidx.recyclerview.widget.RecyclerView;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.interfaces.LinkListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.ImageLoadingHelper;

class AccountViewHolder extends RecyclerView.ViewHolder {
    private TextView username;
    private TextView displayName;
    private ImageView avatar;
    private ImageView avatarInset;
    private String accountId;
    private boolean showBotOverlay;
    private boolean animateAvatar;

    AccountViewHolder(View itemView) {
        super(itemView);
        username = itemView.findViewById(R.id.account_username);
        displayName = itemView.findViewById(R.id.account_display_name);
        avatar = itemView.findViewById(R.id.account_avatar);
        avatarInset = itemView.findViewById(R.id.account_avatar_inset);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(itemView.getContext());
        showBotOverlay = sharedPrefs.getBoolean("showBotOverlay", true);
        animateAvatar = sharedPrefs.getBoolean("animateGifAvatars", false);
    }

    void setupWithAccount(Account account) {
        accountId = account.getId();
        String format = username.getContext().getString(R.string.status_username_format);
        String formattedUsername = String.format(format, account.getUsername());
        username.setText(formattedUsername);
        CharSequence emojifiedName = CustomEmojiHelper.emojifyString(account.getName(), account.getEmojis(), displayName);
        displayName.setText(emojifiedName);
        int avatarRadius = avatar.getContext().getResources()
                .getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        ImageLoadingHelper.loadAvatar(account.getAvatar(), avatar, avatarRadius, animateAvatar);
        if (showBotOverlay && account.getBot()) {
            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setImageResource(R.drawable.ic_bot_24dp);
            avatarInset.setBackgroundColor(0x50ffffff);
        } else {
            avatarInset.setVisibility(View.GONE);
        }
    }

    void setupActionListener(final AccountActionListener listener) {
        itemView.setOnClickListener(v -> listener.onViewAccount(accountId));
    }

    void setupLinkListener(final LinkListener listener) {
        itemView.setOnClickListener(v -> listener.onViewAccount(accountId));
    }
}