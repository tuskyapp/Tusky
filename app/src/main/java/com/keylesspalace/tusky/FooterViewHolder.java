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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class FooterViewHolder extends RecyclerView.ViewHolder {
    private LinearLayout retryBar;
    private TextView retryMessage;
    private Button retry;
    private ProgressBar progressBar;

    public FooterViewHolder(View itemView) {
        super(itemView);
        retryBar = (LinearLayout) itemView.findViewById(R.id.footer_retry_bar);
        retryMessage = (TextView) itemView.findViewById(R.id.footer_retry_message);
        retry = (Button) itemView.findViewById(R.id.footer_retry_button);
        progressBar = (ProgressBar) itemView.findViewById(R.id.footer_progress_bar);
        progressBar.setIndeterminate(true);
    }

    public void setupButton(final FooterActionListener listener) {
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onLoadMore();
            }
        });
    }

    public void setRetryMessage(int messageId) {
        retryMessage.setText(messageId);
    }

    public void showRetry(boolean show) {
        if (!show) {
            retryBar.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            retryBar.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }
    }
}