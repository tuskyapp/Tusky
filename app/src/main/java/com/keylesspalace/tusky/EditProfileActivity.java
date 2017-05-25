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
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Profile;
import com.keylesspalace.tusky.util.IOUtils;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends BaseActivity {
    private static final String TAG = "EditProfileActivity";
    private static final int AVATAR_PICK_RESULT = 1;
    private static final int HEADER_PICK_RESULT = 2;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int AVATAR_WIDTH = 120;
    private static final int AVATAR_HEIGHT = 120;
    private static final int HEADER_WIDTH = 700;
    private static final int HEADER_HEIGHT = 335;

    private enum PickType {
        NOTHING,
        AVATAR,
        HEADER
    }

    @BindView(R.id.edit_profile_header) ImageButton headerButton;
    @BindView(R.id.edit_profile_header_preview) ImageView headerPreview;
    @BindView(R.id.edit_profile_header_progress) ProgressBar headerProgress;
    @BindView(R.id.edit_profile_avatar) ImageButton avatarButton;
    @BindView(R.id.edit_profile_avatar_preview) ImageView avatarPreview;
    @BindView(R.id.edit_profile_avatar_progress) ProgressBar avatarProgress;
    @BindView(R.id.edit_profile_display_name) EditText displayNameEditText;
    @BindView(R.id.edit_profile_note) EditText noteEditText;
    @BindView(R.id.edit_profile_save_progress) ProgressBar saveProgress;

    private String priorDisplayName;
    private String priorNote;
    private boolean isAlreadySaving;
    private PickType currentlyPicking;
    private String avatarBase64;
    private String headerBase64;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.title_edit_profile));
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        if (savedInstanceState != null) {
            priorDisplayName = savedInstanceState.getString("priorDisplayName");
            priorNote = savedInstanceState.getString("priorNote");
            isAlreadySaving = savedInstanceState.getBoolean("isAlreadySaving");
            currentlyPicking = (PickType) savedInstanceState.getSerializable("currentlyPicking");
            avatarBase64 = savedInstanceState.getString("avatarBase64");
            headerBase64 = savedInstanceState.getString("headerBase64");
        } else {
            priorDisplayName = null;
            priorNote = null;
            isAlreadySaving = false;
            currentlyPicking = PickType.NOTHING;
            avatarBase64 = null;
            headerBase64 = null;
        }



        avatarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick(PickType.AVATAR);
            }
        });
        headerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick(PickType.HEADER);
            }
        });

        avatarPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                avatarPreview.setImageBitmap(null);
                avatarPreview.setVisibility(View.INVISIBLE);
                avatarBase64 = null;
            }
        });
        headerPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                headerPreview.setImageBitmap(null);
                headerPreview.setVisibility(View.INVISIBLE);
                headerBase64 = null;
            }
        });

        mastodonAPI.accountVerifyCredentials().enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
                if (!response.isSuccessful()) {
                    onAccountVerifyCredentialsFailed();
                    return;
                }
                Account me = response.body();
                priorDisplayName = me.getDisplayName();
                priorNote = me.note.toString();
                CircularImageView avatar = (CircularImageView) findViewById(R.id.edit_profile_avatar_preview);
                ImageView header = (ImageView) findViewById(R.id.edit_profile_header_preview);

                displayNameEditText.setText(priorDisplayName);
                noteEditText.setText(priorNote);
                Picasso.with(avatar.getContext())
                        .load(me.avatar)
                        .placeholder(R.drawable.avatar_default)
                        .error(R.drawable.avatar_error)
                        .into(avatar);
                Picasso.with(header.getContext())
                        .load(me.header)
                        .placeholder(R.drawable.account_header_default)
                        .into(header);
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onAccountVerifyCredentialsFailed();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("priorDisplayName", priorDisplayName);
        outState.putString("priorNote", priorNote);
        outState.putBoolean("isAlreadySaving", isAlreadySaving);
        outState.putSerializable("currentlyPicking", currentlyPicking);
        outState.putString("avatarBase64", avatarBase64);
        outState.putString("headerBase64", headerBase64);
        super.onSaveInstanceState(outState);
    }

    private void onAccountVerifyCredentialsFailed() {
        Log.e(TAG, "The account failed to load.");
    }

    private void onMediaPick(PickType pickType) {
        if (currentlyPicking != PickType.NOTHING) {
            // Ignore inputs if another pick operation is still occurring.
            return;
        }
        currentlyPicking = pickType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                    PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            initiateMediaPicking();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initiateMediaPicking();
                } else {
                    endMediaPicking();
                    Snackbar.make(avatarButton, R.string.error_media_upload_permission,
                            Snackbar.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private void initiateMediaPicking() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        switch (currentlyPicking) {
            case AVATAR: { startActivityForResult(intent, AVATAR_PICK_RESULT); break; }
            case HEADER: { startActivityForResult(intent, HEADER_PICK_RESULT); break; }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit_profile_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.action_save: {
                save();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        if (isAlreadySaving || currentlyPicking != PickType.NOTHING) {
            return;
        }
        String newDisplayName = displayNameEditText.getText().toString();
        if (newDisplayName.isEmpty()) {
            displayNameEditText.setError(getString(R.string.error_empty));
            return;
        }
        if (priorDisplayName != null && priorDisplayName.equals(newDisplayName)) {
            // If it's not any different, don't patch it.
            newDisplayName = null;
        }

        String newNote = noteEditText.getText().toString();
        if (newNote.isEmpty()) {
            noteEditText.setError(getString(R.string.error_empty));
            return;
        }
        if (priorNote != null && priorNote.equals(newNote)) {
            // If it's not any different, don't patch it.
            newNote = null;
        }
        if (newDisplayName == null && newNote == null && avatarBase64 == null
                && headerBase64 == null) {
            // If nothing is changed, then there's nothing to save.
            return;
        }

        saveProgress.setVisibility(View.VISIBLE);

        isAlreadySaving = true;

        Profile profile = new Profile();
        profile.displayName = newDisplayName;
        profile.note = newNote;
        profile.avatar = avatarBase64;
        profile.header = headerBase64;
        mastodonAPI.accountUpdateCredentials(profile).enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
                if (!response.isSuccessful()) {
                    onSaveFailure();
                    return;
                }
                getPrivatePreferences().edit()
                        .putBoolean("refreshProfileHeader", true)
                        .apply();
                finish();
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onSaveFailure();
            }
        });
    }

    private void onSaveFailure() {
        isAlreadySaving = false;
        Snackbar.make(avatarButton, R.string.error_media_upload_sending, Snackbar.LENGTH_LONG)
                .show();
        saveProgress.setVisibility(View.GONE);
    }

    private void beginMediaPicking() {
        switch (currentlyPicking) {
            case AVATAR: {
                avatarProgress.setVisibility(View.VISIBLE);
                avatarPreview.setVisibility(View.INVISIBLE);
                break;
            }
            case HEADER: {
                headerProgress.setVisibility(View.VISIBLE);
                headerPreview.setVisibility(View.INVISIBLE);
                break;
            }
        }
    }

    private void endMediaPicking() {
        switch (currentlyPicking) {
            case AVATAR: {
                avatarProgress.setVisibility(View.GONE);
                break;
            }
            case HEADER: {
                headerProgress.setVisibility(View.GONE);
                break;
            }
        }
        currentlyPicking = PickType.NOTHING;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case AVATAR_PICK_RESULT: {
                if (resultCode == RESULT_OK && data != null) {
                    CropImage.activity(data.getData())
                            .setInitialCropWindowPaddingRatio(0)
                            .setAspectRatio(AVATAR_WIDTH, AVATAR_HEIGHT)
                            .start(this);
                } else {
                    endMediaPicking();
                }
                break;
            }
            case HEADER_PICK_RESULT: {
                if (resultCode == RESULT_OK && data != null) {
                    CropImage.activity(data.getData())
                            .setInitialCropWindowPaddingRatio(0)
                            .setAspectRatio(HEADER_WIDTH, HEADER_HEIGHT)
                            .start(this);
                } else {
                    endMediaPicking();
                }
                break;
            }
            case CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE: {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                if (resultCode == RESULT_OK) {
                    beginResize(result.getUri());
                } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                    onResizeFailure();
                }
                break;
            }
        }
    }

    private void beginResize(Uri uri) {
        beginMediaPicking();
        int width, height;
        switch (currentlyPicking) {
            default: {
                throw new AssertionError("PickType not set.");
            }
            case AVATAR: {
                width = AVATAR_WIDTH;
                height = AVATAR_HEIGHT;
                break;
            }
            case HEADER: {
                width = HEADER_WIDTH;
                height = HEADER_HEIGHT;
                break;
            }
        }
        new ResizeImageTask(getContentResolver(), width, height, new ResizeImageTask.Listener() {
                @Override
                public void onSuccess(List<Bitmap> contentList) {
                    Bitmap bitmap = contentList.get(0);
                    PickType pickType = currentlyPicking;
                    endMediaPicking();
                    switch (pickType) {
                        case AVATAR: {
                            avatarPreview.setImageBitmap(bitmap);
                            avatarPreview.setVisibility(View.VISIBLE);
                            avatarBase64 = bitmapToBase64(bitmap);
                            break;
                        }
                        case HEADER: {
                            headerPreview.setImageBitmap(bitmap);
                            headerPreview.setVisibility(View.VISIBLE);
                            headerBase64 = bitmapToBase64(bitmap);
                            break;
                        }
                    }
                }

                @Override
                public void onFailure() {
                    onResizeFailure();
                }
            }).execute(uri);
    }

    private void onResizeFailure() {
        Snackbar.make(avatarButton, R.string.error_media_upload_sending, Snackbar.LENGTH_LONG)
                .show();
        endMediaPicking();
    }

    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        IOUtils.closeQuietly(stream);
        return "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private static class ResizeImageTask extends AsyncTask<Uri, Void, Boolean> {
        private ContentResolver contentResolver;
        private int resizeWidth;
        private int resizeHeight;
        private Listener listener;
        private List<Bitmap> resultList;

        ResizeImageTask(ContentResolver contentResolver, int width, int height, Listener listener) {
            this.contentResolver = contentResolver;
            this.resizeWidth = width;
            this.resizeHeight = height;
            this.listener = listener;
        }

        @Override
        protected Boolean doInBackground(Uri... uris) {
            resultList = new ArrayList<>();
            for (Uri uri : uris) {
                InputStream inputStream;
                try {
                    inputStream = contentResolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    Log.d(TAG, Log.getStackTraceString(e));
                    return false;
                }
                Bitmap sourceBitmap;
                try {
                    sourceBitmap = BitmapFactory.decodeStream(inputStream, null, null);
                } catch (OutOfMemoryError error) {
                    Log.d(TAG, Log.getStackTraceString(error));
                    return false;
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
                if (sourceBitmap == null) {
                    return false;
                }
                Bitmap bitmap = Bitmap.createScaledBitmap(sourceBitmap, resizeWidth, resizeHeight,
                        false);
                sourceBitmap.recycle();
                if (bitmap == null) {
                    return false;
                }
                resultList.add(bitmap);
                if (isCancelled()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean successful) {
            if (successful) {
                listener.onSuccess(resultList);
            } else {
                listener.onFailure();
            }
            super.onPostExecute(successful);
        }

        interface Listener {
            void onSuccess(List<Bitmap> contentList);
            void onFailure();
        }
    }
}
