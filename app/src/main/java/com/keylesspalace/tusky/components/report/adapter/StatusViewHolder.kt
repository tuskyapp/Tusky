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
import com.keylesspalace.tusky.databinding.ItemReportStatusBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.util.AbsoluteTimeFormatter
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.StatusViewHelper
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.COLLAPSE_INPUT_FILTER
import com.keylesspalace.tusky.util.StatusViewHelper.Companion.NO_INPUT_FILTER
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getRelativeTimeSpanString
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.setClickableMentions
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.shouldTrimStatus
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.keylesspalace.tusky.viewdata.toViewData
import java.util.Date

class StatusViewHolder(
    private val binding: ItemReportStatusBinding,
    private val statusDisplayOptions: StatusDisplayOptions,
    private val viewState: StatusViewState,
    private val adapterHandler: AdapterHandler,
    private val getStatusForPosition: (Int) -> StatusViewData.Concrete?
) : RecyclerView.ViewHolder(binding.root) {

    private val mediaViewHeight = itemView.context.resources.getDimensionPixelSize(R.dimen.status_media_preview_height)
    private val statusViewHelper = StatusViewHelper(itemView)
    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    private val previewListener = object : StatusViewHelper.MediaPreviewListener {
        override fun onViewMedia(v: View?, idx: Int) {
            viewdata()?.let { viewdata ->
                adapterHandler.showMedia(v, viewdata.status, idx)
            }
        }

        override fun onContentHiddenChange(isShowing: Boolean) {
            viewdata()?.id?.let { id ->
                viewState.setMediaShow(id, isShowing)
            }
        }
    }

    init {
        binding.statusSelection.setOnCheckedChangeListener { _, isChecked ->
            viewdata()?.let { viewdata ->
                adapterHandler.setStatusChecked(viewdata.status, isChecked)
            }
        }
        binding.statusMediaPreviewContainer.clipToOutline = true
    }

    fun bind(viewData: StatusViewData.Concrete) {
        binding.statusSelection.isChecked = adapterHandler.isStatusChecked(viewData.id)

        updateTextView()

        val sensitive = viewData.status.sensitive

        statusViewHelper.setMediasPreview(
            statusDisplayOptions, viewData.status.attachments,
            sensitive, previewListener, viewState.isMediaShow(viewData.id, viewData.status.sensitive),
            mediaViewHeight
        )

        statusViewHelper.setupPollReadonly(viewData.status.poll.toViewData(), viewData.status.emojis, statusDisplayOptions)
        setCreatedAt(viewData.status.createdAt)
    }

    private fun updateTextView() {
        viewdata()?.let { viewdata ->
            setupCollapsedState(
                shouldTrimStatus(viewdata.content), viewState.isCollapsed(viewdata.id, true),
                viewState.isContentShow(viewdata.id, viewdata.status.sensitive), viewdata.spoilerText
            )

            if (viewdata.spoilerText.isBlank()) {
                setTextVisible(true, viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
                binding.statusContentWarningButton.hide()
                binding.statusContentWarningDescription.hide()
            } else {
                val emojiSpoiler = viewdata.spoilerText.emojify(viewdata.status.emojis, binding.statusContentWarningDescription, statusDisplayOptions.animateEmojis)
                binding.statusContentWarningDescription.text = emojiSpoiler
                binding.statusContentWarningDescription.show()
                binding.statusContentWarningButton.show()
                setContentWarningButtonText(viewState.isContentShow(viewdata.id, true))
                binding.statusContentWarningButton.setOnClickListener {
                    viewdata()?.let { viewdata ->
                        val contentShown = viewState.isContentShow(viewdata.id, true)
                        binding.statusContentWarningDescription.invalidate()
                        viewState.setContentShow(viewdata.id, !contentShown)
                        setTextVisible(!contentShown, viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
                        setContentWarningButtonText(!contentShown)
                    }
                }
                setTextVisible(viewState.isContentShow(viewdata.id, true), viewdata.content, viewdata.status.mentions, viewdata.status.tags, viewdata.status.emojis, adapterHandler)
            }
        }
    }

    private fun setContentWarningButtonText(contentShown: Boolean) {
        if (contentShown) {
            binding.statusContentWarningButton.setText(R.string.post_content_warning_show_less)
        } else {
            binding.statusContentWarningButton.setText(R.string.post_content_warning_show_more)
        }
    }

    private fun setTextVisible(
        expanded: Boolean,
        content: Spanned,
        mentions: List<Status.Mention>,
        tags: List<HashTag>?,
        emojis: List<Emoji>,
        listener: LinkListener
    ) {
        if (expanded) {
            val emojifiedText = content.emojify(emojis, binding.statusContent, statusDisplayOptions.animateEmojis)
            setClickableText(binding.statusContent, emojifiedText, mentions, tags, listener)
        } else {
            setClickableMentions(binding.statusContent, mentions, listener)
        }
        if (binding.statusContent.text.isNullOrBlank()) {
            binding.statusContent.hide()
        } else {
            binding.statusContent.show()
        }
    }

    private fun setCreatedAt(createdAt: Date?) {
        if (statusDisplayOptions.useAbsoluteTime) {
            binding.timestampInfo.text = absoluteTimeFormatter.format(createdAt)
        } else {
            binding.timestampInfo.text = if (createdAt != null) {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                getRelativeTimeSpanString(binding.timestampInfo.context, then, now)
            } else {
                // unknown minutes~
                "?m"
            }
        }
    }

    private fun setupCollapsedState(collapsible: Boolean, collapsed: Boolean, expanded: Boolean, spoilerText: String) {
        /* input filter for TextViews have to be set before text */
        if (collapsible && (expanded || TextUtils.isEmpty(spoilerText))) {
            binding.buttonToggleContent.setOnClickListener {
                viewdata()?.let { viewdata ->
                    viewState.setCollapsed(viewdata.id, !collapsed)
                    updateTextView()
                }
            }

            binding.buttonToggleContent.show()
            if (collapsed) {
                binding.buttonToggleContent.setText(R.string.post_content_show_more)
                binding.statusContent.filters = COLLAPSE_INPUT_FILTER
            } else {
                binding.buttonToggleContent.setText(R.string.post_content_show_less)
                binding.statusContent.filters = NO_INPUT_FILTER
            }
        } else {
            binding.buttonToggleContent.hide()
            binding.statusContent.filters = NO_INPUT_FILTER
        }
    }

    private fun viewdata() = getStatusForPosition(bindingAdapterPosition)
}
