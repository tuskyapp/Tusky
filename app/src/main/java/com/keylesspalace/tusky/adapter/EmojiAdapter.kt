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

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Emoji
import java.util.*

class EmojiAdapter(emojiList: List<Emoji>, private val onEmojiSelectedListener: OnEmojiSelectedListener) : RecyclerView.Adapter<EmojiAdapter.EmojiHolder>() {
    private val emojiList : List<Emoji>

    init {
        this.emojiList = emojiList.filter { emoji -> emoji.visibleInPicker == null || emoji.visibleInPicker }
                .sortedBy { it.shortcode.toLowerCase(Locale.ROOT) }
    }

    override fun getItemCount(): Int {
        return emojiList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji_button, parent, false) as ImageView
        return EmojiHolder(view)
    }

    override fun onBindViewHolder(viewHolder: EmojiHolder, position: Int) {
        val emoji = emojiList[position]

        Glide.with(viewHolder.emojiImageView)
                .load(emoji.url)
                .into(viewHolder.emojiImageView)

        viewHolder.emojiImageView.setOnClickListener {
            onEmojiSelectedListener.onEmojiSelected(emoji.shortcode)
        }

        viewHolder.emojiImageView.contentDescription = emoji.shortcode
    }

    class EmojiHolder(val emojiImageView: ImageView) : RecyclerView.ViewHolder(emojiImageView)

}

interface OnEmojiSelectedListener {
    fun onEmojiSelected(shortcode: String)
}
