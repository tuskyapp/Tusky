package com.keylesspalace.tusky.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.AccountActionListener;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
import com.keylesspalace.tusky.util.ImageLoadingHelper;

import java.util.HashMap;

public class MutesAdapter extends AccountAdapter {
    private HashMap<String, Boolean> mutingNotificationsMap;

    public MutesAdapter(AccountActionListener accountActionListener) {
        super(accountActionListener);
        mutingNotificationsMap = new HashMap<String, Boolean>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            default:
            case VIEW_TYPE_ACCOUNT: {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_muted_user, parent, false);
                return new MutesAdapter.MutedUserViewHolder(view);
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
            MutedUserViewHolder holder = (MutedUserViewHolder) viewHolder;
            Account account = accountList.get(position);
            holder.setupWithAccount(account, mutingNotificationsMap.get(account.getId()));
            holder.setupActionListener(accountActionListener);
        }
    }

    public void updateMutingNotifications(String id, boolean mutingNotifications, int position) {
        mutingNotificationsMap.put(id, mutingNotifications);
        notifyItemChanged(position);
    }

    public void updateMutingNotificationsMap(HashMap<String, Boolean> newMutingNotificationsMap) {
        mutingNotificationsMap.putAll(newMutingNotificationsMap);
        notifyDataSetChanged();
    }

    static class MutedUserViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private TextView username;
        private TextView displayName;
        private ImageButton unmute;
        private ImageButton muteNotifications;
        private String id;
        private boolean animateAvatar;
        private boolean notifications;

        MutedUserViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.muted_user_avatar);
            username = itemView.findViewById(R.id.muted_user_username);
            displayName = itemView.findViewById(R.id.muted_user_display_name);
            unmute = itemView.findViewById(R.id.muted_user_unmute);
            muteNotifications = itemView.findViewById(R.id.muted_user_mute_notifications);
            animateAvatar = PreferenceManager.getDefaultSharedPreferences(itemView.getContext())
                    .getBoolean("animateGifAvatars", false);
        }

        void setupWithAccount(Account account, Boolean mutingNotifications) {
            id = account.getId();
            CharSequence emojifiedName = CustomEmojiHelper.emojify(account.getName(), account.getEmojis(), displayName);
            displayName.setText(emojifiedName);
            String format = username.getContext().getString(R.string.status_username_format);
            String formattedUsername = String.format(format, account.getUsername());
            username.setText(formattedUsername);
            int avatarRadius = avatar.getContext().getResources()
                    .getDimensionPixelSize(R.dimen.avatar_radius_48dp);
            ImageLoadingHelper.loadAvatar(account.getAvatar(), avatar, avatarRadius, animateAvatar);

            String unmuteString = unmute.getContext().getString(R.string.action_unmute_desc, formattedUsername);
            unmute.setContentDescription(unmuteString);
            ViewCompat.setTooltipText(unmute, unmuteString);

            if (mutingNotifications == null) {
                muteNotifications.setEnabled(false);
                notifications = true;
            } else {
                muteNotifications.setEnabled(true);
                notifications = mutingNotifications;
            }

            if (notifications) {
                muteNotifications.setImageResource(R.drawable.ic_notifications_24dp);
                String unmuteNotificationsString = muteNotifications.getContext()
                    .getString(R.string.action_unmute_notifications_desc, formattedUsername);
                muteNotifications.setContentDescription(unmuteNotificationsString);
                ViewCompat.setTooltipText(muteNotifications, unmuteNotificationsString);
            } else {
                muteNotifications.setImageResource(R.drawable.ic_notifications_off_24dp);
                String muteNotificationsString = muteNotifications.getContext()
                    .getString(R.string.action_mute_notifications_desc, formattedUsername);
                muteNotifications.setContentDescription(muteNotificationsString);
                ViewCompat.setTooltipText(muteNotifications, muteNotificationsString);
            }
        }

        void setupActionListener(final AccountActionListener listener) {
            unmute.setOnClickListener(v -> listener.onMute(false, id, getAdapterPosition(), false));
            muteNotifications.setOnClickListener(
                v -> listener.onMute(true, id, getAdapterPosition(), !notifications));
            itemView.setOnClickListener(v -> listener.onViewAccount(id));
        }
    }
}
