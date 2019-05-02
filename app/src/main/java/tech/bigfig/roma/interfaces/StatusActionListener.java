/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.interfaces;

import android.view.View;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface StatusActionListener extends LinkListener {
    void onReply(int position);
    void onReblog(final boolean reblog, final int position);
    void onFavourite(final boolean favourite, final int position);
    void onMore(@NonNull View view, final int position);
    void onViewMedia(int position, int attachmentIndex, @Nullable View view);
    void onViewThread(int position);

    /**
     * Open reblog author for the status.
     * @param position At which position in the list status is located
     */
    void onOpenReblog(int position);
    void onExpandedChange(boolean expanded, int position);
    void onContentHiddenChange(boolean isShowing, int position);
    void onLoadMore(int position);

    /**
     * Called when the status {@link android.widget.ToggleButton} responsible for collapsing long
     * status content is interacted with.
     *
     * @param isCollapsed Whether the status content is shown in a collapsed state or fully.
     * @param position    The position of the status in the list.
     */
    void onContentCollapsedChange(boolean isCollapsed, int position);

    /**
     * called when the reblog count has been clicked
     * @param position The position of the status in the list.
     */
    default void onShowReblogs(int position) {}

    /**
     * called when the favourite count has been clicked
     * @param position The position of the status in the list.
     */
    default void onShowFavs(int position) {}

    void onVoteInPoll(int position, @NonNull List<Integer> choices);

}
