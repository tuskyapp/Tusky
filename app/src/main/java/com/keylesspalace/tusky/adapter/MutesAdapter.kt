package com.keylesspalace.tusky.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemMutedUserBinding
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.util.BindingHolder
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar

/**
 * Displays a list of muted accounts with mute/unmute account and mute/unmute notifications
 * buttons.
 * */
class MutesAdapter(
    accountActionListener: AccountActionListener,
    animateAvatar: Boolean,
    animateEmojis: Boolean,
    showBotOverlay: Boolean
) : AccountAdapter<BindingHolder<ItemMutedUserBinding>>(
    accountActionListener,
    animateAvatar,
    animateEmojis,
    showBotOverlay
) {
    private val mutingNotificationsMap = HashMap<String, Boolean>()

    override fun createAccountViewHolder(parent: ViewGroup): BindingHolder<ItemMutedUserBinding> {
        val binding = ItemMutedUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindAccountViewHolder(viewHolder: BindingHolder<ItemMutedUserBinding>, position: Int) {
        val account = accountList[position]
        val binding = viewHolder.binding
        val context = binding.root.context

        val mutingNotifications = mutingNotificationsMap[account.id]

        val emojifiedName = account.name.emojify(account.emojis, binding.mutedUserDisplayName, animateEmojis)
        binding.mutedUserDisplayName.text = emojifiedName

        val formattedUsername = context.getString(R.string.post_username_format, account.username)
        binding.mutedUserUsername.text = formattedUsername

        val avatarRadius = context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.mutedUserAvatar, avatarRadius, animateAvatar)

        val unmuteString = context.getString(R.string.action_unmute_desc, formattedUsername)
        binding.mutedUserUnmute.contentDescription = unmuteString
        ViewCompat.setTooltipText(binding.mutedUserUnmute, unmuteString)

        binding.mutedUserMuteNotifications.setOnCheckedChangeListener(null)

        binding.mutedUserMuteNotifications.isChecked = if (mutingNotifications == null) {
            binding.mutedUserMuteNotifications.isEnabled = false
            true
        } else {
            binding.mutedUserMuteNotifications.isEnabled = true
            mutingNotifications
        }

        binding.mutedUserUnmute.setOnClickListener {
            accountActionListener.onMute(
                false,
                account.id,
                viewHolder.bindingAdapterPosition,
                false
            )
        }
        binding.mutedUserMuteNotifications.setOnCheckedChangeListener { _, isChecked ->
            accountActionListener.onMute(
                true,
                account.id,
                viewHolder.bindingAdapterPosition,
                isChecked
            )
        }
        binding.root.setOnClickListener { accountActionListener.onViewAccount(account.id) }
    }

    fun updateMutingNotifications(id: String, mutingNotifications: Boolean, position: Int) {
        mutingNotificationsMap[id] = mutingNotifications
        notifyItemChanged(position)
    }

    fun updateMutingNotificationsMap(newMutingNotificationsMap: HashMap<String, Boolean>) {
        mutingNotificationsMap.putAll(newMutingNotificationsMap)
        notifyDataSetChanged()
    }
}
