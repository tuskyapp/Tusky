package com.keylesspalace.tusky.components.notifications

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowBinding
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData

class FollowViewHolder(
    private val binding: ItemFollowBinding,
    private val listener: AccountActionListener,
) : RecyclerView.ViewHolder(binding.root), NotificationsViewHolder {

    override fun bind(
        viewData: NotificationViewData.Concrete,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val context = itemView.context
        val account = viewData.account
        val messageTemplate =
            context.getString(if (viewData.type == Notification.Type.SIGN_UP) R.string.notification_sign_up_format else R.string.notification_follow_format)
        val wrappedDisplayName = account.name.unicodeWrap()

        binding.notificationText.text = messageTemplate.format(wrappedDisplayName)
            .emojify(account.emojis, binding.notificationText, statusDisplayOptions.animateEmojis)

        binding.notificationUsername.text = context.getString(R.string.post_username_format, viewData.account.username)

        val emojifiedDisplayName = wrappedDisplayName.emojify(
            account.emojis,
            binding.notificationDisplayName,
            statusDisplayOptions.animateEmojis
        )
        binding.notificationDisplayName.text = emojifiedDisplayName

        val avatarRadius = context.resources
            .getDimensionPixelSize(R.dimen.avatar_radius_42dp)
        loadAvatar(
            account.avatar,
            binding.notificationAvatar,
            avatarRadius,
            statusDisplayOptions.animateAvatars,
            null
        )

        itemView.setOnClickListener { listener.onViewAccount(account.id) }
    }
}
