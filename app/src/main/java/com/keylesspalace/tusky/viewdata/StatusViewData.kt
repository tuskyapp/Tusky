/* Copyright 2017 Andrew Dawson
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
package com.keylesspalace.tusky.viewdata

import android.text.Spanned
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.Translation
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.shouldTrimStatus

sealed interface TranslationViewData {
    val data: Translation?

    data class Loaded(override val data: Translation) : TranslationViewData

    data object Loading : TranslationViewData {
        override val data: Translation?
            get() = null
    }
}

/**
 * Created by charlag on 11/07/2017.
 *
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a [StatusViewData.Concrete] or a [StatusViewData.Placeholder].
 */
sealed class StatusViewData {
    abstract val id: String
    var filterAction: Filter.Action = Filter.Action.NONE

    data class Concrete(
        val status: Status,
        val isExpanded: Boolean,
        val isShowingContent: Boolean,
        /**
         * Specifies whether the content of this post is currently limited in visibility to the first
         * 500 characters or not.
         *
         * @return Whether the post is collapsed or fully expanded.
         */
        val isCollapsed: Boolean,
        val isDetailed: Boolean = false,
        val translation: TranslationViewData? = null,
    ) : StatusViewData() {
        override val id: String
            get() = status.id

        val content: Spanned =
            (translation?.data?.content ?: actionable.content).parseAsMastodonHtml()

        val attachments: List<Attachment> =
            actionable.attachments.translated { translation -> map { it.translated(translation) } }

        val spoilerText: String =
            actionable.spoilerText.translated { translation -> translation.spoilerText ?: this }

        val poll = actionable.poll?.translated { translation ->
            val translatedOptionsText = translation.poll?.options?.map { option ->
                option.title
            } ?: return@translated this
            val translatedOptions = options.zip(translatedOptionsText) { option, translatedText ->
                option.copy(title = translatedText)
            }
            copy(options = translatedOptions)
        }

        /**
         * Specifies whether the content of this post is long enough to be automatically
         * collapsed or if it should show all content regardless.
         * Translated posts only show the button if the original post had it as well.
         *
         * @return Whether the post is collapsible or never collapsed.
         */
        val isCollapsible: Boolean = shouldTrimStatus(this.content) &&
            (translation == null || shouldTrimStatus(actionable.content.parseAsMastodonHtml()))

        val actionable: Status
            get() = status.actionableStatus

        val actionableId: String
            get() = status.actionableStatus.id

        val rebloggedAvatar: String?
            get() = if (status.reblog != null) {
                status.account.avatar
            } else {
                null
            }

        val rebloggingStatus: Status?
            get() = if (status.reblog != null) status else null

        /** Helper for Java */
        fun copyWithCollapsed(isCollapsed: Boolean): Concrete {
            return copy(isCollapsed = isCollapsed)
        }

        private fun Attachment.translated(translation: Translation): Attachment {
            val translatedDescription =
                translation.mediaAttachments.find { it.id == id }?.description
                    ?: return this
            return copy(description = translatedDescription)
        }

        private inline fun <T> T.translated(mapper: T.(Translation) -> T): T =
            if (translation is TranslationViewData.Loaded) {
                mapper(translation.data)
            } else {
                this
            }
    }

    data class Placeholder(
        override val id: String,
        val isLoading: Boolean
    ) : StatusViewData()

    fun asStatusOrNull() = this as? Concrete

    fun asPlaceholderOrNull() = this as? Placeholder
}
