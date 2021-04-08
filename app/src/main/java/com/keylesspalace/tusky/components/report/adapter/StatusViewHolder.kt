/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report.adapter

import android.text.Spanned
import android.text.TextUtils
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.model.StatusViewState
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.COLLAPSE_INPUT_FILTER
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.NO_INPUT_FILTER
import com.keylesspalace.tusky.viewdata.toViewData
import kotlinx.android.synthetic.main.item_report_status.view.*
import java.util.*

class StatusViewHolder(
        itemView: View,
        private val statusDisplayOptions: StatusDisplayOptions,
        private val viewState: StatusViewState,
        private val adapterHandler: AdapterHandler,
        private val getStatusForPosition: (Int) -> Status?
) : RecyclerView.ViewHolder(itemView) {
    private val mediaViewHeight = itemView.context.resources.getDimensionPixelSize(R.dimen.status_media_preview_height)
    private val statusViewHelper = StatusViewHelper(itemView)

    private val previewListener = object : StatusViewHelper.MediaPreviewListener {
        override fun onViewMedia(v: View?, idx: Int) {
            status()?.let { status ->
                adapterHandler.showMedia(v, status, idx)
            }
        }

        override fun onContentHiddenChange(isShowing: Boolean) {
            status()?.id?.let { id ->
                viewState.setMediaShow(id, isShowing)
            }
        }
    }

    init {
        itemView.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            status()?.let { status ->
                adapterHandler.setStatusChecked(status, isChecked)
            }
        }
        itemView.status_media_preview_container.clipToOutline = true
    }

    fun bind(status: Status) {
        itemView.statusSelection.isChecked = adapterHandler.isStatusChecked(status.id)

        updateTextView()

        val sensitive = status.sensitive

        statusViewHelper.setMediasPreview(statusDisplayOptions, status.attachments,
                sensitive, previewListener, viewState.isMediaShow(status.id, status.sensitive),
                mediaViewHeight)

        statusViewHelper.setupPollReadonly(status.poll.toViewData(), status.emojis, statusDisplayOptions)
        setCreatedAt(status.createdAt)
    }

    private fun updateTextView() {
        status()?.let { status ->
            setupCollapsedState(shouldTrimStatus(status.content), viewState.isCollapsed(status.id, true),
                    viewState.isContentShow(status.id, status.sensitive), status.spoilerText)

            if (status.spoilerText.isBlank()) {
                setTextVisible(true, status.content, status.mentions, status.emojis, adapterHandler)
                itemView.statusContentWarningButton.hide()
                itemView.statusContentWarningDescription.hide()
            } else {
                val emojiSpoiler = status.spoilerText.emojify(status.emojis, itemView.statusContentWarningDescription, statusDisplayOptions.animateEmojis)
                itemView.statusContentWarningDescription.text = emojiSpoiler
                itemView.statusContentWarningDescription.show()
                itemView.statusContentWarningButton.show()
                setContentWarningButtonText(viewState.isContentShow(status.id, true))
                itemView.statusContentWarningButton.setOnClickListener {
                    status()?.let { status ->
                        val contentShown = viewState.isContentShow(status.id, true)
                        itemView.statusContentWarningDescription.invalidate()
                        viewState.setContentShow(status.id, !contentShown)
                        setTextVisible(!contentShown, status.content, status.mentions, status.emojis, adapterHandler)
                        setContentWarningButtonText(!contentShown)
                    }
                }
                setTextVisible(viewState.isContentShow(status.id, true), status.content, status.mentions, status.emojis, adapterHandler)
            }
        }
    }

    private fun setContentWarningButtonText(contentShown: Boolean) {
        if(contentShown) {
            itemView.statusContentWarningButton.setText(R.string.status_content_warning_show_less)
        } else {
            itemView.statusContentWarningButton.setText(R.string.status_content_warning_show_more)
        }
    }

    private fun setTextVisible(expanded: Boolean,
                               content: Spanned,
                               mentions: Array<Status.Mention>?,
                               emojis: List<Emoji>,
                               listener: LinkListener) {
        if (expanded) {
            val emojifiedText = content.emojify(emojis, itemView.statusContent, statusDisplayOptions.animateEmojis)
            LinkHelper.setClickableText(itemView.statusContent, emojifiedText, mentions, listener)
        } else {
            LinkHelper.setClickableMentions(itemView.statusContent, mentions, listener)
        }
        if (itemView.statusContent.text.isNullOrBlank()) {
            itemView.statusContent.hide()
        } else {
            itemView.statusContent.show()
        }
    }

    private fun setCreatedAt(createdAt: Date?) {
        if (statusDisplayOptions.useAbsoluteTime) {
            itemView.timestampInfo.text = statusViewHelper.getAbsoluteTime(createdAt)
        } else {
            itemView.timestampInfo.text = if (createdAt != null) {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                TimestampUtils.getRelativeTimeSpanString(itemView.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                "?m"
            }
        }
    }


    private fun setupCollapsedState(collapsible: Boolean, collapsed: Boolean, expanded: Boolean, spoilerText: String) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            itemView.buttonToggleContent.setOnClickListener{
                status()?.let { status ->
                    viewState.setCollapsed(status.id, !collapsed)
                    updateTextView()
                }
            }

            itemView.buttonToggleContent.show()
            if (collapsed) {
                itemView.buttonToggleContent.setText(R.string.status_content_show_more)
                itemView.statusContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                itemView.buttonToggleContent.setText(R.string.status_content_show_less)
                itemView.statusContent.filters = NO_INPUT_FILTER
            }
        } else {
            itemView.buttonToggleContent.hide()
            itemView.statusContent.filters = NO_INPUT_FILTER
        }
    }

    private fun status() = getStatusForPosition(adapterPosition)
}