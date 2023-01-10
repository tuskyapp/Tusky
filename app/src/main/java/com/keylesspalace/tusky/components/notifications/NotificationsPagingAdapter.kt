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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.databinding.ItemStatusNotificationBinding
import com.keylesspalace.tusky.databinding.SimpleListItem1Binding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.SmartLengthInputFilter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import java.util.Date

/** How to present the notification in the UI */
enum class NotificationViewKind {
    /** View as the original status */
    STATUS,

    /** View as the original status, with the interaction type above */
    NOTIFICATION,
    FOLLOW,
    FOLLOW_REQUEST,
    REPORT,
    UNKNOWN;

    companion object {
        fun from(kind: Notification.Type?): NotificationViewKind {
            return when (kind) {
                Notification.Type.MENTION,
                Notification.Type.POLL,
                Notification.Type.UNKNOWN -> STATUS
                Notification.Type.FAVOURITE,
                Notification.Type.REBLOG,
                Notification.Type.STATUS,
                Notification.Type.UPDATE -> NOTIFICATION
                Notification.Type.FOLLOW,
                Notification.Type.SIGN_UP -> FOLLOW
                Notification.Type.FOLLOW_REQUEST -> FOLLOW_REQUEST
                Notification.Type.REPORT -> REPORT
                null -> UNKNOWN
            }
        }
    }
}

