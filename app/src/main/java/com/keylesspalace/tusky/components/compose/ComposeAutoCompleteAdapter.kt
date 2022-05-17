/* Copyright 2022 Tusky Contributors
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import androidx.annotation.WorkerThread
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ItemAutocompleteAccountBinding
import com.keylesspalace.tusky.databinding.ItemAutocompleteEmojiBinding
import com.keylesspalace.tusky.databinding.ItemAutocompleteHashtagBinding
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.visible

class ComposeAutoCompleteAdapter(
    private val autocompletionProvider: AutocompletionProvider,
    private val animateAvatar: Boolean,
    private val animateEmojis: Boolean,
    private val showBotBadge: Boolean
) : BaseAdapter(), Filterable {

    private var resultList: List<AutocompleteResult> = emptyList()

    override fun getCount() = resultList.size

    override fun getItem(index: Int): AutocompleteResult {
        return resultList[index]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getFilter(): Filter {
        return object : Filter() {

            override fun convertResultToString(resultValue: Any): CharSequence {
                return when (resultValue) {
                    is AutocompleteResult.AccountResult -> formatUsername(resultValue)
                    is AutocompleteResult.HashtagResult -> formatHashtag(resultValue)
                    is AutocompleteResult.EmojiResult -> formatEmoji(resultValue)
                    else -> ""
                }
            }

            @WorkerThread
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null) {
                    val results = autocompletionProvider.search(constraint.toString())
                    filterResults.values = results
                    filterResults.count = results.size
                }
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                if (results.count > 0) {
                    resultList = results.values as List<AutocompleteResult>
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemViewType = getItemViewType(position)
        val context = parent.context

        val view: View = convertView ?: run {
            val layoutInflater = LayoutInflater.from(context)
            val binding = when (itemViewType) {
                ACCOUNT_VIEW_TYPE -> ItemAutocompleteAccountBinding.inflate(layoutInflater)
                HASHTAG_VIEW_TYPE -> ItemAutocompleteHashtagBinding.inflate(layoutInflater)
                EMOJI_VIEW_TYPE -> ItemAutocompleteEmojiBinding.inflate(layoutInflater)
                else -> throw AssertionError("unknown view type")
            }
            binding.root.tag = binding
            binding.root
        }

        when (val binding = view.tag) {
            is ItemAutocompleteAccountBinding -> {
                val accountResult = getItem(position) as AutocompleteResult.AccountResult
                val account = accountResult.account
                binding.username.text = context.getString(R.string.post_username_format, account.username)
                binding.displayName.text = account.name.emojify(account.emojis, binding.displayName, animateEmojis)
                val avatarRadius = context.resources.getDimensionPixelSize(R.dimen.avatar_radius_42dp)
                loadAvatar(
                    account.avatar,
                    binding.avatar,
                    avatarRadius,
                    animateAvatar
                )
                binding.avatarBadge.visible(showBotBadge && account.bot)
            }
            is ItemAutocompleteHashtagBinding -> {
                val result = getItem(position) as AutocompleteResult.HashtagResult
                binding.root.text = formatHashtag(result)
            }
            is ItemAutocompleteEmojiBinding -> {
                val emojiResult = getItem(position) as AutocompleteResult.EmojiResult
                val (shortcode, url) = emojiResult.emoji
                binding.shortcode.text = context.getString(R.string.emoji_shortcode_format, shortcode)
                Glide.with(binding.preview)
                    .load(url)
                    .into(binding.preview)
            }
        }
        return view
    }

    override fun getViewTypeCount() = 3

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AutocompleteResult.AccountResult -> ACCOUNT_VIEW_TYPE
            is AutocompleteResult.HashtagResult -> HASHTAG_VIEW_TYPE
            is AutocompleteResult.EmojiResult -> EMOJI_VIEW_TYPE
        }
    }

    sealed class AutocompleteResult {
        class AccountResult(val account: TimelineAccount) : AutocompleteResult()

        class HashtagResult(val hashtag: String) : AutocompleteResult()

        class EmojiResult(val emoji: Emoji) : AutocompleteResult()
    }

    interface AutocompletionProvider {
        fun search(token: String): List<AutocompleteResult>
    }

    companion object {
        private const val ACCOUNT_VIEW_TYPE = 0
        private const val HASHTAG_VIEW_TYPE = 1
        private const val EMOJI_VIEW_TYPE = 2

        private fun formatUsername(result: AutocompleteResult.AccountResult): String {
            return String.format("@%s", result.account.username)
        }

        private fun formatHashtag(result: AutocompleteResult.HashtagResult): String {
            return String.format("#%s", result.hashtag)
        }

        private fun formatEmoji(result: AutocompleteResult.EmojiResult): String {
            return String.format(":%s:", result.emoji.shortcode)
        }
    }
}
