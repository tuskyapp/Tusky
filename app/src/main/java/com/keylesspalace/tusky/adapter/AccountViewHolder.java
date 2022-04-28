package com.keylesspalace.tusky.adapter;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.TimelineAccount;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.interfaces.LinkListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.ImageLoadingHelper;

public class AccountViewHolder extends RecyclerView.ViewHolder {
    private TextView username;
    private TextView displayName;
    private ImageView avatar;
    private ImageView avatarInset;
    private String accountId;
    private boolean showBotOverlay;

    public AccountViewHolder(View itemView) {
        super(itemView);
        username = itemView.findViewById(R.id.account_username);
        displayName = itemView.findViewById(R.id.account_display_name);
        avatar = itemView.findViewById(R.id.account_avatar);
        avatarInset = itemView.findViewById(R.id.account_avatar_inset);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(itemView.getContext());
        showBotOverlay = sharedPrefs.getBoolean("showBotOverlay", true);
    }

    public void setupWithAccount(TimelineAccount account, boolean animateAvatar, boolean animateEmojis) {
        accountId = account.getId();
        String format = username.getContext().getString(R.string.post_username_format);
        String formattedUsername = String.format(format, account.getUsername());
        username.setText(formattedUsername);
        CharSequence emojifiedName = CustomEmojiHelper.emojify(account.getName(), account.getEmojis(), displayName, animateEmojis);
        displayName.setText(emojifiedName);
        int avatarRadius = avatar.getContext().getResources()
                .getDimensionPixelSize(R.dimen.avatar_radius_48dp);
        ImageLoadingHelper.loadAvatar(account.getAvatar(), avatar, avatarRadius, animateAvatar);
        if (showBotOverlay && account.getBot()) {
            avatarInset.setVisibility(View.VISIBLE);
            avatarInset.setImageResource(R.drawable.bot_badge);
        } else {
            avatarInset.setVisibility(View.GONE);
        }
    }

    void setupActionListener(final AccountActionListener listener) {
        itemView.setOnClickListener(v -> listener.onViewAccount(accountId));
    }

    public void setupLinkListener(final LinkListener listener) {
        itemView.setOnClickListener(v -> listener.onViewAccount(accountId));
    }
}