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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.AttrRes;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Relationship;
import com.pkmmte.view.CircularImageView;
import com.squareup.picasso.Picasso;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AccountActivity extends BaseActivity {
    private static final String TAG = "AccountActivity"; // logging tag

    private String accountId;
    private boolean following = false;
    private boolean blocking = false;
    private boolean muting = false;
    private boolean isSelf;
    private TabLayout tabLayout;
    private Account loadedAccount;

    @BindView(R.id.account_locked) ImageView accountLockedView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            accountId = savedInstanceState.getString("accountId");
        } else {
            Intent intent = getIntent();
            accountId = intent.getStringExtra("id");
        }

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        String loggedInAccountId = preferences.getString("loggedInAccountId", null);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setTitle(null);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        // Add a listener to change the toolbar icon color when it enters/exits its collapsed state.
        AppBarLayout appBarLayout = (AppBarLayout) findViewById(R.id.account_app_bar_layout);
        final CollapsingToolbarLayout collapsingToolbar =
                (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @AttrRes int priorAttribute = R.attr.account_toolbar_icon_tint_uncollapsed;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                @AttrRes int attribute;
                if (collapsingToolbar.getHeight() + verticalOffset
                        < 2 * ViewCompat.getMinimumHeight(collapsingToolbar)) {
                    if (getSupportActionBar() != null && loadedAccount != null) {
                        getSupportActionBar().setTitle(loadedAccount.getDisplayName());
                        getSupportActionBar().setSubtitle(
                                String.format(getString(R.string.status_username_format),
                                        loadedAccount.username));
                    }
                    attribute = R.attr.account_toolbar_icon_tint_collapsed;
                } else {
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle("");
                        getSupportActionBar().setSubtitle("");
                    }
                    attribute = R.attr.account_toolbar_icon_tint_uncollapsed;
                }
                if (attribute != priorAttribute) {
                    priorAttribute = attribute;
                    Context context = toolbar.getContext();
                    ThemeUtils.setDrawableTint(context, toolbar.getNavigationIcon(), attribute);
                    ThemeUtils.setDrawableTint(context, toolbar.getOverflowIcon(), attribute);
                }
            }
        });

        FloatingActionButton floatingBtn = (FloatingActionButton) findViewById(R.id.floating_btn);
        floatingBtn.hide();

        CircularImageView avatar = (CircularImageView) findViewById(R.id.account_avatar);
        ImageView header = (ImageView) findViewById(R.id.account_header);
        avatar.setImageResource(R.drawable.avatar_default);
        header.setImageResource(R.drawable.account_header_default);

        obtainAccount();
        if (!accountId.equals(loggedInAccountId)) {
            isSelf = false;
            obtainRelationships();
        } else {
            /* Cause the options menu to update and instead show an options menu for when the
             * account being shown is their own account. */
            isSelf = true;
            invalidateOptionsMenu();
        }

        // Setup the tabs and timeline pager.
        AccountPagerAdapter adapter = new AccountPagerAdapter(getSupportFragmentManager(), this,
                accountId);
        String[] pageTitles = {
            getString(R.string.title_statuses),
            getString(R.string.title_follows),
            getString(R.string.title_followers)
        };
        adapter.setPageTitles(pageTitles);
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);
        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                tab.setCustomView(adapter.getTabView(i, tabLayout));
            }
        }
    }

    private void obtainAccount() {
        mastodonAPI.account(accountId).enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, retrofit2.Response<Account> response) {
                if (response.isSuccessful()) {
                    onObtainAccountSuccess(response.body());
                } else {
                    onObtainAccountFailure();
                }
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onObtainAccountFailure();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("accountId", accountId);
        super.onSaveInstanceState(outState);
    }

    private void onObtainAccountSuccess(Account account) {
        loadedAccount = account;

        TextView username = (TextView) findViewById(R.id.account_username);
        TextView displayName = (TextView) findViewById(R.id.account_display_name);
        TextView note = (TextView) findViewById(R.id.account_note);
        CircularImageView avatar = (CircularImageView) findViewById(R.id.account_avatar);
        ImageView header = (ImageView) findViewById(R.id.account_header);

        String usernameFormatted = String.format(
                getString(R.string.status_username_format), account.username);
        username.setText(usernameFormatted);

        displayName.setText(account.getDisplayName());

        note.setText(account.note);
        note.setLinksClickable(true);
        note.setMovementMethod(LinkMovementMethod.getInstance());

        if (account.locked) {
            accountLockedView.setVisibility(View.VISIBLE);
        } else {
            accountLockedView.setVisibility(View.GONE);
        }

        Picasso.with(this)
                .load(account.avatar)
                .placeholder(R.drawable.avatar_default)
                .error(R.drawable.avatar_error)
                .into(avatar);
        Picasso.with(this)
                .load(account.header)
                .placeholder(R.drawable.account_header_missing)
                .into(header);

        NumberFormat nf = NumberFormat.getInstance();

        // Add counts to the tabs in the TabLayout.
        String[] counts = {
            nf.format(Integer.parseInt(account.statusesCount)),
            nf.format(Integer.parseInt(account.followingCount)),
            nf.format(Integer.parseInt(account.followersCount)),
        };

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                View view = tab.getCustomView();
                if (view != null) {
                    TextView total = (TextView) view.findViewById(R.id.total);
                    total.setText(counts[i]);
                }
            }
        }
    }

    private void onObtainAccountFailure() {
        Snackbar.make(tabLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        obtainAccount();
                    }
                })
                .show();
    }

    private void obtainRelationships() {
        List<String> ids = new ArrayList<>(1);
        ids.add(accountId);
        mastodonAPI.relationships(ids).enqueue(new Callback<List<Relationship>>() {
            @Override
            public void onResponse(Call<List<Relationship>> call, retrofit2.Response<List<Relationship>> response) {
                if (response.isSuccessful()) {
                    Relationship relationship = response.body().get(0);
                    onObtainRelationshipsSuccess(relationship.following, relationship.blocking, relationship.muting);
                } else {
                    onObtainRelationshipsFailure(new Exception(response.message()));
                }
            }

            @Override
            public void onFailure(Call<List<Relationship>> call, Throwable t) {
                onObtainRelationshipsFailure((Exception) t);
            }
        });
    }

    private void onObtainRelationshipsSuccess(boolean following, boolean blocking, boolean muting) {
        this.following = following;
        this.blocking = blocking;
        this.muting = muting;

        if (!following || !blocking || !muting) {
            invalidateOptionsMenu();
        }

        updateButtons();
    }

    private void updateFollowButton(FloatingActionButton button) {
        if (following) {
            button.setImageResource(R.drawable.ic_person_minus_24px);
            button.setContentDescription(getString(R.string.action_unfollow));
        } else {
            button.setImageResource(R.drawable.ic_person_add_24dp);
            button.setContentDescription(getString(R.string.action_follow));
        }
    }

    private void updateButtons() {
        invalidateOptionsMenu();

        final FloatingActionButton floatingBtn = (FloatingActionButton) findViewById(R.id.floating_btn);

        if(!isSelf && !blocking) {
            floatingBtn.show();

            updateFollowButton(floatingBtn);

            floatingBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    follow(accountId);
                    updateFollowButton(floatingBtn);
                }
            });
        }
    }

    private void onObtainRelationshipsFailure(Exception exception) {
        Log.e(TAG, "Could not obtain relationships. " + exception.getMessage());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!isSelf) {
            MenuItem follow = menu.findItem(R.id.action_follow);
            String title;
            if (following) {
                title = getString(R.string.action_unfollow);
            } else {
                title = getString(R.string.action_follow);
            }
            follow.setTitle(title);
            MenuItem block = menu.findItem(R.id.action_block);
            if (blocking) {
                title = getString(R.string.action_unblock);
            } else {
                title = getString(R.string.action_block);
            }
            block.setTitle(title);
            MenuItem mute = menu.findItem(R.id.action_mute);
            if (muting) {
                title = getString(R.string.action_unmute);
            } else {
                title = getString(R.string.action_mute);
            }
            mute.setTitle(title);
        } else {
            // It shouldn't be possible to block or follow yourself.
            menu.removeItem(R.id.action_follow);
            menu.removeItem(R.id.action_block);
            menu.removeItem(R.id.action_mute);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void follow(final String id) {
        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, retrofit2.Response<Relationship> response) {
                if (response.isSuccessful()) {
                    following = response.body().following;
                    // TODO: display message/indicator when "requested" is true (i.e. when the follow is awaiting approval)
                    updateButtons();
                } else {
                    onFollowFailure(id);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onFollowFailure(id);
            }
        };

        if (following) {
            mastodonAPI.unfollowAccount(id).enqueue(cb);
        } else {
            mastodonAPI.followAccount(id).enqueue(cb);
        }
    }

    private void onFollowFailure(final String id) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                follow(id);
            }
        };
        View anyView = findViewById(R.id.activity_account);
        Snackbar.make(anyView, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }

    private void block(final String id) {
        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, retrofit2.Response<Relationship> response) {
                if (response.isSuccessful()) {
                    blocking = response.body().blocking;
                    updateButtons();
                } else {
                    onBlockFailure(id);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onBlockFailure(id);
            }
        };
        if (blocking) {
            mastodonAPI.unblockAccount(id).enqueue(cb);
        } else {
            mastodonAPI.blockAccount(id).enqueue(cb);
        }
    }

    private void onBlockFailure(final String id) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                block(id);
            }
        };
        View anyView = findViewById(R.id.activity_account);
        Snackbar.make(anyView, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }


    private void mute(final String id) {
        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    muting = response.body().muting;
                    updateButtons();
                } else {
                    onMuteFailure(id);
                }
            }

            @Override
            public void onFailure(Call<Relationship> call, Throwable t) {
                onMuteFailure(id);
            }
        };

        if (muting) {
            mastodonAPI.unmuteAccount(id).enqueue(cb);
        } else {
            mastodonAPI.muteAccount(id).enqueue(cb);
        }
    }

    private void onMuteFailure(final String id) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mute(id);
            }
        };
        View anyView = findViewById(R.id.activity_account);
        Snackbar.make(anyView, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case R.id.action_mention: {
                if (loadedAccount == null) {
                    // If the account isn't loaded yet, eat the input.
                    return false;
                }
                Intent intent = new Intent(this, ComposeActivity.class);
                intent.putExtra("mentioned_usernames", new String[] { loadedAccount.username });
                startActivity(intent);
                return true;
            }
            case R.id.action_open_in_web: {
                if (loadedAccount == null) {
                    // If the account isn't loaded yet, eat the input.
                    return false;
                }
                Uri uri = Uri.parse(loadedAccount.url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                return true;
            }
            case R.id.action_follow: {
                follow(accountId);
                return true;
            }
            case R.id.action_block: {
                block(accountId);
                return true;
            }
            case R.id.action_mute: {
                mute(accountId);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
