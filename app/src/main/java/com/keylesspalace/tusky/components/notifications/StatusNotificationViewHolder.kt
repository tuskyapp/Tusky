/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.notifications

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.ItemStatusNotificationBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.SmartLengthInputFilter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.Date

/**
 * View holder for a status with an activity to be notified about (posted, boosted,
 * favourited, or edited, per [NotificationViewKind.from]).
 *
 * Shows a line with the activity, and who initiated the activity. Clicking this should
 * go to the profile page for the initiator.
 *
 * Displays the original status below that. Clicking this should go to the original
 * status in context.
 */
internal class StatusNotificationViewHolder(
    private val binding: ItemStatusNotificationBinding,
    private val statusActionListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val absoluteTimeFormatter: AbsoluteTimeFormatter
) : NotificationsPagingAdapter.ViewHolder, RecyclerView.ViewHolder(binding.root) {
    private val avatarRadius48dp = itemView.context.resources.getDimensionPixelSize(
        R.dimen.avatar_radius_48dp
    )
    private val avatarRadius36dp = itemView.context.resources.getDimensionPixelSize(
        R.dimen.avatar_radius_36dp
    )
    private val avatarRadius24dp = itemView.context.resources.getDimensionPixelSize(
        R.dimen.avatar_radius_24dp
    )

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val statusViewData = viewData.statusViewData
        if (payloads.isNullOrEmpty()) {
            // Hide null statuses. Shouldn't happen according to the spec, but some servers
            // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
            if (statusViewData == null) {
                showNotificationContent(false)
            } else {
                showNotificationContent(true)
                val (_, _, account, _, _, _, _, createdAt) = statusViewData.actionable
                setDisplayName(account.name, account.emojis, statusDisplayOptions.animateEmojis)
                setUsername(account.username)
                setCreatedAt(createdAt, statusDisplayOptions.useAbsoluteTime)
                if (viewData.type == Notification.Type.STATUS ||
                    viewData.type == Notification.Type.UPDATE
                ) {
                    setAvatar(
                        account.avatar,
                        account.bot,
                        statusDisplayOptions.animateAvatars,
                        statusDisplayOptions.showBotOverlay
                    )
                } else {
                    setAvatars(
                        account.avatar,
                        viewData.account.avatar,
                        statusDisplayOptions.animateAvatars
                    )
                }

                binding.notificationContainer.setOnClickListener {
                    notificationActionListener.onViewThreadForStatus(statusViewData.status)
                }
                binding.notificationContent.setOnClickListener {
                    notificationActionListener.onViewThreadForStatus(statusViewData.status)
                }
                binding.notificationTopText.setOnClickListener {
                    notificationActionListener.onViewAccount(viewData.account.id)
                }
            }
            setMessage(viewData, statusActionListener, statusDisplayOptions.animateEmojis)
        } else {
            for (item in payloads) {
                if (StatusBaseViewHolder.Key.KEY_CREATED == item && statusViewData != null) {
                    setCreatedAt(
                        statusViewData.status.actionableStatus.createdAt,
                        statusDisplayOptions.useAbsoluteTime
                    )
                }
            }
        }
    }

    private fun showNotificationContent(show: Boolean) {
        binding.statusDisplayName.visibility = if (show) View.VISIBLE else View.GONE
        binding.statusUsername.visibility = if (show) View.VISIBLE else View.GONE
        binding.statusMetaInfo.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationContentWarningDescription.visibility =
            if (show) View.VISIBLE else View.GONE
        binding.notificationContentWarningButton.visibility =
            if (show) View.VISIBLE else View.GONE
        binding.notificationContent.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationStatusAvatar.visibility = if (show) View.VISIBLE else View.GONE
        binding.notificationNotificationAvatar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setDisplayName(name: String, emojis: List<Emoji>?, animateEmojis: Boolean) {
        val emojifiedName = name.emojify(emojis, binding.statusDisplayName, animateEmojis)
        binding.statusDisplayName.text = emojifiedName
    }

    private fun setUsername(name: String) {
        val context = binding.statusUsername.context
        val format = context.getString(R.string.post_username_format)
        val usernameText = String.format(format, name)
        binding.statusUsername.text = usernameText
    }

    private fun setCreatedAt(createdAt: Date?, useAbsoluteTime: Boolean) {
        if (useAbsoluteTime) {
            binding.statusMetaInfo.text = absoluteTimeFormatter.format(createdAt, true)
        } else {
            // This is the visible timestampInfo.
            val readout: String
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */
            val readoutAloud: CharSequence
            if (createdAt != null) {
                val then = createdAt.time
                val now = Date().time
                readout = getRelativeTimeSpanString(binding.statusMetaInfo.context, then, now)
                readoutAloud = DateUtils.getRelativeTimeSpanString(
                    then,
                    now,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            } else {
                // unknown minutes~
                readout = "?m"
                readoutAloud = "? minutes"
            }
            binding.statusMetaInfo.text = readout
            binding.statusMetaInfo.contentDescription = readoutAloud
        }
    }

    private fun getIconWithColor(
        context: Context,
        @DrawableRes drawable: Int,
        @ColorRes color: Int
    ): Drawable? {
        val icon = ContextCompat.getDrawable(context, drawable)
        icon?.setColorFilter(context.getColor(color), PorterDuff.Mode.SRC_ATOP)
        return icon
    }

    private fun setAvatar(statusAvatarUrl: String?, isBot: Boolean, animateAvatars: Boolean, showBotOverlay: Boolean) {
        binding.notificationStatusAvatar.setPaddingRelative(0, 0, 0, 0)
        loadAvatar(
            statusAvatarUrl,
            binding.notificationStatusAvatar,
            avatarRadius48dp,
            animateAvatars
        )
        if (showBotOverlay && isBot) {
            binding.notificationNotificationAvatar.visibility = View.VISIBLE
            Glide.with(binding.notificationNotificationAvatar)
                .load(R.drawable.bot_badge)
                .into(binding.notificationNotificationAvatar)
        } else {
            binding.notificationNotificationAvatar.visibility = View.GONE
        }
    }

    private fun setAvatars(statusAvatarUrl: String?, notificationAvatarUrl: String?, animateAvatars: Boolean) {
        val padding = Utils.dpToPx(binding.notificationStatusAvatar.context, 12)
        binding.notificationStatusAvatar.setPaddingRelative(0, 0, padding, padding)
        loadAvatar(
            statusAvatarUrl,
            binding.notificationStatusAvatar,
            avatarRadius36dp,
            animateAvatars
        )
        binding.notificationNotificationAvatar.visibility = View.VISIBLE
        loadAvatar(
            notificationAvatarUrl,
            binding.notificationNotificationAvatar,
            avatarRadius24dp,
            animateAvatars
        )
    }

    fun setMessage(
        notificationViewData: NotificationViewData,
        listener: LinkListener,
        animateEmojis: Boolean
    ) {
        val statusViewData = notificationViewData.statusViewData
        val displayName = notificationViewData.account.name.unicodeWrap()
        val type = notificationViewData.type
        val context = binding.notificationTopText.context
        val format: String
        val icon: Drawable?
        when (type) {
            Notification.Type.FAVOURITE -> {
                icon = getIconWithColor(context, R.drawable.ic_star_24dp, R.color.tusky_orange)
                format = context.getString(R.string.notification_favourite_format)
            }
            Notification.Type.REBLOG -> {
                icon = getIconWithColor(context, R.drawable.ic_repeat_24dp, R.color.tusky_blue)
                format = context.getString(R.string.notification_reblog_format)
            }
            Notification.Type.STATUS -> {
                icon = getIconWithColor(context, R.drawable.ic_home_24dp, R.color.tusky_blue)
                format = context.getString(R.string.notification_subscription_format)
            }
            Notification.Type.UPDATE -> {
                icon = getIconWithColor(context, R.drawable.ic_edit_24dp, R.color.tusky_blue)
                format = context.getString(R.string.notification_update_format)
            }
            else -> {
                icon = getIconWithColor(context, R.drawable.ic_star_24dp, R.color.tusky_orange)
                format = context.getString(R.string.notification_favourite_format)
            }
        }
        binding.notificationTopText.setCompoundDrawablesWithIntrinsicBounds(
            icon,
            null,
            null,
            null
        )
        val wholeMessage = String.format(format, displayName)
        val str = SpannableStringBuilder(wholeMessage)
        val displayNameIndex = format.indexOf("%s")
        str.setSpan(
            StyleSpan(Typeface.BOLD),
            displayNameIndex,
            displayNameIndex + displayName.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val emojifiedText = str.emojify(
            notificationViewData.account.emojis,
            binding.notificationTopText,
            animateEmojis
        )
        binding.notificationTopText.text = emojifiedText
        if (statusViewData != null) {
            val hasSpoiler = !TextUtils.isEmpty(statusViewData.status.spoilerText)
            binding.notificationContentWarningDescription.visibility =
                if (hasSpoiler) View.VISIBLE else View.GONE
            binding.notificationContentWarningButton.visibility =
                if (hasSpoiler) View.VISIBLE else View.GONE
            if (statusViewData.isExpanded) {
                binding.notificationContentWarningButton.setText(
                    R.string.post_content_warning_show_less
                )
            } else {
                binding.notificationContentWarningButton.setText(
                    R.string.post_content_warning_show_more
                )
            }
            binding.notificationContentWarningButton.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    notificationActionListener.onExpandedChange(
                        !statusViewData.isExpanded,
                        bindingAdapterPosition
                    )
                }
                binding.notificationContent.visibility =
                    if (statusViewData.isExpanded) View.GONE else View.VISIBLE
            }
            setupContentAndSpoiler(listener, statusViewData, animateEmojis)
        }
    }

    private fun setupContentAndSpoiler(
        listener: LinkListener,
        statusViewData: StatusViewData.Concrete,
        animateEmojis: Boolean
    ) {
        val shouldShowContentIfSpoiler = statusViewData.isExpanded
        val hasSpoiler = !TextUtils.isEmpty(statusViewData.status.spoilerText)
        if (!shouldShowContentIfSpoiler && hasSpoiler) {
            binding.notificationContent.visibility = View.GONE
        } else {
            binding.notificationContent.visibility = View.VISIBLE
        }
        val content = statusViewData.content
        val emojis = statusViewData.actionable.emojis
        if (statusViewData.isCollapsible && (statusViewData.isExpanded || !hasSpoiler)) {
            binding.buttonToggleNotificationContent.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notificationActionListener.onNotificationContentCollapsedChange(
                        !statusViewData.isCollapsed,
                        position
                    )
                }
            }
            binding.buttonToggleNotificationContent.visibility = View.VISIBLE
            if (statusViewData.isCollapsed) {
                binding.buttonToggleNotificationContent.setText(
                    R.string.post_content_warning_show_more
                )
                binding.notificationContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleNotificationContent.setText(
                    R.string.post_content_warning_show_less
                )
                binding.notificationContent.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleNotificationContent.visibility = View.GONE
            binding.notificationContent.filters = NO_INPUT_FILTER
        }
        val emojifiedText =
            content.emojify(
                emojis,
                binding.notificationContent,
                animateEmojis
            )
        setClickableText(
            binding.notificationContent,
            emojifiedText,
            statusViewData.actionable.mentions,
            statusViewData.actionable.tags,
            listener
        )
        val emojifiedContentWarning: CharSequence = statusViewData.spoilerText.emojify(
            statusViewData.actionable.emojis,
            binding.notificationContentWarningDescription,
            animateEmojis
        )
        binding.notificationContentWarningDescription.text = emojifiedContentWarning
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
