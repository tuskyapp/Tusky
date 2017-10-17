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

package com.keylesspalace.tusky.adapter;

import android.graphics.drawable.Drawable;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.support.v7.widget.RecyclerView.LayoutParams;

import com.keylesspalace.tusky.R;

public class FooterViewHolder extends RecyclerView.ViewHolder {
    public enum State {
        EMPTY,
        END,
        LOADING
    }

    private View container;
    private ProgressBar progressBar;
    private TextView endMessage;

    FooterViewHolder(View itemView) {
        super(itemView);
        container = itemView.findViewById(R.id.footer_container);
        progressBar = itemView.findViewById(R.id.footer_progress_bar);
        endMessage = itemView.findViewById(R.id.footer_end_message);
        Drawable top = AppCompatResources.getDrawable(itemView.getContext(),
                R.drawable.elephant_friend);
        if (top != null) {
            top.setBounds(0, 0, top.getIntrinsicWidth() / 2, top.getIntrinsicHeight() / 2);
        }
        endMessage.setCompoundDrawables(null, top, null, null);
    }

    public void setState(State state) {
        switch (state) {
            case LOADING: {
                RecyclerView.LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT);
                container.setLayoutParams(layoutParams);
                container.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                endMessage.setVisibility(View.GONE);
                break;
            }
            case END: {
                RecyclerView.LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT);
                container.setLayoutParams(layoutParams);
                container.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                endMessage.setVisibility(View.GONE);
                break;
            }
            case EMPTY: {
                RecyclerView.LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT);
                container.setLayoutParams(layoutParams);
                container.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                endMessage.setVisibility(View.VISIBLE);
                break;
            }
        }
    }
}