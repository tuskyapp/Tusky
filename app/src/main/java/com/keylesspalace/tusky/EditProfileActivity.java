package com.keylesspalace.tusky;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Profile;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends BaseActivity {
    private static final String TAG = "EditProfileActivity";
    private static final int MEDIA_PICK_RESULT = 1;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    @BindView(R.id.edit_profile_display_name) EditText displayNameEditText;
    @BindView(R.id.edit_profile_note) EditText noteEditText;
    @BindView(R.id.edit_profile_avatar) Button avatarButton;
    @BindView(R.id.edit_profile_header) Button headerButton;
    @BindView(R.id.edit_profile_error) TextView errorText;

    private String priorDisplayName;
    private String priorNote;
    private boolean isAlreadySaving;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        if (savedInstanceState != null) {
            priorDisplayName = savedInstanceState.getString("priorDisplayName");
            priorNote = savedInstanceState.getString("priorNote");
            isAlreadySaving = savedInstanceState.getBoolean("isAlreadySaving");
        } else {
            priorDisplayName = null;
            priorNote = null;
            isAlreadySaving = false;
        }

        avatarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick();
            }
        });
        headerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMediaPick();
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
                displayNameEditText.setText(priorDisplayName);
                noteEditText.setText(priorNote);
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
        super.onSaveInstanceState(outState);
    }

    private void onAccountVerifyCredentialsFailed() {
        Log.e(TAG, "The account failed to load.");
    }

    private void onMediaPick() {
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
                    errorText.setText(R.string.error_media_upload_permission);
                }
                break;
            }
        }
    }

    private void initiateMediaPicking() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, MEDIA_PICK_RESULT);
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
        if (isAlreadySaving) {
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

        isAlreadySaving = true;

        Profile profile = new Profile();
        profile.displayName = newDisplayName;
        profile.note = newNote;
        profile.avatar = null;
        profile.header = null;
        mastodonAPI.accountUpdateCredentials(profile).enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
                if (!response.isSuccessful()) {
                    onSaveFailure();
                    return;
                }
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
        errorText.setText(getString(R.string.error_media_upload_sending));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PICK_RESULT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            Log.d(TAG, "picked: " + uri.toString());
        }
    }
}
