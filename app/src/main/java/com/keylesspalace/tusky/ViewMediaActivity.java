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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.keylesspalace.tusky.fragment.ViewMediaFragment;
import com.keylesspalace.tusky.pager.ImagePagerAdapter;
import com.keylesspalace.tusky.view.ImageViewPager;

import java.io.File;

public class ViewMediaActivity extends BaseActivity implements ViewMediaFragment.PhotoActionsListener {
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private ImageViewPager viewPager;
    private View anyView;
    private String[] imageUrls;
    private Toolbar toolbar;

    private boolean isToolbarVisible = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_media);

        supportPostponeEnterTransition();

        // Obtain the views.
        toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.view_pager);
        anyView = toolbar;

        // Gather the parameters.
        Intent intent = getIntent();
        imageUrls = intent.getStringArrayExtra("urls");
        int initialPosition = intent.getIntExtra("urlIndex", 0);

        // Setup the view pager.
        final ImagePagerAdapter adapter = new ImagePagerAdapter(getSupportFragmentManager(),
                imageUrls, initialPosition);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(initialPosition);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                CharSequence title = adapter.getPageTitle(position);
                toolbar.setTitle(title);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        // Setup the toolbar.
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(adapter.getPageTitle(initialPosition));
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                supportFinishAfterTransition();
            }
        });
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                switch (id) {
                    case R.id.action_download:
                        downloadImage();
                        break;
                }
                return true;
            }
        });

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        decorView.setSystemUiVisibility(uiOptions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_media_toolbar, menu);
        // Manually tint all action buttons, because the theme overlay doesn't handle them properly.
        for (int i = 0; i < menu.size(); i++) {
            Drawable drawable = menu.getItem(i).getIcon();
            if (drawable != null) {
                drawable.mutate();
                int color = ContextCompat.getColor(this, R.color.text_color_primary_dark);
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
        return true;
    }

    @Override
    public void onBringUp() {
        supportStartPostponedEnterTransition();
    }

    @Override
    public void onDismiss() {
        supportFinishAfterTransition();
    }

    @Override
    public void onPhotoTap() {
        isToolbarVisible = !isToolbarVisible;
        final int visibility = isToolbarVisible ? View.VISIBLE : View.INVISIBLE;
        int alpha = isToolbarVisible ? 1 : 0;
        toolbar.animate().alpha(alpha)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        toolbar.setVisibility(visibility);
                        animation.removeListener(this);
                    }
                })
                .start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadImage();
                } else {
                    doErrorDialog(R.string.error_media_download_permission, R.string.action_retry,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    downloadImage();
                                }
                            });
                }
                break;
            }
        }
    }

    private void doErrorDialog(@StringRes int descriptionId, @StringRes int actionId,
                               View.OnClickListener listener) {
        if (anyView != null) {
            Snackbar bar = Snackbar.make(anyView, getString(descriptionId),
                    Snackbar.LENGTH_SHORT);
            bar.setAction(actionId, listener);
            bar.show();
        }
    }

    private void downloadImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            String url = imageUrls[viewPager.getCurrentItem()];
            Uri uri = Uri.parse(url);

            String filename = new File(url).getName();

            String toastText = String.format(getResources().getString(R.string.download_image),
                    filename);
            Toast.makeText(this.getApplicationContext(), toastText, Toast.LENGTH_SHORT).show();

            DownloadManager downloadManager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.allowScanningByMediaScanner();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    getString(R.string.app_name) + "/" + filename);

            downloadManager.enqueue(request);
        }
    }
}
