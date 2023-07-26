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

import android.os.Build
import android.text.Spanned
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.replaceCrashingCharacters
import com.keylesspalace.tusky.util.shouldTrimStatus

/**
 * Created by charlag on 11/07/2017.
 *
 * Class to represent data required to display either a status or a placeholder ("Load More" bar).
 * It is either a [StatusViewData.Concrete] or a [StatusViewData.Placeholder].
 * Can be created either from a ConversationStatusEntity, or by helpers in ViewDataUtils.
 */
sealed class StatusViewData {
    abstract val id: String
    var filterAction: Filter.Action = Filter.Action.NONE

    data class Concrete(
        /** The Mastodon-API level information about the status. */
        val status: Status,
        /**
         * If StatusViewData spoilerText is nonempty, specifies whether the text content of this post
         * is currently hidden.
         *
         * @return If true, post is shown. If false, it is hidden.
         */
        val isExpanded: Boolean,
        /**
         * Specifies whether attachments are currently hidden as sensitive.
         *
         * @return If true, attachments are shown. If false, they is hidden.
         */
        val isShowingContent: Boolean,
        /**
         * If StatusViewData isCollapsible, specifies whether the content of this post is currently
         * limited in visibility to the first characters or not.
         *
         * @return If true, post is collapsed. If false, it is fully expanded.
         */
        val isCollapsed: Boolean,
        /**
         * If true, the status is "big" (has been selected by the user for detailed display).
         */
        val isDetailed: Boolean = false
    ) : StatusViewData() {
        override val id: String
            get() = status.id

        /**
         * Specifies whether the content of this post is long enough to be automatically
         * collapsed or if it should show all content regardless. (See shouldTrimStatus())
         *
         * @return Whether the post is collapsible or never collapsed.
         */
        val isCollapsible: Boolean
        val content: Spanned
        /**
         * @return If nonempty, the spoiler/content warning text. If empty, there is no warning.
         */
        val spoilerText: String
        val username: String

        /**
         * @return The "true" status (same as status unless this is a reblog)
         */
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

        init {
            if (Build.VERSION.SDK_INT == 23) {
                // https://github.com/tuskyapp/Tusky/issues/563
                this.content = replaceCrashingCharacters(status.actionableStatus.content.parseAsMastodonHtml())
                this.spoilerText =
                    replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
                this.username =
                    replaceCrashingCharacters(status.actionableStatus.account.username).toString()
            } else {
                this.content = status.actionableStatus.content.parseAsMastodonHtml()
                this.spoilerText = status.actionableStatus.spoilerText
                this.username = status.actionableStatus.account.username
            }
            this.isCollapsible = shouldTrimStatus(this.content)
        }

        /** Helper for Java */
        fun copyWithCollapsed(isCollapsed: Boolean): Concrete {
            return copy(isCollapsed = isCollapsed)
        }
    }

    data class Placeholder(
        override val id: String,
        val isLoading: Boolean
    ) : StatusViewData()

    fun asStatusOrNull() = this as? Concrete

    fun asPlaceholderOrNull() = this as? Placeholder
}
