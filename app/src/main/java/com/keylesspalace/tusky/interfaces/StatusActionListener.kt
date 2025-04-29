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
package com.keylesspalace.tusky.interfaces

import android.view.View
import at.connyduck.sparkbutton.SparkButton
import com.keylesspalace.tusky.entity.Status

interface StatusActionListener : LinkListener {
    fun onReply(position: Int)

    /**
     * Reblog the post at [position]
     * @param visibility The visibility to use for the reblog, if the user has already chosen it, null otherwise
     * @param button Optional button to animate
     */
    fun onReblog(reblog: Boolean, position: Int, visibility: Status.Visibility?, button: SparkButton? = null)

    /**
     * Favourite the post at [position]
     * @param button Optional button to animate
     */
    fun onFavourite(favourite: Boolean, position: Int, button: SparkButton? = null)
    fun onBookmark(bookmark: Boolean, position: Int)
    fun onMore(view: View, position: Int)
    fun onViewMedia(position: Int, attachmentIndex: Int, view: View?)
    fun onViewThread(position: Int)

    /**
     * Open reblog author for the status.
     * @param position At which position in the list status is located
     */
    fun onOpenReblog(position: Int)
    fun onExpandedChange(expanded: Boolean, position: Int)
    fun onContentHiddenChange(isShowing: Boolean, position: Int)
    fun onLoadMore(position: Int)

    /**
     * Called when the status [android.widget.ToggleButton] responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     * @param position    The position of the status in the list.
     */
    fun onContentCollapsedChange(isCollapsed: Boolean, position: Int)

    /**
     * called when the reblog count has been clicked
     * @param position The position of the status in the list.
     */
    fun onShowReblogs(position: Int) {}

    /**
     * called when the favourite count has been clicked
     * @param position The position of the status in the list.
     */
    fun onShowFavs(position: Int) {}

    fun onVoteInPoll(position: Int, choices: List<Int>)

    fun onShowPollResults(position: Int)

    fun onShowEdits(position: Int) {}

    fun clearWarningAction(position: Int)

    fun onUntranslate(position: Int)
}
