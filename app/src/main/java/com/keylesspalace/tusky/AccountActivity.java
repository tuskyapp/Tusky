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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.AttrRes;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.entity.Relationship;
import com.keylesspalace.tusky.interfaces.LinkListener;
import com.keylesspalace.tusky.pager.AccountPagerAdapter;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.Assert;
import com.keylesspalace.tusky.util.ThemeUtils;
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

    private enum FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED,
    }

    private String accountId;
    private FollowState followState;
    private boolean blocking;
    private boolean muting;
    private boolean isSelf;
    private Account loadedAccount;

    @BindView(R.id.account_avatar) CircularImageView avatar;
    @BindView(R.id.account_header) ImageView header;
    @BindView(R.id.floating_btn) FloatingActionButton floatingBtn;
    @BindView(R.id.tab_layout) TabLayout tabLayout;
    @BindView(R.id.account_locked) ImageView accountLockedView;
    @BindView(R.id.activity_account) View container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        ButterKnife.bind(this);

        if (savedInstanceState != null) {
            accountId = savedInstanceState.getString("accountId");
            followState = (FollowState) savedInstanceState.getSerializable("followState");
            blocking = savedInstanceState.getBoolean("blocking");
            muting = savedInstanceState.getBoolean("muting");
        } else {
            Intent intent = getIntent();
            accountId = intent.getStringExtra("id");
            followState = FollowState.NOT_FOLLOWING;
            blocking = false;
            muting = false;
        }
        loadedAccount = null;

        SharedPreferences preferences = getPrivatePreferences();
        String loggedInAccountId = preferences.getString("loggedInAccountId", null);

        // Setup the toolbar.
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

                        toolbar.setTitleTextColor(ThemeUtils.getColor(AccountActivity.this,
                                android.R.attr.textColorPrimary));
                        toolbar.setSubtitleTextColor(ThemeUtils.getColor(AccountActivity.this,
                                android.R.attr.textColorSecondary));

                    attribute = R.attr.account_toolbar_icon_tint_collapsed;
                } else {
                    toolbar.setTitleTextColor(Color.TRANSPARENT);
                    toolbar.setSubtitleTextColor(Color.TRANSPARENT);

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

        // Initialise the default UI states.
        floatingBtn.hide();

        // Obtain information to fill out the profile.
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
        tabLayout.setupWithViewPager(viewPager);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                tab.setCustomView(adapter.getTabView(i, tabLayout));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("accountId", accountId);
        outState.putSerializable("followState", followState);
        outState.putBoolean("blocking", blocking);
        outState.putBoolean("muting", muting);
        super.onSaveInstanceState(outState);
    }

    private void obtainAccount() {
        mastodonAPI.account(accountId).enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
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

    private void onObtainAccountSuccess(Account account) {
        loadedAccount = account;

        TextView username = (TextView) findViewById(R.id.account_username);
        TextView displayName = (TextView) findViewById(R.id.account_display_name);
        TextView note = (TextView) findViewById(R.id.account_note);

        String usernameFormatted = String.format(
                getString(R.string.status_username_format), account.username);
        username.setText(usernameFormatted);

        displayName.setText(account.getDisplayName());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(account.getDisplayName());

            String subtitle = String.format(getString(R.string.status_username_format),
                    account.username);
            getSupportActionBar().setSubtitle(subtitle);

        }

        boolean useCustomTabs = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("customTabs", true);
        LinkHelper.setClickableText(note, account.note, null, useCustomTabs, new LinkListener() {
            @Override
            public void onViewTag(String tag) {
                Intent intent = new Intent(AccountActivity.this, ViewTagActivity.class);
                intent.putExtra("hashtag", tag);
                startActivity(intent);
            }

            @Override
            public void onViewAccount(String id) {
                Intent intent = new Intent(AccountActivity.this, AccountActivity.class);
                intent.putExtra("id", id);
                startActivity(intent);
            }
        });

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
                .placeholder(R.drawable.account_header_default)
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
            public void onResponse(Call<List<Relationship>> call,
                                   Response<List<Relationship>> response) {
                if (response.isSuccessful()) {
                    Relationship relationship = response.body().get(0);
                    onObtainRelationshipsSuccess(relationship.requested, relationship.following,
                            relationship.blocking, relationship.muting);
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

    private void onObtainRelationshipsSuccess(boolean followRequested, boolean following,
                                              boolean blocking, boolean muting) {
        if (following) {
            followState = FollowState.FOLLOWING;
        } else if (followRequested) {
            followState = FollowState.REQUESTED;
        } else {
            followState = FollowState.NOT_FOLLOWING;
        }
        this.blocking = blocking;
        this.muting = muting;

        if (followState != FollowState.NOT_FOLLOWING || !blocking || !muting) {
            invalidateOptionsMenu();
        }

        updateButtons();
    }

    private void updateFollowButton(FloatingActionButton button) {
        switch (followState) {
            case NOT_FOLLOWING: {
                button.setImageResource(R.drawable.ic_person_add_24dp);
                button.setContentDescription(getString(R.string.action_follow));
                break;
            }
            case REQUESTED: {
                button.setImageResource(R.drawable.ic_hourglass_24dp);
                button.setContentDescription(getString(R.string.state_follow_requested));
                break;
            }
            case FOLLOWING: {
                button.setImageResource(R.drawable.ic_person_minus_24px);
                button.setContentDescription(getString(R.string.action_unfollow));
                break;
            }
        }
    }

    private void updateButtons() {
        invalidateOptionsMenu();

        if(!isSelf && !blocking) {
            floatingBtn.show();

            updateFollowButton(floatingBtn);

            floatingBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (followState != FollowState.REQUESTED) {
                        follow(accountId);
                    } else {
                        showFollowRequestPendingDialog(accountId);
                    }
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

    private String getFollowAction() {
        switch (followState) {
            default:
            case NOT_FOLLOWING: return getString(R.string.action_follow);
            case REQUESTED:
            case FOLLOWING: return getString(R.string.action_unfollow);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!isSelf) {
            MenuItem follow = menu.findItem(R.id.action_follow);
            follow.setTitle(getFollowAction());
            follow.setVisible(followState != FollowState.REQUESTED);

            MenuItem block = menu.findItem(R.id.action_block);
            String title;
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
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    Relationship relationship = response.body();
                    if (relationship.following) {
                        followState = FollowState.FOLLOWING;
                    } else if (relationship.requested) {
                        followState = FollowState.REQUESTED;
                        Snackbar.make(container, R.string.state_follow_requested,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        followState = FollowState.NOT_FOLLOWING;
                        broadcast(TimelineReceiver.Types.UNFOLLOW_ACCOUNT, id);
                    }
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

        Assert.expect(followState != FollowState.REQUESTED);
        switch (followState) {
            case NOT_FOLLOWING: { mastodonAPI.followAccount(id).enqueue(cb);   break; }
            case FOLLOWING:     { mastodonAPI.unfollowAccount(id).enqueue(cb); break; }
        }
    }

    private void onFollowFailure(final String id) {
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                follow(id);
            }
        };
        Snackbar.make(container, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }

    private void showFollowRequestPendingDialog(final String id) {
        DialogInterface.OnClickListener waitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };
        new AlertDialog.Builder(AccountActivity.this)
                .setMessage(R.string.dialog_message_follow_request)
                .setPositiveButton(R.string.action_ok, waitListener)
                .show();
    }

    private void block(final String id) {
        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    broadcast(TimelineReceiver.Types.BLOCK_ACCOUNT, id);
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
        Snackbar.make(container, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }


    private void mute(final String id) {
        Callback<Relationship> cb = new Callback<Relationship>() {
            @Override
            public void onResponse(Call<Relationship> call, Response<Relationship> response) {
                if (response.isSuccessful()) {
                    broadcast(TimelineReceiver.Types.MUTE_ACCOUNT, id);
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
        Snackbar.make(container, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show();
    }

    private void broadcast(String action, String id) {
        Intent intent = new Intent(action);
        intent.putExtra("id", id);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
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
