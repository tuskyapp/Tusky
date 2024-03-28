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
package com.keylesspalace.tusky.components.conversation

import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.SmartLengthInputFilter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.loadAvatar

class ConversationViewHolder(
    itemView: View,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val listener: StatusActionListener
) : StatusBaseViewHolder(itemView) {
    private val conversationNameTextView: TextView = itemView.findViewById(R.id.conversation_name)
    private val contentCollapseButton: Button = itemView.findViewById(R.id.button_toggle_content)
    private val avatars = arrayOf(
        avatar,
        itemView.findViewById(R.id.status_avatar_1),
        itemView.findViewById(R.id.status_avatar_2)
    )

    fun setupWithConversation(
        conversation: ConversationViewData,
        payloads: Any?
    ) {
        val statusViewData = conversation.lastStatus
        val status = statusViewData.status

        if (payloads == null) {
            val account = status.account

            setupCollapsedState(
                statusViewData.isCollapsible,
                statusViewData.isCollapsed,
                statusViewData.isExpanded,
                status.spoilerText,
                listener
            )

            setDisplayName(account.displayName!!, account.emojis, statusDisplayOptions)
            setUsername(account.username)
            setMetaData(statusViewData, statusDisplayOptions, listener)
            setIsReply(status.inReplyToId != null)
            setFavourited(status.favourited)
            setBookmarked(status.bookmarked)
            val attachments = status.attachments
            val sensitive = status.sensitive
            if (statusDisplayOptions.mediaPreviewEnabled && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(
                    attachments,
                    sensitive,
                    listener,
                    statusViewData.isShowingContent,
                    statusDisplayOptions.useBlurhash
                )

                if (attachments.isEmpty()) {
                    hideSensitiveMediaWarning()
                }
                // Hide the unused label.
                for (mediaLabel in mediaLabels) {
                    mediaLabel.isVisible = false
                }
            } else {
                setMediaLabel(attachments, sensitive, listener, statusViewData.isShowingContent)
                // Hide all unused views.
                mediaPreview.isVisible = false
                hideSensitiveMediaWarning()
            }

            setupButtons(
                listener,
                account.id,
                statusViewData.content.toString(),
                statusDisplayOptions
            )

            setSpoilerAndContent(statusViewData, statusDisplayOptions, listener)

            setConversationName(conversation.accounts)

            setAvatars(conversation.accounts)
        } else {
            if (payloads is List<*>) {
                for (item in payloads) {
                    if (Key.KEY_CREATED == item) {
                        setMetaData(statusViewData, statusDisplayOptions, listener)
                    }
                }
            }
        }
    }

    private fun setConversationName(accounts: List<ConversationAccountEntity>) {
        val context = conversationNameTextView.context
        conversationNameTextView.text = when (accounts.size) {
            1 -> context.getString(R.string.conversation_1_recipients, accounts[0].username)
            2 -> context.getString(
                R.string.conversation_2_recipients,
                accounts[0].username,
                accounts[1].username
            )
            else -> context.getString(
                R.string.conversation_more_recipients,
                accounts[0].username,
                accounts[1].username,
                accounts.size - 2
            )
        }
    }

    private fun setAvatars(accounts: List<ConversationAccountEntity>) {
        for (i in avatars.indices) {
            val avatarView = avatars[i]
            avatarView.isVisible = if (i < accounts.size) {
                loadAvatar(
                    accounts[i].avatar,
                    avatarView,
                    avatarRadius48dp,
                    statusDisplayOptions.animateAvatars,
                    null
                )
                true
            } else {
                false
            }
        }
    }

    private fun setupCollapsedState(
        collapsible: Boolean,
        collapsed: Boolean,
        expanded: Boolean,
        spoilerText: String,
        listener: StatusActionListener
    ) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || spoilerText.isEmpty())) {
            contentCollapseButton.setOnClickListener { _ ->
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onContentCollapsedChange(!collapsed, position)
                }
            }

            contentCollapseButton.isVisible = true
            if (collapsed) {
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

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}
