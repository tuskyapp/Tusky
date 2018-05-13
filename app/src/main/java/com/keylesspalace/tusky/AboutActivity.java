package com.keylesspalace.tusky;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.network.MastodonApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AboutActivity extends BaseActivity implements Injectable {
    private Button appAccountButton;

    @Inject
    public MastodonApi mastodonApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        setTitle(R.string.about_title_activity);

        TextView versionTextView = findViewById(R.id.versionTV);
        String versionName = BuildConfig.VERSION_NAME;
        String versionFormat = getString(R.string.about_tusky_version);
        versionTextView.setText(String.format(versionFormat, versionName));

        appAccountButton = findViewById(R.id.tusky_profile_button);
        appAccountButton.setOnClickListener(v -> onAccountButtonClick());
        setupAboutEmoji();
    }

    private void onAccountButtonClick() {
        String appAccountId = getPrivatePreferences().getString("appAccountId", null);
        if (appAccountId != null) {
            viewAccount(appAccountId);
        } else {
            searchForAccountThenViewIt();
        }
    }

    private void viewAccount(String id) {
        Intent intent = new Intent(this, AccountActivity.class);
        intent.putExtra("id", id);
        startActivity(intent);
    }

    private void searchForAccountThenViewIt() {
        Callback<List<Account>> callback = new Callback<List<Account>>() {
            @Override
            public void onResponse(@NonNull Call<List<Account>> call, @NonNull Response<List<Account>> response) {
                if (response.isSuccessful()) {
                    List<Account> accountList = response.body();
                    if (accountList != null && !accountList.isEmpty()) {
                        String id = accountList.get(0).getId();
                        getPrivatePreferences().edit()
                                .putString("appAccountId", id)
                                .apply();
                        viewAccount(id);
                    } else {
                        onSearchFailed();
                    }
                } else {
                    onSearchFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Account>> call, @NonNull Throwable t) {
                onSearchFailed();
            }
        };
        mastodonApi.searchAccounts(null, null, "Tusky@mastodon.social", true, null).enqueue(callback);
    }

    private void onSearchFailed() {
        Snackbar.make(appAccountButton, R.string.error_generic, Snackbar.LENGTH_LONG).show();
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

    private void setupAboutEmoji() {
        // Inflate the TextView containing the Apache 2.0 license text.
        TextView apacheView = findViewById(R.id.license_apache);
        BufferedReader reader = null;
        try {
            InputStream apacheLicense = getAssets().open("LICENSE_APACHE");
            StringBuilder builder = new StringBuilder();
            reader = new BufferedReader(
                    new InputStreamReader(apacheLicense, "UTF-8"));
            String line;
            while((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
            reader.close();
            apacheView.setText(builder);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Set up the button action
        ImageButton expand = findViewById(R.id.about_blobmoji_expand);
        expand.setOnClickListener(v ->
        {
            if(apacheView.getVisibility() == View.GONE) {
                apacheView.setVisibility(View.VISIBLE);
                ((ImageButton) v).setImageResource(R.drawable.ic_arrow_drop_up_black_24dp);
            }
            else {
                apacheView.setVisibility(View.GONE);
                ((ImageButton) v).setImageResource(R.drawable.ic_arrow_drop_down_black_24dp);
            }
        });
    }
}
