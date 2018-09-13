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
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.support.v4.content.FileProvider;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import com.keylesspalace.tusky.entity.Attachment;
import com.keylesspalace.tusky.fragment.ViewMediaFragment;
import com.keylesspalace.tusky.pager.AvatarImagePagerAdapter;
import com.keylesspalace.tusky.pager.ImagePagerAdapter;
import com.keylesspalace.tusky.view.ImageViewPager;
import com.keylesspalace.tusky.viewdata.AttachmentViewData;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function0;

import static com.keylesspalace.tusky.BuildConfig.APPLICATION_ID;

public final class ViewMediaActivity extends BaseActivity
        implements ViewMediaFragment.PhotoActionsListener {
    private static final String EXTRA_ATTACHMENTS = "attachments";
    private static final String EXTRA_ATTACHMENT_INDEX = "index";
    private static final String EXTRA_AVATAR_URL = "avatar";
    private static final String TAG = "ViewMediaActivity";

    public static Intent newIntent(Context context, List<AttachmentViewData> attachments, int index) {
        final Intent intent = new Intent(context, ViewMediaActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_ATTACHMENTS, new ArrayList<>(attachments));
        intent.putExtra(EXTRA_ATTACHMENT_INDEX, index);
        return intent;
    }

    public static Intent newAvatarIntent(Context context, String url) {
        final Intent intent = new Intent(context, ViewMediaActivity.class);
        intent.putExtra(EXTRA_AVATAR_URL, url);
        return intent;
    }

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    private ImageViewPager viewPager;
    private View anyView;
    private Toolbar toolbar;

    private List<AttachmentViewData> attachments;

    private boolean isToolbarVisible = true;
    private final List<ToolbarVisibilityListener> toolbarVisibilityListeners = new ArrayList<>();

    public interface ToolbarVisibilityListener {
        void onToolbarVisiblityChanged(boolean isVisible);
    }

    public Function0 addToolbarVisibilityListener(ToolbarVisibilityListener listener) {
        this.toolbarVisibilityListeners.add(listener);
        listener.onToolbarVisiblityChanged(isToolbarVisible);
        return () -> toolbarVisibilityListeners.remove(listener);
    }

    public boolean isToolbarVisible() {
        return isToolbarVisible;
    }

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
        attachments = intent.getParcelableArrayListExtra(EXTRA_ATTACHMENTS);
        int initialPosition = intent.getIntExtra(EXTRA_ATTACHMENT_INDEX, 0);

        final PagerAdapter adapter;

        if(attachments != null) {
            List<Attachment> realAttachs =
                    CollectionsKt.map(attachments, AttachmentViewData::getAttachment);
            // Setup the view pager.
            adapter = new ImagePagerAdapter(getSupportFragmentManager(),
                    realAttachs, initialPosition);

        } else {
            String avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL);

            if(avatarUrl == null) {
                throw new IllegalArgumentException("attachment list or avatar url has to be set");
            }

            adapter = new AvatarImagePagerAdapter(getSupportFragmentManager(), avatarUrl);
        }

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
        toolbar.setNavigationOnClickListener(v -> supportFinishAfterTransition());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case R.id.action_download:
                    downloadImage();
                    break;
                case R.id.action_open_status:
                    onOpenStatus();
                    break;
                case R.id.action_share_media:
                    shareImage();
                    break;
            }
            return true;
        });

        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        decorView.setSystemUiVisibility(uiOptions);
        getWindow().setStatusBarColor(Color.BLACK);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(attachments != null) {
            getMenuInflater().inflate(R.menu.view_media_toolbar, menu);
            return true;
        } else {
            return false;
        }
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
        for (ToolbarVisibilityListener listener : toolbarVisibilityListeners) {
            listener.onToolbarVisiblityChanged(isToolbarVisible);
        }
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
                            v -> downloadImage());
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
            String url = attachments.get(viewPager.getCurrentItem()).getAttachment().getUrl();
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

    private void onOpenStatus() {
        final AttachmentViewData attach = attachments.get(viewPager.getCurrentItem());
        startActivityWithSlideInAnimation(ViewThreadActivity.startIntent(this, attach.getStatusId(),
                attach.getStatusUrl()));
    }

    private void shareImage() {
        File directory = getApplicationContext().getExternalFilesDir("Tusky");
        if (directory == null || !(directory.exists())) {
            Log.e(TAG, "Error obtaining directory to save temporary media.");
            return;
        }

        Attachment attachment = attachments.get(viewPager.getCurrentItem()).getAttachment();
        Context context = getApplicationContext();
        Picasso.with(context).load(Uri.parse(attachment.getUrl())).into(new Target(){
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                try {
                    String filename = String.format("Tusky_Share_Media_%s.png",
                            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()));
                    File file = new File(directory, filename);
                    FileOutputStream stream = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.close();

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, APPLICATION_ID + ".fileprovider", file));
                    sendIntent.setType("image/png");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_media_to)));
                } catch (FileNotFoundException fnfe) {
                    Log.e(TAG, "Error writing temporary media.");
                } catch (IOException ioe) {
                    Log.e(TAG, "Error writing temporary media.");
                }
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                Log.e(TAG, "Error loading temporary media.");
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) { }
        });
    }
}
