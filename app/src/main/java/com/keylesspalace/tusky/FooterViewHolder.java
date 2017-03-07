/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

class FooterViewHolder extends RecyclerView.ViewHolder {
    private ProgressBar progressBar;

    enum State {
        LOADING,
        RETRY,
        END_OF_TIMELINE,
    }

    FooterViewHolder(View itemView) {
        super(itemView);
        progressBar = (ProgressBar) itemView.findViewById(R.id.footer_progress_bar);
        progressBar.setIndeterminate(true);
    }

    void setState(State state) {
        switch (state) {
            case LOADING: {
                progressBar.setVisibility(View.VISIBLE);
                break;
            }
            case RETRY: {
                progressBar.setVisibility(View.GONE);
                break;
            }
            case END_OF_TIMELINE: {
                progressBar.setVisibility(View.GONE);
                break;
            }
        }
    }
}