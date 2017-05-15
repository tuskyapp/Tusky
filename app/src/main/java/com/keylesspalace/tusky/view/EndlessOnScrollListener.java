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
    private int currentPage;
    private int previousTotalItemCount;
    private boolean loading;
    private int startingPageIndex;
    private LinearLayoutManager layoutManager;

    public EndlessOnScrollListener(LinearLayoutManager layoutManager) {
        this.layoutManager = layoutManager;
        currentPage = 0;
        previousTotalItemCount = 0;
        loading = true;
        startingPageIndex = 0;
    }

    @Override
    public void onScrolled(RecyclerView view, int dx, int dy) {
        int totalItemCount = layoutManager.getItemCount();
        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();
        if (totalItemCount < previousTotalItemCount) {
            currentPage = startingPageIndex;
            previousTotalItemCount = totalItemCount;
            if (totalItemCount == 0) {
                loading = true;
            }
        }
        if (loading && totalItemCount > previousTotalItemCount) {
            loading = false;
            previousTotalItemCount = totalItemCount;
        }
        if (!loading && lastVisibleItemPosition + VISIBLE_THRESHOLD > totalItemCount) {
            currentPage++;
            onLoadMore(currentPage, totalItemCount, view);
            loading = true;
        }
    }

    public void reset() {
        currentPage = startingPageIndex;
        previousTotalItemCount = 0;
        loading = true;
    }

    public abstract void onLoadMore(int page, int totalItemsCount, RecyclerView view);
}
