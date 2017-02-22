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
    private View retryBar;
    private TextView retryMessage;
    private Button retry;
    private ProgressBar progressBar;
    private TextView endOfTimelineMessage;

    enum State {
        LOADING,
        RETRY,
        END_OF_TIMELINE,
    }

    FooterViewHolder(View itemView) {
        super(itemView);
        retryBar = itemView.findViewById(R.id.footer_retry_bar);
        retryMessage = (TextView) itemView.findViewById(R.id.footer_retry_message);
        retry = (Button) itemView.findViewById(R.id.footer_retry_button);
        progressBar = (ProgressBar) itemView.findViewById(R.id.footer_progress_bar);
        progressBar.setIndeterminate(true);
        endOfTimelineMessage = (TextView) itemView.findViewById(R.id.footer_end_of_timeline_text);
    }

    void setupButton(final FooterActionListener listener) {
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onLoadMore();
            }
        });
    }

    void setRetryMessage(int messageId) {
        retryMessage.setText(messageId);
    }

    void setEndOfTimelineMessage(int messageId) {
        endOfTimelineMessage.setText(messageId);
    }

    void setState(State state) {
        switch (state) {
            case LOADING: {
                retryBar.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                endOfTimelineMessage.setVisibility(View.GONE);
                break;
            }
            case RETRY: {
                retryBar.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                endOfTimelineMessage.setVisibility(View.GONE);
                break;
            }
            case END_OF_TIMELINE: {
                retryBar.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                endOfTimelineMessage.setVisibility(View.VISIBLE);
                break;
            }
        }
    }
}