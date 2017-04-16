package com.keylesspalace.tusky;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;

import com.keylesspalace.tusky.entity.Account;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends BaseActivity {
    private static final String TAG = "EditProfileActivity";

    @BindView(R.id.edit_profile_display_name) EditText displayNameEditText;
    @BindView(R.id.edit_profile_note) EditText noteEditText;
    @BindView(R.id.edit_profile_avatar) Button avatarButton;
    @BindView(R.id.edit_profile_header) Button headerButton;

    private String priorDisplayName;
    private String priorNote;

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
        } else {
            priorDisplayName = null;
            priorNote = null;
        }

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
                noteEditText.setText(HtmlUtils.fromHtml(priorNote));
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
        super.onSaveInstanceState(outState);
    }

    private void onAccountVerifyCredentialsFailed() {
        Log.e(TAG, "The account failed to load.");
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
        String newDisplayName = displayNameEditText.getText().toString();
        if (newDisplayName.isEmpty()) {
            displayNameEditText.setError(getString(R.string.error_empty));
            return;
        }
        if (priorDisplayName != null && priorDisplayName.equals(newDisplayName)) {
            // If it's not any different, don't patch it.
            newDisplayName = null;
        }

        String newNote = HtmlUtils.toHtml(noteEditText.getText());
        if (newNote.isEmpty()) {
            noteEditText.setError(getString(R.string.error_empty));
            return;
        }
        if (priorNote != null && priorNote.equals(newNote)) {
            // If it's not any different, don't patch it.
            newNote = null;
        }

        mastodonAPI.accountUpdateCredentials(newDisplayName, newNote, null, null);
    }
}
