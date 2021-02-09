package com.keylesspalace.tusky.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.*
import kotlinx.android.synthetic.main.item_follow_request_notification.view.*

internal class FollowRequestViewHolder(
        itemView: View,
        private val showHeader: Boolean) : RecyclerView.ViewHolder(itemView) {
    private var id: String? = null

    fun setupWithAccount(account: Account, animateAvatar: Boolean, animateEmojis: Boolean) {
        id = account.id
        val wrappedName = account.name.unicodeWrap()
        val emojifiedName: CharSequence = wrappedName.emojify(account.emojis, itemView, animateEmojis)
        itemView.displayNameTextView.text = emojifiedName
        if (showHeader) {
            val wholeMessage: String = itemView.context.getString(R.string.notification_follow_request_format, wrappedName)
            itemView.notificationTextView?.text = SpannableStringBuilder(wholeMessage).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, wrappedName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }.emojify(account.emojis, itemView, animateEmojis)
        }
        itemView.notificationTextView?.visible(showHeader)
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
        itemView.setOnClickListener { listener.onViewAccount(id) }
    }
}
