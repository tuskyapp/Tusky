package com.keylesspalace.tusky.components.notifications

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowBinding
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val notificationActionListener: NotificationActionListener,
    private val statusDisplayOptions: StatusDisplayOptions
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius42dp = itemView.context.resources.getDimensionPixelSize(
        R.dimen.avatar_radius_42dp
    )

    override fun bind(viewData: NotificationViewData.Concrete, payloads: List<*>?) {
        // TODO: This was in the original code. Why skip if there's a payload?
        if (!payloads.isNullOrEmpty()) return

        setMessage(viewData.account, viewData.type === Notification.Type.SIGN_UP)
        setupButtons(notificationActionListener, viewData.account.id)
    }

    private fun setMessage(account: TimelineAccount, isSignUp: Boolean) {
        val context = binding.notificationText.context
        val format =
            context.getString(
                if (isSignUp) {
                    R.string.notification_sign_up_format
                } else {
                    R.string.notification_follow_format
                }
            )
        val wrappedDisplayName = account.name.unicodeWrap()
        val wholeMessage = String.format(format, wrappedDisplayName)
        val emojifiedMessage =
            wholeMessage.emojify(
                account.emojis,
                binding.notificationText,
                statusDisplayOptions.animateEmojis
            )
        binding.notificationText.text = emojifiedMessage
        val username = context.getString(R.string.post_username_format, account.username)
        binding.notificationUsername.text = username
        val emojifiedDisplayName = wrappedDisplayName.emojify(
            account.emojis,
            binding.notificationUsername,
            statusDisplayOptions.animateEmojis
        )
        binding.notificationDisplayName.text = emojifiedDisplayName
        loadAvatar(
            account.avatar,
            binding.notificationAvatar,
            avatarRadius42dp,
            statusDisplayOptions.animateAvatars
        )
    }

    private fun setupButtons(listener: NotificationActionListener, accountId: String) {
        binding.root.setOnClickListener { listener.onViewAccount(accountId) }
    }
}
