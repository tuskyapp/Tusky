package com.keylesspalace.tusky.components.notifications.requests

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemNotificationRequestBinding
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar

class NotificationRequestsAdapter(
    private val onAcceptRequest: (String) -> Unit,
    private val onDismissRequest: (String) -> Unit,
    private val animateAvatar: Boolean,
    private val animateEmojis: Boolean,
) : PagingDataAdapter<NotificationRequest, BindingHolder<ItemNotificationRequestBinding>>(NOTIFICATION_REQUEST_COMPARATOR) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BindingHolder<ItemNotificationRequestBinding> {
        val binding = ItemNotificationRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingHolder(binding)
    }

    @OptIn(ExperimentalBadgeUtils::class)
    override fun onBindViewHolder(holder: BindingHolder<ItemNotificationRequestBinding>, position: Int) {
        getItem(position)?.let { notificationRequest ->
            val binding = holder.binding
            val context = binding.root.context
            val account = notificationRequest.account

            val avatarRadius = context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
            loadAvatar(account.avatar, binding.notificationRequestAvatar, avatarRadius, animateAvatar)

            binding.notificationRequestBadge.text = notificationRequest.notificationsCount

            val emojifiedName = account.name.emojify(
                account.emojis,
                binding.notificationRequestDisplayName,
                animateEmojis
            )
            binding.notificationRequestDisplayName.text = emojifiedName
            val formattedUsername = context.getString(R.string.post_username_format, account.username)
            binding.notificationRequestUsername.text = formattedUsername

            binding.notificationRequestAccept.setOnClickListener {
                onAcceptRequest(notificationRequest.id)
            }
            binding.notificationRequestDismiss.setOnClickListener {
                onDismissRequest(notificationRequest.id)
            }
        }
    }

    companion object {
        val NOTIFICATION_REQUEST_COMPARATOR = object : DiffUtil.ItemCallback<NotificationRequest>() {
            override fun areItemsTheSame(oldItem: NotificationRequest, newItem: NotificationRequest): Boolean =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NotificationRequest, newItem: NotificationRequest): Boolean =
                oldItem == newItem
        }
    }
}
