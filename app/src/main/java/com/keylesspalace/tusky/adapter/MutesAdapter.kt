package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import java.util.HashMap

/**
 * Displays a list of muted accounts with mute/unmute account and mute/unmute notifications
 * buttons.
 * */
class MutesAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean
) : AccountAdapter<MutesAdapter.MutedUserViewHolder>(
    accountActionListener,
    animateAvatar,
    animateEmojis
) {
    private val mutingNotificationsMap = HashMap<String, Boolean>()

    override fun createAccountViewHolder(parent: ViewGroup): MutedUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_muted_user, parent, false)
        return MutedUserViewHolder(view)
    }

    override fun onBindAccountViewHolder(viewHolder: MutedUserViewHolder, position: Int) {
        val account = accountList[position]
        viewHolder.setupWithAccount(
            account,
            mutingNotificationsMap[account.id],
            animateAvatar,
            animateEmojis
        )
        viewHolder.setupActionListener(accountActionListener)
    }

    fun updateMutingNotifications(id: String, mutingNotifications: Boolean, position: Int) {
        mutingNotificationsMap[id] = mutingNotifications
        notifyItemChanged(position)
    }

    fun updateMutingNotificationsMap(newMutingNotificationsMap: HashMap<String, Boolean>?) {
        mutingNotificationsMap.putAll(newMutingNotificationsMap!!)
        notifyDataSetChanged()
    }

    class MutedUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.muted_user_avatar)
        private val username: TextView = itemView.findViewById(R.id.muted_user_username)
        private val displayName: TextView = itemView.findViewById(R.id.muted_user_display_name)
        private val unmute: ImageButton = itemView.findViewById(R.id.muted_user_unmute)
        private val muteNotifications: ImageButton =
            itemView.findViewById(R.id.muted_user_mute_notifications)

        private var id: String? = null
        private var notifications = false

        fun setupWithAccount(
            account: TimelineAccount,
            mutingNotifications: Boolean?,
            animateAvatar: Boolean,
            animateEmojis: Boolean
        ) {
            id = account.id
            val emojifiedName = account.name.emojify(account.emojis, displayName, animateEmojis)
            displayName.text = emojifiedName
            val format = username.context.getString(R.string.post_username_format)
            val formattedUsername = String.format(format, account.username)
            username.text = formattedUsername
            val avatarRadius = avatar.context.resources
                .getDimensionPixelSize(R.dimen.avatar_radius_48dp)
            loadAvatar(account.avatar, avatar, avatarRadius, animateAvatar)
            val unmuteString =
                unmute.context.getString(R.string.action_unmute_desc, formattedUsername)
            unmute.contentDescription = unmuteString
            ViewCompat.setTooltipText(unmute, unmuteString)
            if (mutingNotifications == null) {
                muteNotifications.isEnabled = false
                notifications = true
            } else {
                muteNotifications.isEnabled = true
                notifications = mutingNotifications
            }
            if (notifications) {
                muteNotifications.setImageResource(R.drawable.ic_notifications_24dp)
                val unmuteNotificationsString = muteNotifications.context
                    .getString(R.string.action_unmute_notifications_desc, formattedUsername)
                muteNotifications.contentDescription = unmuteNotificationsString
                ViewCompat.setTooltipText(muteNotifications, unmuteNotificationsString)
            } else {
                muteNotifications.setImageResource(R.drawable.ic_notifications_off_24dp)
                val muteNotificationsString = muteNotifications.context
                    .getString(R.string.action_mute_notifications_desc, formattedUsername)
                muteNotifications.contentDescription = muteNotificationsString
                ViewCompat.setTooltipText(muteNotifications, muteNotificationsString)
            }
        }

        fun setupActionListener(listener: AccountActionListener) {
            unmute.setOnClickListener {
                listener.onMute(
                    false,
                    id,
                    bindingAdapterPosition,
                    false
                )
            }
            muteNotifications.setOnClickListener {
                listener.onMute(
                    true,
                    id,
                    bindingAdapterPosition,
                    !notifications
                )
            }
            itemView.setOnClickListener { listener.onViewAccount(id) }
        }
    }
}
