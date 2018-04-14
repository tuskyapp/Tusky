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

package com.keylesspalace.tusky;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

public class ViewVideoActivity extends BaseActivity {

    Handler handler = new Handler(Looper.getMainLooper());
    Toolbar toolbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_video);

        final ProgressBar progressBar = findViewById(R.id.video_progress);
        VideoView videoView = findViewById(R.id.video_player);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(null);
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
        }

        String url = getIntent().getStringExtra("url");

        videoView.setVideoPath(url);
        MediaController controller = new MediaController(this);
        controller.setMediaPlayer(videoView);
        videoView.setMediaController(controller);
        videoView.requestFocus();
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                progressBar.setVisibility(View.GONE);
                mp.setLooping(true);
                hideToolbarAfterDelay();
            }
        });
        videoView.start();

        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    handler.removeCallbacksAndMessages(null);
                    toolbar.animate().cancel();
                    toolbar.setAlpha(1);
                    toolbar.setVisibility(View.VISIBLE);
                    hideToolbarAfterDelay();
                }
                return false;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    void hideToolbarAfterDelay() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toolbar.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        View decorView = getWindow().getDecorView();
                        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;
                        decorView.setSystemUiVisibility(uiOptions);
                        toolbar.setVisibility(View.INVISIBLE);
                        animation.removeListener(this);
                    }
                });
            }
        }, 3000);
    }
}
