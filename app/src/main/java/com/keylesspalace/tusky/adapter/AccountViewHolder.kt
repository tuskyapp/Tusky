package com.keylesspalace.tusky.adapter

import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAccountBinding
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.interfaces.AccountActionListener
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.visible

class AccountViewHolder(
    private val binding: ItemAccountBinding
) : RecyclerView.ViewHolder(binding.root) {
    private lateinit var accountId: String

    fun setupWithAccount(
        account: TimelineAccount,
        animateAvatar: Boolean,
        animateEmojis: Boolean,
        showBotOverlay: Boolean
    ) {
        accountId = account.id

        binding.accountUsername.text = binding.accountUsername.context.getString(
            R.string.post_username_format,
            account.username
        )

        val emojifiedName = account.name.emojify(
            account.emojis,
            binding.accountDisplayName,
            animateEmojis
        )
        binding.accountDisplayName.text = emojifiedName

        val avatarRadius = binding.accountAvatar.context.resources
            .getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        loadAvatar(account.avatar, binding.accountAvatar, avatarRadius, animateAvatar)

        binding.accountBotBadge.visible(showBotOverlay && account.bot)
    }

    fun setupActionListener(listener: AccountActionListener) {
        itemView.setOnClickListener { listener.onViewAccount(accountId) }
    }

    fun setupLinkListener(listener: LinkListener) {
        itemView.setOnClickListener {
            listener.onViewAccount(
                accountId
            )
        }
    }
}
