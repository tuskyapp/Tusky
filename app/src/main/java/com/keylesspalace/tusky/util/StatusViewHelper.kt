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
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.view.MediaPreviewImageView
import com.keylesspalace.tusky.viewdata.PollViewData
import com.keylesspalace.tusky.viewdata.buildDescription
import com.keylesspalace.tusky.viewdata.calculatePercent
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
            statusDisplayOptions: StatusDisplayOptions,
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
        if (statusDisplayOptions.mediaPreviewEnabled) {
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


        val mediaPreviewUnloaded = ColorDrawable(ThemeUtils.getColor(context, R.attr.colorBackgroundAccent))

        val n = min(attachments.size, Status.MAX_MEDIA_ATTACHMENTS)

        for (i in 0 until n) {
            val attachment = attachments[i]
            val previewUrl = attachment.previewUrl
            val description = attachment.description

            if (TextUtils.isEmpty(description)) {
                mediaPreviews[i].contentDescription = context.getString(R.string.action_view_media)
            } else {
                mediaPreviews[i].contentDescription = description
            }

            mediaPreviews[i].visibility = View.VISIBLE

            if (TextUtils.isEmpty(previewUrl)) {
                Glide.with(mediaPreviews[i])
                        .load(mediaPreviewUnloaded)
                        .centerInside()
                        .into(mediaPreviews[i])
            } else {
                val placeholder = if (attachment.blurhash != null)
                    decodeBlurHash(context, attachment.blurhash)
                else mediaPreviewUnloaded
                val meta = attachment.meta
                val focus = meta?.focus
                if (showingContent) {
                    if (focus != null) { // If there is a focal point for this attachment:
                        mediaPreviews[i].setFocalPoint(focus)

                        Glide.with(mediaPreviews[i])
                                .load(previewUrl)
                                .placeholder(placeholder)
                                .centerInside()
                                .addListener(mediaPreviews[i])
                                .into(mediaPreviews[i])
                    } else {
                        mediaPreviews[i].removeFocalPoint()

                        Glide.with(mediaPreviews[i])
                                .load(previewUrl)
                                .placeholder(placeholder)
                                .centerInside()
                                .into(mediaPreviews[i])
                    }
                } else {
                    mediaPreviews[i].removeFocalPoint()
                    if (statusDisplayOptions.useBlurhash && attachment.blurhash != null) {
                        val blurhashBitmap = decodeBlurHash(context, attachment.blurhash)
                        mediaPreviews[i].setImageDrawable(blurhashBitmap)
                    } else {
                        mediaPreviews[i].setImageDrawable(mediaPreviewUnloaded)
                    }
                }
            }

            val type = attachment.type
            if (showingContent
                    && (type === Attachment.Type.VIDEO) or (type === Attachment.Type.GIFV)) {
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
            sensitiveMediaWarning.text =  if (sensitive) {
                context.getString(R.string.status_sensitive_media_title)
            } else {
                context.getString(R.string.status_media_hidden_title)
            }

            sensitiveMediaWarning.visibility = if (showingContent) View.GONE else View.VISIBLE
            sensitiveMediaShow.visibility = if (showingContent) View.VISIBLE else View.GONE
            sensitiveMediaShow.setOnClickListener { v ->
                previewListener.onContentHiddenChange(false)
                v.visibility = View.GONE
                sensitiveMediaWarning.visibility = View.VISIBLE
                setMediasPreview(statusDisplayOptions, attachments, sensitive, previewListener,
                        false, mediaPreviewHeight)
            }
            sensitiveMediaWarning.setOnClickListener { v ->
                previewListener.onContentHiddenChange(true)
                v.visibility = View.GONE
                sensitiveMediaShow.visibility = View.VISIBLE
                setMediasPreview(statusDisplayOptions, attachments, sensitive, previewListener,
                        true, mediaPreviewHeight)
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
        mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0)

        mediaLabel.setOnClickListener { listener.onViewMedia(null, 0) }
    }

    private fun getLabelTypeText(context: Context, type: Attachment.Type): String {
        return when (type) {
            Attachment.Type.IMAGE -> context.getString(R.string.status_media_images)
            Attachment.Type.GIFV, Attachment.Type.VIDEO -> context.getString(R.string.status_media_video)
            Attachment.Type.AUDIO -> context.getString(R.string.status_media_audio)
            else -> context.getString(R.string.status_media_attachments)
        }
    }

    @DrawableRes
    private fun getLabelIcon(type: Attachment.Type): Int {
        return when (type) {
            Attachment.Type.IMAGE -> R.drawable.ic_photo_24dp
            Attachment.Type.GIFV, Attachment.Type.VIDEO -> R.drawable.ic_videocam_24dp
            Attachment.Type.AUDIO -> R.drawable.ic_music_box_24dp
            else -> R.drawable.ic_attach_file_24dp
        }
    }

    fun setupPollReadonly(poll: PollViewData?, emojis: List<Emoji>, statusDisplayOptions: StatusDisplayOptions) {
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


            setupPollResult(poll, emojis, pollResults, statusDisplayOptions.animateEmojis)

            pollDescription.visibility = View.VISIBLE
            pollDescription.text = getPollInfoText(timestamp, poll, pollDescription, statusDisplayOptions.useAbsoluteTime)
        }
    }

    private fun getPollInfoText(timestamp: Long, poll: PollViewData, pollDescription: TextView, useAbsoluteTime: Boolean): CharSequence {
        val context = pollDescription.context

        val votesText = if(poll.votersCount == null) {
            val votes = NumberFormat.getNumberInstance().format(poll.votesCount.toLong())
            context.resources.getQuantityString(R.plurals.poll_info_votes, poll.votesCount, votes)
        } else {
            val votes = NumberFormat.getNumberInstance().format(poll.votersCount.toLong())
            context.resources.getQuantityString(R.plurals.poll_info_people, poll.votersCount, votes)
        }
        val pollDurationInfo = if (poll.expired) {
            context.getString(R.string.poll_info_closed)
        } else {
            if (useAbsoluteTime) {
                context.getString(R.string.poll_info_time_absolute, getAbsoluteTime(poll.expiresAt))
            } else {
                TimestampUtils.formatPollDuration(context, poll.expiresAt!!.time, timestamp)
            }
        }

        return context.getString(R.string.poll_info_format, votesText, pollDurationInfo)
    }


    private fun setupPollResult(poll: PollViewData, emojis: List<Emoji>, pollResults: List<TextView>, animateEmojis: Boolean) {
        val options = poll.options

        for (i in 0 until Status.MAX_POLL_OPTIONS) {
            if (i < options.size) {
                val percent = calculatePercent(options[i].votesCount, poll.votersCount, poll.votesCount)

                val pollOptionText = buildDescription(options[i].title, percent, pollResults[i].context)
                pollResults[i].text = pollOptionText.emojify(emojis, pollResults[i], animateEmojis)
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
        val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}