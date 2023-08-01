/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.trending

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemTrendingLinkBinding
import com.keylesspalace.tusky.entity.TrendsLink
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.decodeBlurHash
import com.keylesspalace.tusky.util.hide

class TrendingLinkViewHolder(
    private val binding: ItemTrendingLinkBinding,
    private val onClick: (String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    private var link: TrendsLink? = null

    private val radius = binding.cardImage.resources.getDimensionPixelSize(R.dimen.card_radius).toFloat()

    init {
        itemView.setOnClickListener { link?.let { onClick(it.url) } }
    }

    fun bind(link: TrendsLink, statusDisplayOptions: StatusDisplayOptions) {
        this.link = link

        // TODO: This is very similar to the code in StatusBaseViewHolder.setupCard().
        // Can a "PreviewCardView" type be created to encapsulate all this?
        binding.cardTitle.text = link.title

        val description = if (link.description.isNotBlank()) {
            link.description
        } else if (link.authorName.isNotBlank()) {
            link.authorName
        } else {
            null
        }
        description?.let { binding.cardDescription.text = it } ?: binding.cardDescription.hide()

        binding.cardLink.text = link.url

        val cardImageShape = ShapeAppearanceModel.Builder()

        if (statusDisplayOptions.mediaPreviewEnabled && !link.image.isNullOrBlank()) {
            if (link.width > link.height) {
                binding.statusCardView.orientation = LinearLayout.VERTICAL
                binding.cardImage.layoutParams.height = binding.cardImage.resources.getDimensionPixelSize(R.dimen.card_image_vertical_height)
                binding.cardImage.layoutParams.width = MATCH_PARENT
                binding.cardInfo.layoutParams.height = MATCH_PARENT
                binding.cardInfo.layoutParams.width = WRAP_CONTENT
                cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius)
                cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, radius)
            } else {
                binding.statusCardView.orientation = LinearLayout.HORIZONTAL
                binding.cardImage.layoutParams.height = MATCH_PARENT
                binding.cardImage.layoutParams.width = binding.cardImage.resources.getDimensionPixelSize(R.dimen.card_image_horizontal_width)
                binding.cardInfo.layoutParams.height = WRAP_CONTENT
                binding.cardInfo.layoutParams.width = MATCH_PARENT
                cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius)
                cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, radius)
            }

            binding.cardImage.shapeAppearanceModel = cardImageShape.build()
            binding.cardImage.scaleType = ScaleType.CENTER_CROP

            val builder = Glide.with(binding.cardImage.context)
                .load(link.image)
                .dontTransform()
            if (statusDisplayOptions.useBlurhash && !link.blurhash.isNullOrBlank()) {
                builder
                    .placeholder(decodeBlurHash(binding.cardImage.context, link.blurhash))
                    .into(binding.cardImage)
            } else {
                builder.into(binding.cardImage)
            }
        } else if (statusDisplayOptions.useBlurhash && !link.blurhash.isNullOrBlank()) {
            binding.statusCardView.orientation = LinearLayout.HORIZONTAL
            binding.cardImage.layoutParams.height = MATCH_PARENT
            binding.cardImage.layoutParams.width = binding.cardImage.resources.getDimensionPixelSize(R.dimen.card_image_horizontal_width)
            binding.cardInfo.layoutParams.height = WRAP_CONTENT
            binding.cardInfo.layoutParams.width = MATCH_PARENT

            cardImageShape
                .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
            binding.cardImage.shapeAppearanceModel = cardImageShape.build()
            binding.cardImage.scaleType = ScaleType.CENTER_CROP

            Glide.with(binding.cardImage.context)
                .load(decodeBlurHash(binding.cardImage.context, link.blurhash))
                .dontTransform()
                .into(binding.cardImage)
        } else {
            binding.statusCardView.orientation = LinearLayout.HORIZONTAL
            binding.cardImage.layoutParams.height = MATCH_PARENT
            binding.cardImage.layoutParams.width = binding.cardImage.resources.getDimensionPixelSize(R.dimen.card_image_horizontal_width)
            binding.cardInfo.layoutParams.height = WRAP_CONTENT
            binding.cardInfo.layoutParams.width = MATCH_PARENT

            binding.cardImage.shapeAppearanceModel = ShapeAppearanceModel()
            binding.cardImage.scaleType = ScaleType.CENTER

            Glide.with(binding.cardImage.context)
                .load(R.drawable.card_image_placeholder)
                .into(binding.cardImage)
        }

        binding.statusCardView.clipToOutline = true
    }
}
