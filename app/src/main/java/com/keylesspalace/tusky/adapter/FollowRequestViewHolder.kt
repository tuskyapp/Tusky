package com.keylesspalace.tusky.adapter

import android.view.View
import androidx.core.text.BidiFormatter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.CustomEmojiHelper
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.visible
import kotlinx.android.synthetic.main.item_follow_request_notification.view.*

internal class FollowRequestViewHolder(itemView: View, private val showHeader: Boolean) : RecyclerView.ViewHolder(itemView) {
    private var id: String? = null
    private val animateAvatar: Boolean = PreferenceManager.getDefaultSharedPreferences(itemView.context)
            .getBoolean("animateGifAvatars", false)

    fun setupWithAccount(account: Account, formatter: BidiFormatter?) {
        id = account.id
        val wrappedName = formatter?.unicodeWrap(account.name) ?: account.name
        val emojifiedName: CharSequence = CustomEmojiHelper.emojifyString(wrappedName, account.emojis, itemView)
        itemView.displayNameTextView.text = emojifiedName
        if (showHeader) {
            itemView.notificationTextView.text = itemView.context.getString(R.string.notification_follow_request_format, emojifiedName)
        }
        itemView.notificationTextView.visible(showHeader)
        val format = itemView.context.getString(R.string.status_username_format)
        val formattedUsername = String.format(format, account.username)
        itemView.usernameTextView.text = formattedUsername
        val avatarRadius = itemView.avatar.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, itemView.avatar, avatarRadius, animateAvatar)
    }

    fun setupActionListener(listener: AccountActionListener) {
        itemView.acceptButton.setOnClickListener {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(true, id, position)
            }
        }
        itemView.rejectButton.setOnClickListener {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onRespondToFollowRequest(false, id, position)
            }
        }
        itemView.avatar.setOnClickListener { listener.onViewAccount(id) }
    }
}
