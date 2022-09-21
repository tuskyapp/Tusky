/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.components.drafts

import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.DraftAttachment
import com.keylesspalace.tusky.view.MediaPreviewImageView

class DraftMediaAdapter(
    private val attachmentClick: () -> Unit
) : ListAdapter<DraftAttachment, DraftMediaAdapter.DraftMediaViewHolder>(
    object : DiffUtil.ItemCallback<DraftAttachment>() {
        override fun areItemsTheSame(oldItem: DraftAttachment, newItem: DraftAttachment): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: DraftAttachment, newItem: DraftAttachment): Boolean {
            return oldItem == newItem
        }
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftMediaViewHolder {
        return DraftMediaViewHolder(MediaPreviewImageView(parent.context))
    }

    override fun onBindViewHolder(holder: DraftMediaViewHolder, position: Int) {
        getItem(position)?.let { attachment ->
            if (attachment.type == DraftAttachment.Type.AUDIO) {
                holder.imageView.clearFocus()
                holder.imageView.setImageResource(R.drawable.ic_music_box_preview_24dp)
            } else {
                if (attachment.focus != null)
                    holder.imageView.setFocalPoint(attachment.focus)
                else
                    holder.imageView.clearFocus()
                var glide = Glide.with(holder.itemView.context)
                    .load(attachment.uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .dontAnimate()
                    .centerInside()

                if (attachment.focus != null)
                    glide = glide.addListener(holder.imageView)

                glide.into(holder.imageView)
            }
        }
    }

    inner class DraftMediaViewHolder(val imageView: MediaPreviewImageView) :
        RecyclerView.ViewHolder(imageView) {
        init {
            val thumbnailViewSize =
                imageView.context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)
            val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
            val margin = itemView.context.resources
                .getDimensionPixelSize(R.dimen.compose_media_preview_margin)
            val marginBottom = itemView.context.resources
                .getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)
            layoutParams.setMargins(margin, 0, margin, marginBottom)
            imageView.layoutParams = layoutParams
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setOnClickListener {
                attachmentClick()
            }
        }
    }
}
