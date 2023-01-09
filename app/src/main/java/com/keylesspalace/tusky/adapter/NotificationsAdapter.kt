/* Copyright 2021 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>. */
package com.keylesspalace.tusky.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemFollowRequestBinding
import com.keylesspalace.tusky.databinding.ItemReportNotificationBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.CardViewMode
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

class NotificationsAdapter(
    private val accountId: String,
    private val dataSource: AdapterDataSource<NotificationViewData>,
    private var statusDisplayOptions: StatusDisplayOptions,
    private val statusListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val accountActionListener: AccountActionListener
) : RecyclerView.Adapter<Any?>() {
    interface AdapterDataSource<T> {
        val itemCount: Int
        fun getItemAt(pos: Int): T
    }

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STATUS -> {
                val view = inflater
                    .inflate(R.layout.item_status, parent, false)
                StatusViewHolder(view)
            }
            VIEW_TYPE_STATUS_NOTIFICATION -> {
                val view = inflater
                    .inflate(R.layout.item_status_notification, parent, false)
                StatusNotificationViewHolder(view, statusDisplayOptions, absoluteTimeFormatter)
            }
            VIEW_TYPE_FOLLOW -> {
                val view = inflater
                    .inflate(R.layout.item_follow, parent, false)
                FollowViewHolder(view, statusDisplayOptions)
            }
            VIEW_TYPE_FOLLOW_REQUEST -> {
                val binding = ItemFollowRequestBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                FollowRequestViewHolder(binding, true)
            }
            VIEW_TYPE_PLACEHOLDER -> {
                val view = inflater
                    .inflate(R.layout.item_status_placeholder, parent, false)
                PlaceholderViewHolder(view)
            }
            VIEW_TYPE_REPORT -> {
                val binding = ItemReportNotificationBinding.inflate(
                    inflater,
                    parent,
                    false
                )
                ReportNotificationViewHolder(binding)
            }
            VIEW_TYPE_UNKNOWN -> {
                val view = View(parent.context)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dpToPx(parent.context, 24)
                )
                object : RecyclerView.ViewHolder(view) {}
            }
            else -> {
                val view = View(parent.context)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Utils.dpToPx(parent.context, 24)
                )
                object : RecyclerView.ViewHolder(view) {}
            }
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(viewHolder, position, null)
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>
    ) {
        bindViewHolder(viewHolder, position, payloads)
    }

    private fun bindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?
    ) {
        val payloadForHolder = if (payloads != null && !payloads.isEmpty()) payloads[0] else null
        if (position < dataSource.itemCount) {
            val notification = dataSource.getItemAt(position)
            if (notification is NotificationViewData.Placeholder) {
                if (payloadForHolder == null) {
                    val holder = viewHolder as PlaceholderViewHolder
                    holder.setup(statusListener, notification.isLoading)
                }
                return
            }
            val concreteNotification = notification as NotificationViewData.Concrete
            when (viewHolder.itemViewType) {
                VIEW_TYPE_STATUS -> {
                    val holder = viewHolder as StatusViewHolder
                    val status = concreteNotification.statusViewData
                    if (status == null) {
                        /* in some very rare cases servers sends null status even though they should not,
                         * we have to handle it somehow */
                        holder.showStatusContent(false)
                    } else {
                        if (payloads == null) {
                            holder.showStatusContent(true)
                        }
                        holder.setupWithStatus(
                            status,
                            statusListener,
                            statusDisplayOptions,
                            payloadForHolder
                        )
                    }
                    if (concreteNotification.type === Notification.Type.POLL) {
                        holder.setPollInfo(accountId == concreteNotification.account.id)
                    } else {
                        holder.hideStatusInfo()
                    }
                }
                VIEW_TYPE_STATUS_NOTIFICATION -> {
                    val holder = viewHolder as StatusNotificationViewHolder
                    val statusViewData = concreteNotification.statusViewData
                    if (payloadForHolder == null) {
                        if (statusViewData == null) {
                            /* in some very rare cases servers sends null status even though they should not,
                             * we have to handle it somehow */
                            holder.showNotificationContent(false)
                        } else {
                            holder.showNotificationContent(true)
                            val (_, _, account, _, _, _, _, createdAt) = statusViewData.actionable
                            holder.setDisplayName(account.displayName, account.emojis)
                            holder.setUsername(account.username)
                            holder.setCreatedAt(createdAt)
                            if (concreteNotification.type === Notification.Type.STATUS ||
                                concreteNotification.type === Notification.Type.UPDATE
                            ) {
                                holder.setAvatar(account.avatar, account.bot)
                            } else {
                                holder.setAvatars(
                                    account.avatar,
                                    concreteNotification.account.avatar
                                )
                            }
                        }
                        holder.setMessage(concreteNotification, statusListener)
                        holder.setupButtons(
                            notificationActionListener,
                            concreteNotification.account.id,
                            concreteNotification.id
                        )
                    } else {
                        if (payloadForHolder is List<*>) for (item in payloadForHolder) {
                            if (StatusBaseViewHolder.Key.KEY_CREATED == item && statusViewData != null) {
                                holder.setCreatedAt(statusViewData.status.actionableStatus.createdAt)
                            }
                        }
                    }
                }
                VIEW_TYPE_FOLLOW -> {
                    if (payloadForHolder == null) {
                        val holder = viewHolder as FollowViewHolder
                        holder.setMessage(
                            concreteNotification.account,
                            concreteNotification.type === Notification.Type.SIGN_UP
                        )
                        holder.setupButtons(
                            notificationActionListener,
                            concreteNotification.account.id
                        )
                    }
                }
                VIEW_TYPE_FOLLOW_REQUEST -> {
                    if (payloadForHolder == null) {
                        val holder = viewHolder as FollowRequestViewHolder
                        holder.setupWithAccount(
                            concreteNotification.account,
                            statusDisplayOptions.animateAvatars,
                            statusDisplayOptions.animateEmojis,
                            statusDisplayOptions.showBotOverlay
                        )
                        holder.setupActionListener(
                            accountActionListener,
                            concreteNotification.account.id
                        )
                    }
                }
                VIEW_TYPE_REPORT -> {
                    if (payloadForHolder == null) {
                        val holder = viewHolder as ReportNotificationViewHolder
                        holder.setupWithReport(
                            concreteNotification.account,
                            concreteNotification.report!!,
                            statusDisplayOptions.animateAvatars,
                            statusDisplayOptions.animateEmojis
                        )
                        holder.setupActionListener(
                            notificationActionListener,
                            concreteNotification.report!!.targetAccount.id,
                            concreteNotification.account.id,
                            concreteNotification.report!!.id
                        )
                    }
                }
                else -> {}
            }
        }
    }

    override fun getItemCount(): Int {
        return dataSource.itemCount
    }

    var isMediaPreviewEnabled: Boolean
        get() = statusDisplayOptions.mediaPreviewEnabled
        set(mediaPreviewEnabled) {
            statusDisplayOptions = statusDisplayOptions.copy(
                statusDisplayOptions.animateAvatars,
                mediaPreviewEnabled,
                statusDisplayOptions.useAbsoluteTime,
                statusDisplayOptions.showBotOverlay,
                statusDisplayOptions.useBlurhash,
                CardViewMode.NONE,
                statusDisplayOptions.confirmReblogs,
                statusDisplayOptions.confirmFavourites,
                statusDisplayOptions.hideStats,
                statusDisplayOptions.animateEmojis
            )
        }

    override fun getItemViewType(position: Int): Int {
        val notification = dataSource.getItemAt(position)
        return if (notification is NotificationViewData.Concrete) {
            when (notification.type) {
                Notification.Type.MENTION, Notification.Type.POLL -> {
                    VIEW_TYPE_STATUS
                }
                Notification.Type.STATUS, Notification.Type.FAVOURITE, Notification.Type.REBLOG, Notification.Type.UPDATE -> {
                    VIEW_TYPE_STATUS_NOTIFICATION
                }
                Notification.Type.FOLLOW, Notification.Type.SIGN_UP -> {
                    VIEW_TYPE_FOLLOW
                }
                Notification.Type.FOLLOW_REQUEST -> {
                    VIEW_TYPE_FOLLOW_REQUEST
                }
                Notification.Type.REPORT -> {
                    VIEW_TYPE_REPORT
                }
                else -> {
                    VIEW_TYPE_UNKNOWN
                }
            }
        } else if (notification is NotificationViewData.Placeholder) {
            VIEW_TYPE_PLACEHOLDER
        } else {
            throw AssertionError("Unknown notification type")
        }
    }

    interface NotificationActionListener {
        fun onViewAccount(id: String?)
        fun onViewStatusForNotificationId(notificationId: String?)
        fun onViewReport(reportId: String?)
        fun onExpandedChange(expanded: Boolean, position: Int)

        /**
         * Called when the status [android.widget.ToggleButton] responsible for collapsing long
         * status content is interacted with.
         *
         * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
         * @param position    The position of the status in the list.
         */
        fun onNotificationContentCollapsedChange(isCollapsed: Boolean, position: Int)
    }

    private class FollowViewHolder internal constructor(
        itemView: View,
        statusDisplayOptions: StatusDisplayOptions
    ) : RecyclerView.ViewHolder(itemView) {
        private val message: TextView
        private val usernameView: TextView
        private val displayNameView: TextView
        private val avatar: ImageView
        private val statusDisplayOptions: StatusDisplayOptions

        init {
            message = itemView.findViewById(R.id.notification_text)
            usernameView = itemView.findViewById(R.id.notification_username)
            displayNameView = itemView.findViewById(R.id.notification_display_name)
            avatar = itemView.findViewById(R.id.notification_avatar)
            this.statusDisplayOptions = statusDisplayOptions
        }

        fun setMessage(account: TimelineAccount, isSignUp: Boolean) {
            val context = message.context
            val format =
                context.getString(if (isSignUp) R.string.notification_sign_up_format else R.string.notification_follow_format)
            val wrappedDisplayName = account.name.unicodeWrap()
            val wholeMessage = String.format(format, wrappedDisplayName)
            val emojifiedMessage =
                wholeMessage.emojify(account.emojis, message, statusDisplayOptions.animateEmojis)
            message.text = emojifiedMessage
            val username = context.getString(R.string.post_username_format, account.username)
            usernameView.text = username
            val emojifiedDisplayName = wrappedDisplayName.emojify(
                account.emojis,
                usernameView,
                statusDisplayOptions.animateEmojis
            )
            displayNameView.text = emojifiedDisplayName
            val avatarRadius = avatar.context.resources
                .getDimensionPixelSize(R.dimen.avatar_radius_42dp)
            loadAvatar(
                account.avatar, avatar, avatarRadius,
                statusDisplayOptions.animateAvatars
            )
        }

        fun setupButtons(listener: NotificationActionListener, accountId: String?) {
            itemView.setOnClickListener { v: View? -> listener.onViewAccount(accountId) }
        }
    }

    private class StatusNotificationViewHolder internal constructor(
        itemView: View,
        statusDisplayOptions: StatusDisplayOptions,
        absoluteTimeFormatter: AbsoluteTimeFormatter
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        private val message: TextView
        private val statusNameBar: View
        private val displayName: TextView
        private val username: TextView
        private val timestampInfo: TextView
        private val statusContent: TextView
        private val statusAvatar: ImageView
        private val notificationAvatar: ImageView
        private val contentWarningDescriptionTextView: TextView
        private val contentWarningButton: Button
        private val contentCollapseButton // TODO: This code SHOULD be based on StatusBaseViewHolder
                : Button
        private val statusDisplayOptions: StatusDisplayOptions
        private val absoluteTimeFormatter: AbsoluteTimeFormatter
        private var accountId: String? = null
        private var notificationId: String? = null
        private var notificationActionListener: NotificationActionListener? = null
        private var statusViewData: StatusViewData.Concrete? = null
        private val avatarRadius48dp: Int
        private val avatarRadius36dp: Int
        private val avatarRadius24dp: Int

        init {
            message = itemView.findViewById(R.id.notification_top_text)
            statusNameBar = itemView.findViewById(R.id.status_name_bar)
            displayName = itemView.findViewById(R.id.status_display_name)
            username = itemView.findViewById(R.id.status_username)
            timestampInfo = itemView.findViewById(R.id.status_meta_info)
            statusContent = itemView.findViewById(R.id.notification_content)
            statusAvatar = itemView.findViewById(R.id.notification_status_avatar)
            notificationAvatar = itemView.findViewById(R.id.notification_notification_avatar)
            contentWarningDescriptionTextView =
                itemView.findViewById(R.id.notification_content_warning_description)
            contentWarningButton = itemView.findViewById(R.id.notification_content_warning_button)
            contentCollapseButton = itemView.findViewById(R.id.button_toggle_notification_content)
            this.statusDisplayOptions = statusDisplayOptions
            this.absoluteTimeFormatter = absoluteTimeFormatter
            val darkerFilter = Color.rgb(123, 123, 123)
            statusAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY)
            notificationAvatar.setColorFilter(darkerFilter, PorterDuff.Mode.MULTIPLY)
            itemView.setOnClickListener(this)
            message.setOnClickListener(this)
            statusContent.setOnClickListener(this)
            avatarRadius48dp =
                itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
            avatarRadius36dp =
                itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp)
            avatarRadius24dp =
                itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_24dp)
        }

        private fun showNotificationContent(show: Boolean) {
            statusNameBar.visibility = if (show) View.VISIBLE else View.GONE
            contentWarningDescriptionTextView.visibility =
                if (show) View.VISIBLE else View.GONE
            contentWarningButton.visibility =
                if (show) View.VISIBLE else View.GONE
            statusContent.visibility = if (show) View.VISIBLE else View.GONE
            statusAvatar.visibility = if (show) View.VISIBLE else View.GONE
            notificationAvatar.visibility = if (show) View.VISIBLE else View.GONE
        }

        private fun setDisplayName(name: String?, emojis: List<Emoji>?) {
            val emojifiedName =
                name!!.emojify(emojis, displayName, statusDisplayOptions.animateEmojis)
            displayName.text = emojifiedName
        }

        private fun setUsername(name: String) {
            val context = username.context
            val format = context.getString(R.string.post_username_format)
            val usernameText = String.format(format, name)
            username.text = usernameText
        }

        fun setCreatedAt(createdAt: Date?) {
            if (statusDisplayOptions.useAbsoluteTime) {
                timestampInfo.text = absoluteTimeFormatter.format(createdAt, true)
            } else {
                // This is the visible timestampInfo.
                val readout: String
                /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
                 * as 17 meters instead of minutes. */
                val readoutAloud: CharSequence
                if (createdAt != null) {
                    val then = createdAt.time
                    val now = Date().time
                    readout = getRelativeTimeSpanString(timestampInfo.context, then, now)
                    readoutAloud = DateUtils.getRelativeTimeSpanString(
                        then, now,
                        DateUtils.SECOND_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                } else {
                    // unknown minutes~
                    readout = "?m"
                    readoutAloud = "? minutes"
                }
                timestampInfo.text = readout
                timestampInfo.contentDescription = readoutAloud
            }
        }

        fun getIconWithColor(
            context: Context,
            @DrawableRes drawable: Int,
            @ColorRes color: Int
        ): Drawable? {
            val icon = ContextCompat.getDrawable(context, drawable)
            icon?.setColorFilter(context.getColor(color), PorterDuff.Mode.SRC_ATOP)
            return icon
        }

        fun setMessage(
            notificationViewData: NotificationViewData.Concrete,
            listener: LinkListener
        ) {
            statusViewData = notificationViewData.statusViewData
            val displayName = notificationViewData.account.name.unicodeWrap()
            val type = notificationViewData.type
            val context = message.context
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
            message.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
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
                message,
                statusDisplayOptions.animateEmojis
            )
            message.text = emojifiedText
            if (statusViewData != null) {
                val hasSpoiler = !TextUtils.isEmpty(statusViewData!!.status.spoilerText)
                contentWarningDescriptionTextView.visibility =
                    if (hasSpoiler) View.VISIBLE else View.GONE
                contentWarningButton.visibility = if (hasSpoiler) View.VISIBLE else View.GONE
                if (statusViewData!!.isExpanded) {
                    contentWarningButton.setText(R.string.post_content_warning_show_less)
                } else {
                    contentWarningButton.setText(R.string.post_content_warning_show_more)
                }
                contentWarningButton.setOnClickListener { view: View? ->
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        notificationActionListener!!.onExpandedChange(
                            !statusViewData!!.isExpanded,
                            bindingAdapterPosition
                        )
                    }
                    statusContent.visibility =
                        if (statusViewData!!.isExpanded) View.GONE else View.VISIBLE
                }
                setupContentAndSpoiler(listener)
            }
        }

        fun setupButtons(
            listener: NotificationActionListener?, accountId: String?,
            notificationId: String?
        ) {
            notificationActionListener = listener
            this.accountId = accountId
            this.notificationId = notificationId
        }

        fun setAvatar(statusAvatarUrl: String?, isBot: Boolean) {
            statusAvatar.setPaddingRelative(0, 0, 0, 0)
            loadAvatar(
                statusAvatarUrl,
                statusAvatar, avatarRadius48dp, statusDisplayOptions.animateAvatars
            )
            if (statusDisplayOptions.showBotOverlay && isBot) {
                notificationAvatar.visibility = View.VISIBLE
                Glide.with(notificationAvatar)
                    .load(
                        ContextCompat.getDrawable(
                            notificationAvatar.context,
                            R.drawable.bot_badge
                        )
                    )
                    .into(notificationAvatar)
            } else {
                notificationAvatar.visibility = View.GONE
            }
        }

        fun setAvatars(statusAvatarUrl: String?, notificationAvatarUrl: String?) {
            val padding = Utils.dpToPx(statusAvatar.context, 12)
            statusAvatar.setPaddingRelative(0, 0, padding, padding)
            loadAvatar(
                statusAvatarUrl,
                statusAvatar, avatarRadius36dp, statusDisplayOptions.animateAvatars
            )
            notificationAvatar.visibility = View.VISIBLE
            loadAvatar(
                notificationAvatarUrl, notificationAvatar,
                avatarRadius24dp, statusDisplayOptions.animateAvatars
            )
        }

        override fun onClick(v: View) {
            when (v.id) {
                R.id.notification_container, R.id.notification_content -> if (notificationActionListener != null) notificationActionListener!!.onViewStatusForNotificationId(
                    notificationId
                )
                R.id.notification_top_text -> if (notificationActionListener != null) notificationActionListener!!.onViewAccount(
                    accountId
                )
            }
        }

        private fun setupContentAndSpoiler(listener: LinkListener) {
            val shouldShowContentIfSpoiler = statusViewData!!.isExpanded
            val hasSpoiler = !TextUtils.isEmpty(statusViewData!!.status.spoilerText)
            if (!shouldShowContentIfSpoiler && hasSpoiler) {
                statusContent.visibility = View.GONE
            } else {
                statusContent.visibility = View.VISIBLE
            }
            val content = statusViewData!!.content
            val emojis = statusViewData!!.actionable.emojis
            if (statusViewData!!.isCollapsible && (statusViewData!!.isExpanded || !hasSpoiler)) {
                contentCollapseButton.setOnClickListener { view: View? ->
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION && notificationActionListener != null) {
                        notificationActionListener!!.onNotificationContentCollapsedChange(
                            !statusViewData!!.isCollapsed,
                            position
                        )
                    }
                }
                contentCollapseButton.visibility = View.VISIBLE
                if (statusViewData!!.isCollapsed) {
                    contentCollapseButton.setText(R.string.post_content_warning_show_more)
                    statusContent.filters = COLLAPSE_INPUT_FILTER
                } else {
                    contentCollapseButton.setText(R.string.post_content_warning_show_less)
                    statusContent.filters = NO_INPUT_FILTER
                }
            } else {
                contentCollapseButton.visibility = View.GONE
                statusContent.filters = NO_INPUT_FILTER
            }
            val emojifiedText =
                content.emojify(emojis, statusContent, statusDisplayOptions.animateEmojis)
            setClickableText(
                statusContent,
                emojifiedText,
                statusViewData!!.actionable.mentions,
                statusViewData!!.actionable.tags,
                listener
            )
            val emojifiedContentWarning: CharSequence
            emojifiedContentWarning = if (statusViewData!!.spoilerText != null) {
                statusViewData!!.spoilerText.emojify(
                    statusViewData!!.actionable.emojis,
                    contentWarningDescriptionTextView,
                    statusDisplayOptions.animateEmojis
                )
            } else {
                ""
            }
            contentWarningDescriptionTextView.text = emojifiedContentWarning
        }
    }

    companion object {
        private const val VIEW_TYPE_STATUS = 0
        private const val VIEW_TYPE_STATUS_NOTIFICATION = 1
        private const val VIEW_TYPE_FOLLOW = 2
        private const val VIEW_TYPE_FOLLOW_REQUEST = 3
        private const val VIEW_TYPE_PLACEHOLDER = 4
        private const val VIEW_TYPE_REPORT = 5
        private const val VIEW_TYPE_UNKNOWN = 6
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}