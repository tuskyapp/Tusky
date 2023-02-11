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
        .sortedWith(compareBy<Emoji, String?>(nullsFirst()) { it.category?.lowercase(Locale.ROOT) }.thenBy { it.shortcode.lowercase(Locale.ROOT) })
    private val emojiList = mutableListOf<EmojiGridItem>()
    lateinit var emojiCategories: LinkedHashMap<String?, Int>
        private set
    private lateinit var emojiCategoryPositions: LinkedHashMap<String?, Int>

    data class EmojiGridItem(val emoji: Emoji?, val trueIndex: Int?)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        val spanCount = (recyclerView.layoutManager as GridLayoutManager).spanCount

        val catMap = LinkedHashMap<String?, Int>()
        val catPosMap = LinkedHashMap<String?, Int>()
        var currentCategory: String? = null
        for (index in trueEmojiList.indices) {
            val emoji = trueEmojiList[index]
            if (emoji.category != currentCategory || index == 0) {
                currentCategory = emoji.category
                catMap[currentCategory] = index
                if (index > 0) {
                    val emojiListIndex = emojiList.size - 1
                    repeat(2 * spanCount - (emojiListIndex % spanCount) - 1) {
                        emojiList.add(EmojiGridItem(null, null))
                    }
                }
                catPosMap[currentCategory] = emojiList.size
            }
            emojiList.add(EmojiGridItem(emoji, index))
        }

        emojiCategories = catMap
        emojiCategoryPositions = catPosMap
    }

    fun getCategoryStartPosition(category: String?): Int {
        return emojiCategoryPositions.getValue(category)
    }

    fun getCategoryForPosition(position: Int): String? {
        var prevKey: String? = null
        for (entry in emojiCategoryPositions) {
            if (position < entry.value) {
                return prevKey
            }
            prevKey = entry.key
        }
        return prevKey
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
            Glide.with(emojiImageView).clear(emojiImageView)
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
