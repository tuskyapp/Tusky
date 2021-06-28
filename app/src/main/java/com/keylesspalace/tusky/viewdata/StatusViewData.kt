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
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.keylesspalace.tusky.entity.Status

/**
 * Created by charlag on 11/07/2017.
 *
 *
 * Class to represent data required to display either a notification or a placeholder.
 * It is either a [StatusViewData.Concrete] or a [StatusViewData.Placeholder].
 */
sealed class StatusViewData private constructor() {
    abstract val viewDataId: Long

    data class Concrete(
        val status: Status,
        val isExpanded: Boolean,
        val isShowingContent: Boolean,
        /**
         * Specifies whether the content of this post is allowed to be collapsed or if it should show
         * all content regardless.
         *
         * @return Whether the post is collapsible or never collapsed.
         */
        val isCollapsible: Boolean,
        /**
         * Specifies whether the content of this post is currently limited in visibility to the first
         * 500 characters or not.
         *
         * @return Whether the post is collapsed or fully expanded.
         */
        /** Whether the status meets the requirement to be collapse  */
        val isCollapsed: Boolean,
    ) : StatusViewData() {
        override val viewDataId: Long
            get() = status.id.hashCode().toLong()

        val content: Spanned
        val spoilerText: String
        val username: String

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
                this.content = replaceCrashingCharacters(status.actionableStatus.content)
                this.spoilerText =
                    replaceCrashingCharacters(status.actionableStatus.spoilerText).toString()
                this.username =
                    replaceCrashingCharacters(status.actionableStatus.account.username).toString()
            } else {
                this.content = status.actionableStatus.content
                this.spoilerText = status.actionableStatus.spoilerText
                this.username = status.actionableStatus.account.username
            }
        }

        companion object {
            private const val SOFT_HYPHEN = '\u00ad'
            private const val ASCII_HYPHEN = '-'
            fun replaceCrashingCharacters(content: Spanned): Spanned {
                return replaceCrashingCharacters(content as CharSequence) as Spanned
            }

            fun replaceCrashingCharacters(content: CharSequence): CharSequence? {
                var replacing = false
                var builder: SpannableStringBuilder? = null
                val length = content.length
                for (index in 0 until length) {
                    val character = content[index]

                    // If there are more than one or two, switch to a map
                    if (character == SOFT_HYPHEN) {
                        if (!replacing) {
                            replacing = true
                            builder = SpannableStringBuilder(content, 0, index)
                        }
                        builder!!.append(ASCII_HYPHEN)
                    } else if (replacing) {
                        builder!!.append(character)
                    }
                }
                return if (replacing) builder else content
            }
        }

        val id: String
            get() = status.id

        /** Helper for Java */
        fun copyWithStatus(status: Status): Concrete {
            return copy(status = status)
        }

        /** Helper for Java */
        fun copyWithExpanded(isExpanded: Boolean): Concrete {
            return copy(isExpanded = isExpanded)
        }

        /** Helper for Java */
        fun copyWithShowingContent(isShowingContent: Boolean): Concrete {
            return copy(isShowingContent = isShowingContent)
        }

        /** Helper for Java */
        fun copyWIthCollapsed(isCollapsed: Boolean): Concrete {
            return copy(isCollapsed = isCollapsed)
        }
    }

    data class Placeholder(val id: String, val isLoading: Boolean) : StatusViewData() {
        override val viewDataId: Long
            get() = id.hashCode().toLong()
    }

    fun asStatusOrNull() = this as? Concrete

    fun asPlaceholderOrNull() = this as? Placeholder
}
