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

package com.keylesspalace.tusky.interfaces;

import android.view.View;

import com.keylesspalace.tusky.entity.Attachment;

public interface StatusActionListener extends LinkListener {
    void onReply(int position);
    void onReblog(final boolean reblog, final int position);
    void onFavourite(final boolean favourite, final int position);
    void onMore(View view, final int position);
    void onViewMedia(String[] urls, int index, Attachment.Type type, View view);
    void onViewThread(int position);
    void onOpenReblog(int position);
    void onExpandedChange(boolean expanded, int position);
    void onContentHiddenChange(boolean isShowing, int position);
    void onLoadMore(int position);
}
