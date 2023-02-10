/* Copyright 2018 Conny Duck
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.databinding.ItemEmojiButtonBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.util.BindingHolder
import java.util.Locale

class EmojiAdapter(
    emojiList: List<Emoji>,
    private val onEmojiSelectedListener: OnEmojiSelectedListener,
    private val animate: Boolean
) : RecyclerView.Adapter<BindingHolder<ItemEmojiButtonBinding>>() {

    private val trueEmojiList: List<Emoji> = emojiList.filter { emoji -> emoji.visibleInPicker == null || emoji.visibleInPicker }
        .sortedWith(compareBy<Emoji, String?>(nullsLast()) { it.category?.lowercase(Locale.ROOT) }.thenBy { it.shortcode.lowercase(Locale.ROOT) })
    private val emojiList = mutableListOf<EmojiGridItem>()
    private lateinit var emojiCategories: Map<String?, Int>

    data class EmojiGridItem(val emoji: Emoji?, val trueIndex: Int?)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        val spanCount = (recyclerView.layoutManager as GridLayoutManager).spanCount

        val catMap = HashMap<String?, Int>()
        var currentCategory: String? = null
        for (index in trueEmojiList.indices) {
            val emoji = trueEmojiList[index]
            if (emoji.category != currentCategory) {
                currentCategory = emoji.category
                catMap[currentCategory] = index
                val emojiListIndex = emojiList.size - 1
                if (index > 0) {
                    repeat(2 * spanCount - (emojiListIndex % spanCount) - 1) {
                        emojiList.add(EmojiGridItem(null, null))
                    }
                }
            }
            emojiList.add(EmojiGridItem(emoji, index))
        }

        emojiCategories = catMap
    }

    override fun getItemCount() = emojiList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemEmojiButtonBinding> {
        val binding = ItemEmojiButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemEmojiButtonBinding>, position: Int) {
        val emojiGridItem = emojiList[position]
        val emoji = emojiGridItem.emoji
        val emojiImageView = holder.binding.root
        if (emoji == null) {
            emojiImageView.background = null
            emojiImageView.setImageResource(android.R.color.transparent)
            emojiImageView.contentDescription = null
            emojiImageView.setOnClickListener(null)
            TooltipCompat.setTooltipText(emojiImageView, null)
            return
        }

        if (animate) {
            Glide.with(emojiImageView)
                .load(emoji.url)
                .into(emojiImageView)
        } else {
            Glide.with(emojiImageView)
                .asBitmap()
                .load(emoji.url)
                .into(emojiImageView)
        }

        emojiImageView.setOnClickListener {
            onEmojiSelectedListener.onEmojiSelected(emoji.shortcode)
        }

        emojiImageView.contentDescription = emoji.shortcode
        TooltipCompat.setTooltipText(emojiImageView, emoji.shortcode)
    }
}

interface OnEmojiSelectedListener {
    fun onEmojiSelected(shortcode: String)
}
