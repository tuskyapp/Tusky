/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.components.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.view.ProgressImageView

class MediaPreviewAdapter(
    context: Context,
    private val onAddCaption: (ComposeViewModel.QueuedMedia) -> Unit,
    private val onAddFocus: (ComposeViewModel.QueuedMedia) -> Unit,
    private val onEditImage: (ComposeViewModel.QueuedMedia) -> Unit,
    private val onRemove: (ComposeViewModel.QueuedMedia) -> Unit
) : ListAdapter<ComposeViewModel.QueuedMedia, MediaPreviewAdapter.PreviewViewHolder>(
    object : DiffUtil.ItemCallback<ComposeViewModel.QueuedMedia>() {
        override fun areItemsTheSame(
            oldItem: ComposeViewModel.QueuedMedia,
            newItem: ComposeViewModel.QueuedMedia
        ) = oldItem.localId == newItem.localId

        override fun areContentsTheSame(
            oldItem: ComposeViewModel.QueuedMedia,
            newItem: ComposeViewModel.QueuedMedia
        ) = oldItem == newItem
    }
) {

    private fun onMediaClick(item: ComposeViewModel.QueuedMedia, view: View) {
        val popup = PopupMenu(view.context, view)
        val addCaptionId = 1
        val addFocusId = 2
        val editImageId = 3
        val removeId = 4

        popup.menu.add(0, addCaptionId, 0, R.string.action_set_caption)
        if (item.type == ComposeViewModel.QueuedMedia.Type.IMAGE) {
            popup.menu.add(0, addFocusId, 0, R.string.action_set_focus)
            if (item.state != ComposeViewModel.QueuedMedia.State.PUBLISHED) {
                // Already-published items can't be edited
                popup.menu.add(0, editImageId, 0, R.string.action_edit_image)
            }
        }
        popup.menu.add(0, removeId, 0, R.string.action_remove)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                addCaptionId -> onAddCaption(item)
                addFocusId -> onAddFocus(item)
                editImageId -> onEditImage(item)
                removeId -> onRemove(item)
            }
            true
        }
        popup.show()
    }

    private val thumbnailViewSize =
        context.resources.getDimensionPixelSize(R.dimen.compose_media_preview_size)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder {
        return PreviewViewHolder(ProgressImageView(parent.context))
    }

    override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
        val item = getItem(position)
        holder.progressImageView.setChecked(!item.description.isNullOrEmpty())
        holder.progressImageView.setProgress(item.uploadPercent)
        if (item.type == ComposeViewModel.QueuedMedia.Type.AUDIO) {
            // TODO: Fancy waveform display?
            holder.progressImageView.setImageResource(R.drawable.ic_music_box_preview_24dp)
        } else {
            val imageView = holder.progressImageView
            val focus = item.focus

            if (focus != null) {
                imageView.setFocalPoint(focus)
            } else {
                imageView.removeFocalPoint() // Probably unnecessary since we have no UI for removal once added.
            }

            var glide = Glide.with(holder.itemView.context)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .dontAnimate()
                .centerInside()

            if (focus != null) {
                glide = glide.addListener(imageView)
            }

            glide.into(imageView)
        }

        holder.progressImageView.setOnClickListener {
            onMediaClick(item, holder.progressImageView)
        }
    }

    inner class PreviewViewHolder(val progressImageView: ProgressImageView) :
        RecyclerView.ViewHolder(progressImageView) {
        init {
            val layoutParams = ConstraintLayout.LayoutParams(thumbnailViewSize, thumbnailViewSize)
            val margin = itemView.context.resources
                .getDimensionPixelSize(R.dimen.compose_media_preview_margin)
            val marginBottom = itemView.context.resources
                .getDimensionPixelSize(R.dimen.compose_media_preview_margin_bottom)
            layoutParams.setMargins(margin, 0, margin, marginBottom)
            progressImageView.layoutParams = layoutParams
            progressImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }
}