interface NotificationActionListener {
    fun onViewAccount(id: String)
    fun onViewThreadForStatus(status: Status)
    fun onViewReport(reportId: String)
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

class NotificationsPagingAdapter(
    diffCallback: DiffUtil.ItemCallback<Notification>,
    private val statusActionListener: StatusActionListener,
    private val notificationActionListener: NotificationActionListener,
    private val statusDisplayOptions: StatusDisplayOptions
) : PagingDataAdapter<Notification, RecyclerView.ViewHolder>(diffCallback) {

    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    /** View holders in this adapter must implement this interface */
    interface ViewHolder {
        /** Bind the data from notification and payloads to the view */
        fun bind(notification: Notification, payloads: List<*>?)
    }

    override fun getItemViewType(position: Int): Int {
        return NotificationViewKind.from(getItem(position)?.type).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (NotificationViewKind.values()[viewType]) {
//            NotificationViewKind.STATUS -> {
//                FollowRequestViewHolder(ItemStatusBinding.inflate(inflater, parent, false))
//            }
            NotificationViewKind.NOTIFICATION -> {
                StatusNotificationViewHolder(
                    ItemStatusNotificationBinding.inflate(inflater, parent, false),
                    statusActionListener,
                    notificationActionListener,
                    statusDisplayOptions,
                    absoluteTimeFormatter
                )
            }
//            NotificationViewKind.FOLLOW -> {
//                FollowViewHolder(ItemFollowBinding.inflate(inflater, parent, false))
//            } NotificationViewKind.FOLLOW_REQUEST -> {
//                FollowRequestViewHolder(ItemFollowRequestBinding.inflate(inflater, parent, false))
//            }
//            NotificationViewKind.REPORT -> {
//                ReportViewHolder(ItemReportNotificationBinding.inflate(inflater, parent, false))
//            }
            else -> {
                FallbackNotificationViewHolder(
                    SimpleListItem1Binding.inflate(inflater, parent, false)
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindViewHolder(holder, position, null)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        bindViewHolder(holder, position, payloads)
    }

    private fun bindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>?
    ) {
        getItem(position)?.let { (holder as ViewHolder).bind(it, payloads) }
    }

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
    private class StatusNotificationViewHolder(
        private val binding: ItemStatusNotificationBinding,
        private val statusActionListener: StatusActionListener,
        private val notificationActionListener: NotificationActionListener,
        private val statusDisplayOptions: StatusDisplayOptions,
        private val absoluteTimeFormatter: AbsoluteTimeFormatter
    ) : ViewHolder, RecyclerView.ViewHolder(binding.root) {
        private val avatarRadius48dp = itemView.context.resources.getDimensionPixelSize(
            R.dimen.avatar_radius_48dp
        )
        private val avatarRadius36dp = itemView.context.resources.getDimensionPixelSize(
            R.dimen.avatar_radius_36dp
        )
        private val avatarRadius24dp = itemView.context.resources.getDimensionPixelSize(
            R.dimen.avatar_radius_24dp
        )

        override fun bind(notification: Notification, payloads: List<*>?) {
            val notificationViewData = notification.toViewData(
                isShowingContent = statusDisplayOptions.showSensitiveMedia ||
                    !(notification.status?.actionableStatus?.sensitive ?: false),
                isExpanded = statusDisplayOptions.openSpoiler,
                isCollapsed = true
            )
            val statusViewData = notificationViewData.statusViewData
            if (payloads.isNullOrEmpty()) {
                // Hide null statuses. Shouldn't happen according to the spec, but some servers
                // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
                if (statusViewData == null) {
                    showNotificationContent(false)
                } else {
                    showNotificationContent(true)
                    val (_, _, account, _, _, _, _, createdAt) = statusViewData.actionable
                    setDisplayName(account.displayName, account.emojis)
                    setUsername(account.username)
                    setCreatedAt(createdAt)
                    if (notificationViewData.type == Notification.Type.STATUS ||
                        notificationViewData.type == Notification.Type.UPDATE
                    ) {
                        setAvatar(account.avatar, account.bot)
                    } else {
                        setAvatars(
                            account.avatar,
                            notificationViewData.account.avatar
                        )
                    }

                    binding.notificationContainer.setOnClickListener {
                        notificationActionListener.onViewThreadForStatus(statusViewData.status)
                    }
                    binding.notificationContent.setOnClickListener {
                        notificationActionListener.onViewThreadForStatus(statusViewData.status)
                    }
                    binding.notificationTopText.setOnClickListener {
                        notificationActionListener.onViewAccount(notificationViewData.account.id)
                    }
                }
                setMessage(notificationViewData, statusActionListener)
            } else {
                for (item in payloads) {
                    if (StatusBaseViewHolder.Key.KEY_CREATED == item && statusViewData != null) {
                        setCreatedAt(
                            statusViewData.status.actionableStatus.createdAt
                        )
                    }
                }
            }
        }

        fun showNotificationContent(show: Boolean) {
            binding.statusNameBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.notificationContentWarningDescription.visibility =
                if (show) View.VISIBLE else View.GONE
            binding.notificationContentWarningButton.visibility =
                if (show) View.VISIBLE else View.GONE
            binding.notificationContent.visibility = if (show) View.VISIBLE else View.GONE
            binding.notificationStatusAvatar.visibility = if (show) View.VISIBLE else View.GONE
            binding.notificationNotificationAvatar.visibility = if (show) View.VISIBLE else View.GONE
        }

        fun setDisplayName(name: String?, emojis: List<Emoji>?) {
            val emojifiedName =
                name!!.emojify(
                    emojis,
                    binding.statusDisplayName,
                    statusDisplayOptions.animateEmojis
                )
            binding.statusDisplayName.text = emojifiedName
        }

        fun setUsername(name: String) {
            val context = binding.statusUsername.context
            val format = context.getString(R.string.post_username_format)
            val usernameText = String.format(format, name)
            binding.statusUsername.text = usernameText
        }

        fun setCreatedAt(createdAt: Date?) {
            if (statusDisplayOptions.useAbsoluteTime) {
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

        fun getIconWithColor(
            context: Context,
            @DrawableRes drawable: Int,
            @ColorRes color: Int
        ): Drawable? {
            val icon = ContextCompat.getDrawable(context, drawable)
            icon?.setColorFilter(context.getColor(color), PorterDuff.Mode.SRC_ATOP)
            return icon
        }

        fun setAvatar(statusAvatarUrl: String?, isBot: Boolean) {
            binding.notificationStatusAvatar.setPaddingRelative(0, 0, 0, 0)
            loadAvatar(
                statusAvatarUrl,
                binding.notificationStatusAvatar,
                avatarRadius48dp,
                statusDisplayOptions.animateAvatars
            )
            if (statusDisplayOptions.showBotOverlay && isBot) {
                binding.notificationNotificationAvatar.visibility = View.VISIBLE
                Glide.with(binding.notificationNotificationAvatar)
                    .load(
                        ContextCompat.getDrawable(
                            binding.notificationNotificationAvatar.context,
                            R.drawable.bot_badge
                        )
                    )
                    .into(binding.notificationNotificationAvatar)
            } else {
                binding.notificationNotificationAvatar.visibility = View.GONE
            }
        }

        fun setAvatars(statusAvatarUrl: String?, notificationAvatarUrl: String?) {
            val padding = Utils.dpToPx(binding.notificationStatusAvatar.context, 12)
            binding.notificationStatusAvatar.setPaddingRelative(0, 0, padding, padding)
            loadAvatar(
                statusAvatarUrl,
                binding.notificationStatusAvatar,
                avatarRadius36dp,
                statusDisplayOptions.animateAvatars
            )
            binding.notificationNotificationAvatar.visibility = View.VISIBLE
            loadAvatar(
                notificationAvatarUrl,
                binding.notificationNotificationAvatar,
                avatarRadius24dp,
                statusDisplayOptions.animateAvatars
            )
        }

        fun setMessage(
            notificationViewData: NotificationViewData.Concrete,
            listener: LinkListener
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
                statusDisplayOptions.animateEmojis
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
                setupContentAndSpoiler(listener, statusViewData)
            }
        }

        private fun setupContentAndSpoiler(
            listener: LinkListener,
            statusViewData: StatusViewData.Concrete
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
                    statusDisplayOptions.animateEmojis
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
                statusDisplayOptions.animateEmojis
            )
            binding.notificationContentWarningDescription.text = emojifiedContentWarning
        }
    }

    /**
     * Notification view holder to use if no other type is appropriate. Should never normally
     * be used, but is useful when migrating code.
     */
    private class FallbackNotificationViewHolder(
        val binding: SimpleListItem1Binding
    ) : ViewHolder, RecyclerView.ViewHolder(binding.root) {
        override fun bind(notification: Notification, payloads: List<*>?) {
            binding.text1.text = notification.status?.content?.parseAsMastodonHtml()
        }
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
