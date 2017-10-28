package com.keylesspalace.tusky;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Account;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AboutActivity extends BaseActivity {
    private Button appAccountButton;

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
        appAccountButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAccountButtonClick();
            }
        });
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
            public void onResponse(Call<List<Account>> call, Response<List<Account>> response) {
                if (response.isSuccessful()) {
                    List<Account> accountList = response.body();
                    if (!accountList.isEmpty()) {
                        String id = accountList.get(0).id;
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
            public void onFailure(Call<List<Account>> call, Throwable t) {
                onSearchFailed();
            }
        };
        mastodonApi.searchAccounts("Tusky@mastodon.social", true, null).enqueue(callback);
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
}
