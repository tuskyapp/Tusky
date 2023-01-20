package com.keylesspalace.tusky.components.notifications

import com.keylesspalace.tusky.adapter.StatusViewHolder
import com.keylesspalace.tusky.databinding.ItemStatusBinding
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.viewdata.NotificationViewData

internal class StatusViewHolder(
    binding: ItemStatusBinding,
    private val statusActionListener: StatusActionListener,
    private val accountId: String
) : NotificationsPagingAdapter.ViewHolder, StatusViewHolder(binding.root) {

    override fun bind(
        viewData: NotificationViewData,
        payloads: List<*>?,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val statusViewData = viewData.statusViewData
        if (statusViewData == null) {
            // Hide null statuses. Shouldn't happen according to the spec, but some servers
            // have been seen to do this (https://github.com/tuskyapp/Tusky/issues/2252)
            showStatusContent(false)
        } else {
            if (payloads.isNullOrEmpty()) {
                showStatusContent(true)
            }
            setupWithStatus(
                statusViewData,
                statusActionListener,
                statusDisplayOptions,
                payloads?.firstOrNull()
            )
        }
        if (viewData.type == Notification.Type.POLL) {
            setPollInfo(accountId == viewData.account.id)
        } else {
            hideStatusInfo()
        }
    }
}
