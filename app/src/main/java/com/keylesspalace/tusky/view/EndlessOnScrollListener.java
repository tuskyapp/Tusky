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

package com.keylesspalace.tusky.view;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

public abstract class EndlessOnScrollListener extends RecyclerView.OnScrollListener {
    private static final int VISIBLE_THRESHOLD = 15;
    private int previousTotalItemCount;
    private LinearLayoutManager layoutManager;

    public EndlessOnScrollListener(LinearLayoutManager layoutManager) {
        this.layoutManager = layoutManager;
        previousTotalItemCount = 0;
    }

    @Override
    public void onScrolled(RecyclerView view, int dx, int dy) {
        int totalItemCount = layoutManager.getItemCount();
        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
        if (totalItemCount < previousTotalItemCount) {
            previousTotalItemCount = totalItemCount;

        }
        if (totalItemCount != previousTotalItemCount) {
            previousTotalItemCount = totalItemCount;
        }

        if (lastVisibleItemPosition + VISIBLE_THRESHOLD > totalItemCount) {
            onLoadMore(totalItemCount, view);
        }
    }

    public void reset() {
        previousTotalItemCount = 0;
    }

    public abstract void onLoadMore(int totalItemsCount, RecyclerView view);
}
