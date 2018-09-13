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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.keylesspalace.tusky.BuildConfig.APPLICATION_ID;

public class ViewVideoActivity extends BaseActivity {

    Handler handler = new Handler(Looper.getMainLooper());
    Toolbar toolbar;
    String url;
    String statusID;
    String statusURL;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static final String TAG = "ViewVideoActivity";
    public static final String URL_EXTRA = "url";
    public static final String STATUS_ID_EXTRA = "statusID";
    public static final String STATUS_URL_EXTRA = "statusURL";

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
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.action_download:
                    downloadVideo();
                    break;
                case R.id.action_open_status:
                    onOpenStatus();
                    break;
                case R.id.action_share_media:
                    shareVideo();
                    break;
            }
            return true;
        });

        url = getIntent().getStringExtra(URL_EXTRA);
        statusID = getIntent().getStringExtra(STATUS_ID_EXTRA);
        statusURL = getIntent().getStringExtra(STATUS_URL_EXTRA);

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

        getWindow().setStatusBarColor(Color.BLACK);

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_media_toolbar, menu);
        return true;
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

    private void downloadVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            String filename = new File(url).getName();
            String toastText = String.format(getResources().getString(R.string.download_image), filename);
            Toast.makeText(this.getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.allowScanningByMediaScanner();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    getString(R.string.app_name) + "/" + filename);

            downloadManager.enqueue(request);
        }
    }

    private void onOpenStatus() {
        startActivityWithSlideInAnimation(ViewThreadActivity.startIntent(this, statusID, statusURL));
    }

    private void shareVideo() {
        File directory = getApplicationContext().getExternalFilesDir("Tusky");
        if (directory == null || !(directory.exists())) {
            Log.e(TAG, "Error obtaining directory to save temporary media.");
            return;
        }

        Uri uri = Uri.parse(url);
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = mimeTypeMap.getFileExtensionFromUrl(url);
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
        String filename = String.format("Tusky_Share_Media_%s.%s",
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()),
                extension);
        File file = new File(directory, filename);

        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setDestinationUri(Uri.fromFile(file));
        request.setVisibleInDownloadsUi(false);
        downloadManager.enqueue(request);

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getApplicationContext(), APPLICATION_ID + ".fileprovider", file));
        sendIntent.setType(mimeType);
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_media_to)));
    }
}
