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

package com.keylesspalace.tusky.util

import android.content.Context
import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.view.MediaPreviewImageView
import com.keylesspalace.tusky.viewdata.PollViewData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class StatusViewHelper(private val itemView: View) {
    interface MediaPreviewListener {
        fun onViewMedia(v: View?, idx: Int)
        fun onContentHiddenChange(isShowing: Boolean)
    }

    private val shortSdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val longSdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    fun setMediasPreview(
            mediaPreviewEnabled: Boolean,
            attachments: List<Attachment>,
            sensitive: Boolean,
            previewListener: MediaPreviewListener,
            showingContent: Boolean,
            mediaPreviewHeight: Int) {

        val context = itemView.context
        val mediaPreviews = arrayOf<MediaPreviewImageView>(
                itemView.findViewById(R.id.status_media_preview_0),
                itemView.findViewById(R.id.status_media_preview_1),
                itemView.findViewById(R.id.status_media_preview_2),
                itemView.findViewById(R.id.status_media_preview_3))

        val mediaOverlays = arrayOf<ImageView>(
                itemView.findViewById(R.id.status_media_overlay_0),
                itemView.findViewById(R.id.status_media_overlay_1),
                itemView.findViewById(R.id.status_media_overlay_2),
                itemView.findViewById(R.id.status_media_overlay_3))

        val sensitiveMediaWarning = itemView.findViewById<TextView>(R.id.status_sensitive_media_warning)
        val sensitiveMediaShow = itemView.findViewById<View>(R.id.status_sensitive_media_button)
        val mediaLabel = itemView.findViewById<TextView>(R.id.status_media_label)
        if (mediaPreviewEnabled) {
            // Hide the unused label.
            mediaLabel.visibility = View.GONE
        } else {
            setMediaLabel(mediaLabel, attachments, sensitive, previewListener)
            // Hide all unused views.
            mediaPreviews[0].visibility = View.GONE
            mediaPreviews[1].visibility = View.GONE
            mediaPreviews[2].visibility = View.GONE
            mediaPreviews[3].visibility = View.GONE
            sensitiveMediaWarning.visibility = View.GONE
            sensitiveMediaShow.visibility = View.GONE
            return
        }


        val mediaPreviewUnloadedId = ThemeUtils.getDrawableId(context, R.attr.media_preview_unloaded_drawable, android.R.color.black)

        val n = min(attachments.size, Status.MAX_MEDIA_ATTACHMENTS)

        for (i in 0 until n) {
            val previewUrl = attachments[i].previewUrl
            val description = attachments[i].description

            if (TextUtils.isEmpty(description)) {
                mediaPreviews[i].contentDescription = context.getString(R.string.action_view_media)
            } else {
                mediaPreviews[i].contentDescription = description
            }

            mediaPreviews[i].visibility = View.VISIBLE

            if (TextUtils.isEmpty(previewUrl)) {
                Glide.with(mediaPreviews[i])
                        .load(mediaPreviewUnloadedId)
                        .centerInside()
                        .into(mediaPreviews[i])
            } else {
                val meta = attachments[i].meta
                val focus = meta?.focus

                if (focus != null) { // If there is a focal point for this attachment:
                    mediaPreviews[i].setFocalPoint(focus)

                    Glide.with(mediaPreviews[i])
                            .load(previewUrl)
                            .placeholder(mediaPreviewUnloadedId)
                            .centerInside()
                            .addListener(mediaPreviews[i])
                            .into(mediaPreviews[i])
                } else {
                    mediaPreviews[i].removeFocalPoint()

                    Glide.with(mediaPreviews[i])
                            .load(previewUrl)
                            .placeholder(mediaPreviewUnloadedId)
                            .centerInside()
                            .into(mediaPreviews[i])
                }
            }

            val type = attachments[i].type
            if ((type === Attachment.Type.VIDEO) or (type === Attachment.Type.GIFV)) {
                mediaOverlays[i].visibility = View.VISIBLE
            } else {
                mediaOverlays[i].visibility = View.GONE
            }

            mediaPreviews[i].setOnClickListener { v ->
                previewListener.onViewMedia(v, i)
            }

            if (n <= 2) {
                mediaPreviews[0].layoutParams.height = mediaPreviewHeight * 2
                mediaPreviews[1].layoutParams.height = mediaPreviewHeight * 2
            } else {
                mediaPreviews[0].layoutParams.height = mediaPreviewHeight
                mediaPreviews[1].layoutParams.height = mediaPreviewHeight
                mediaPreviews[2].layoutParams.height = mediaPreviewHeight
                mediaPreviews[3].layoutParams.height = mediaPreviewHeight
            }
        }
        if (attachments.isNullOrEmpty()) {
            sensitiveMediaWarning.visibility = View.GONE
            sensitiveMediaShow.visibility = View.GONE
        } else {

            val hiddenContentText: String = if (sensitive) {
                context.getString(R.string.status_sensitive_media_template,
                        context.getString(R.string.status_sensitive_media_title),
                        context.getString(R.string.status_sensitive_media_directions))
            } else {
                context.getString(R.string.status_sensitive_media_template,
                        context.getString(R.string.status_media_hidden_title),
                        context.getString(R.string.status_sensitive_media_directions))
            }

            sensitiveMediaWarning.text = HtmlUtils.fromHtml(hiddenContentText)

            sensitiveMediaWarning.visibility = if (showingContent) View.GONE else View.VISIBLE
            sensitiveMediaShow.visibility = if (showingContent) View.VISIBLE else View.GONE
            sensitiveMediaShow.setOnClickListener { v ->
                previewListener.onContentHiddenChange(false)
                v.visibility = View.GONE
                sensitiveMediaWarning.visibility = View.VISIBLE
            }
            sensitiveMediaWarning.setOnClickListener { v ->
                previewListener.onContentHiddenChange(true)
                v.visibility = View.GONE
                sensitiveMediaShow.visibility = View.VISIBLE
            }
        }

        // Hide any of the placeholder previews beyond the ones set.
        for (i in n until Status.MAX_MEDIA_ATTACHMENTS) {
            mediaPreviews[i].visibility = View.GONE
        }
    }

    private fun setMediaLabel(mediaLabel: TextView, attachments: List<Attachment>, sensitive: Boolean,
                              listener: MediaPreviewListener) {
        if (attachments.isEmpty()) {
            mediaLabel.visibility = View.GONE
            return
        }
        mediaLabel.visibility = View.VISIBLE

        // Set the label's text.
        val context = mediaLabel.context
        var labelText = getLabelTypeText(context, attachments[0].type)
        if (sensitive) {
            val sensitiveText = context.getString(R.string.status_sensitive_media_title)
            labelText += String.format(" (%s)", sensitiveText)
        }
        mediaLabel.text = labelText

        // Set the icon next to the label.
        val drawableId = getLabelIcon(attachments[0].type)
        val drawable = AppCompatResources.getDrawable(context, drawableId)
        ThemeUtils.setDrawableTint(context, drawable!!, android.R.attr.textColorTertiary)
        mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)

        mediaLabel.setOnClickListener { listener.onViewMedia(null, 0) }
    }

    private fun getLabelTypeText(context: Context, type: Attachment.Type): String {
        return when (type) {
            Attachment.Type.IMAGE -> context.getString(R.string.status_media_images)
            Attachment.Type.GIFV, Attachment.Type.VIDEO -> context.getString(R.string.status_media_video)
            else -> context.getString(R.string.status_media_images)
        }
    }

    @DrawableRes
    private fun getLabelIcon(type: Attachment.Type): Int {
        return when (type) {
            Attachment.Type.IMAGE -> R.drawable.ic_photo_24dp
            Attachment.Type.GIFV, Attachment.Type.VIDEO -> R.drawable.ic_videocam_24dp
            else -> R.drawable.ic_photo_24dp
        }
    }

    fun setupPollReadonly(poll: PollViewData?, emojis: List<Emoji>, useAbsoluteTime: Boolean) {
        val pollResults = listOf<TextView>(
                itemView.findViewById(R.id.status_poll_option_result_0),
                itemView.findViewById(R.id.status_poll_option_result_1),
                itemView.findViewById(R.id.status_poll_option_result_2),
                itemView.findViewById(R.id.status_poll_option_result_3))

        val pollDescription = itemView.findViewById<TextView>(R.id.status_poll_description)

        if (poll == null) {
            for (pollResult in pollResults) {
                pollResult.visibility = View.GONE
            }
            pollDescription.visibility = View.GONE
        } else {
            val timestamp = System.currentTimeMillis()


            setupPollResult(poll, emojis, pollResults)

            pollDescription.visibility = View.VISIBLE
            pollDescription.text = getPollInfoText(timestamp, poll, pollDescription, useAbsoluteTime)
        }
    }

    private fun getPollInfoText(timestamp: Long, poll: PollViewData, pollDescription: TextView, useAbsoluteTime: Boolean): CharSequence {
        val context = pollDescription.context
        val votes = NumberFormat.getNumberInstance().format(poll.votesCount.toLong())
        val votesText = context.resources.getQuantityString(R.plurals.poll_info_votes, poll.votesCount, votes)
        val pollDurationInfo: CharSequence
        if (poll.expired) {
            pollDurationInfo = context.getString(R.string.poll_info_closed)
        } else {
            if (useAbsoluteTime) {
                pollDurationInfo = context.getString(R.string.poll_info_time_absolute, getAbsoluteTime(poll.expiresAt))
            } else {
                val pollDuration = DateUtils.formatPollDuration(context, poll.expiresAt!!.time, timestamp)
                pollDurationInfo = context.getString(R.string.poll_info_time_relative, pollDuration)
            }
        }

        return context.getString(R.string.poll_info_format, votesText, pollDurationInfo)
    }


    private fun setupPollResult(poll: PollViewData, emojis: List<Emoji>, pollResults: List<TextView>) {
        val options = poll.options

        for (i in 0 until Status.MAX_POLL_OPTIONS) {
            if (i < options.size) {
                val percent = options[i].getPercent(poll.votesCount)

                val pollOptionText = pollResults[i].context.getString(R.string.poll_option_format, percent, options[i].title)
                pollResults[i].text = CustomEmojiHelper.emojifyText(HtmlUtils.fromHtml(pollOptionText), emojis, pollResults[i])
                pollResults[i].visibility = View.VISIBLE

                val level = percent * 100

                pollResults[i].background.level = level

            } else {
                pollResults[i].visibility = View.GONE
            }
        }
    }

    fun getAbsoluteTime(time: Date?): String {
        return if (time != null) {
            if (android.text.format.DateUtils.isToday(time.time)) {
                shortSdf.format(time)
            } else {
                longSdf.format(time)
            }
        } else {
            "??:??:??"
        }
    }

    companion object {
        val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter.INSTANCE)
        val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}