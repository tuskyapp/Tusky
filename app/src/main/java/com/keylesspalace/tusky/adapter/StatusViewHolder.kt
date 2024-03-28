/* Copyright 2017 Andrew Dawson
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

import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import at.connyduck.sparkbutton.helpers.Utils
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.SmartLengthInputFilter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.formatNumber
import com.keylesspalace.tusky.util.unicodeWrap
import com.keylesspalace.tusky.viewdata.StatusViewData

class StatusViewHolder(itemView: View) : StatusBaseViewHolder(itemView) {
    private val statusInfo: TextView = itemView.findViewById(R.id.status_info)
    private val contentCollapseButton: Button = itemView.findViewById(R.id.button_toggle_content)
    private val favouritedCountLabel: TextView = itemView.findViewById(R.id.status_favourites_count)
    private val reblogsCountLabel: TextView = itemView.findViewById(R.id.status_insets)

    override fun setupWithStatus(
        status: StatusViewData.Concrete,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?
    ) {
        if (payloads == null) {
            val sensitive = status.actionable.spoilerText.isNotEmpty()
            val expanded = status.isExpanded

            setupCollapsedState(sensitive, expanded, status, listener)

            val reblogging = status.rebloggingStatus
            if (reblogging == null || status.filterAction == Filter.Action.WARN) {
                hideStatusInfo()
            } else {
                val rebloggedByDisplayName = reblogging.account.name
                setRebloggedByDisplayName(
                    rebloggedByDisplayName,
                    reblogging.account.emojis,
                    statusDisplayOptions
                )
                statusInfo.setOnClickListener { v: View? ->
                    listener.onOpenReblog(
                        bindingAdapterPosition
                    )
                }
            }
        }

        reblogsCountLabel.isInvisible = !statusDisplayOptions.showStatsInline
        favouritedCountLabel.isInvisible = !statusDisplayOptions.showStatsInline
        setFavouritedCount(status.actionable.favouritesCount)
        setReblogsCount(status.actionable.reblogsCount)

        super.setupWithStatus(status, listener, statusDisplayOptions, payloads)
    }

    private fun setRebloggedByDisplayName(
        name: CharSequence,
        accountEmoji: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions
    ) {
        val context = statusInfo.context
        val wrappedName: CharSequence = name.unicodeWrap()
        val boostedText: CharSequence = context.getString(R.string.post_boosted_format, wrappedName)
        val emojifiedText = boostedText.emojify(
            accountEmoji,
            statusInfo,
            statusDisplayOptions.animateEmojis
        )
        statusInfo.text = emojifiedText
        statusInfo.isVisible = true
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    fun setPollInfo(ownPoll: Boolean) {
        statusInfo.setText(if (ownPoll) R.string.poll_ended_created else R.string.poll_ended_voted)
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
        statusInfo.compoundDrawablePadding =
            Utils.dpToPx(statusInfo.context, 10)
        statusInfo.setPaddingRelative(Utils.dpToPx(statusInfo.context, 28), 0, 0, 0)
        statusInfo.isVisible = true
    }

    protected fun setReblogsCount(reblogsCount: Int) {
        reblogsCountLabel.text = formatNumber(reblogsCount.toLong(), 1000)
    }

    protected fun setFavouritedCount(favouritedCount: Int) {
        favouritedCountLabel.text = formatNumber(favouritedCount.toLong(), 1000)
    }

    fun hideStatusInfo() {
        statusInfo.isVisible = false
    }

    private fun setupCollapsedState(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData.Concrete,
        listener: StatusActionListener
    ) {
        /* input filter for TextViews have to be set before text */
        if (status.isCollapsible && (!sensitive || expanded)) {
            contentCollapseButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onContentCollapsedChange(
                        !status.isCollapsed,
                        position
                    )
                }
            }

            contentCollapseButton.isVisible = true
            if (status.isCollapsed) {
                contentCollapseButton.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                contentCollapseButton.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            contentCollapseButton.isVisible = false
            content.filters = NO_INPUT_FILTER
        }
    }

    override fun showStatusContent(show: Boolean) {
        super.showStatusContent(show)
        contentCollapseButton.isVisible = show
    }

    override fun toggleExpandedState(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData.Concrete,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener
    ) {
        setupCollapsedState(sensitive, expanded, status, listener)

        super.toggleExpandedState(sensitive, expanded, status, statusDisplayOptions, listener)
    }

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
