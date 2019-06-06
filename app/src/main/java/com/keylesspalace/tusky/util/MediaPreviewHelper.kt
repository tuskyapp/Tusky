package com.keylesspalace.tusky.util

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.view.MediaPreviewImageView

interface MediaPreviewListener {
    fun onViewMedia(v: View?, idx: Int)
    fun onContentHiddenChange(isShowing: Boolean)
}

fun setMediaPreviews(itemView: View,
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

    val n = Math.min(attachments.size, Status.MAX_MEDIA_ATTACHMENTS)

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
    }
    else {

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
