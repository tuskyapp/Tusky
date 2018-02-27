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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.pager.TimelinePagerAdapter;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.NotificationHelper;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity implements ActionButtonActivity {
    private static final String TAG = "MainActivity"; // logging tag
    private static final long DRAWER_ITEM_ADD_ACCOUNT = -13;
    private static final long DRAWER_ITEM_EDIT_PROFILE = 0;
    private static final long DRAWER_ITEM_FAVOURITES = 1;
    private static final long DRAWER_ITEM_MUTED_USERS = 2;
    private static final long DRAWER_ITEM_BLOCKED_USERS = 3;
    private static final long DRAWER_ITEM_SEARCH = 4;
    private static final long DRAWER_ITEM_PREFERENCES = 5;
    private static final long DRAWER_ITEM_ABOUT = 6;
    private static final long DRAWER_ITEM_LOG_OUT = 7;
    private static final long DRAWER_ITEM_FOLLOW_REQUESTS = 8;
    private static final long DRAWER_ITEM_SAVED_TOOT = 9;
    private static final long DRAWER_ITEM_LISTS = 10;

    private static int COMPOSE_RESULT = 1;

    private FloatingActionButton composeButton;
    private AccountHeader headerResult;
    private Drawer drawer;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // account switching has to be done before MastodonApi is created in super.onCreate
        Intent intent = getIntent();

        int tabPosition = 0;

        if (intent != null) {
            long accountId = intent.getLongExtra(NotificationHelper.ACCOUNT_ID, -1);

            if(accountId != -1) {
                // user clicked a notification, show notification tab and switch user if necessary
                tabPosition = 1;
                AccountEntity account = TuskyApplication.getAccountManager().getActiveAccount();

                if (account == null || accountId != account.getId()) {
                    TuskyApplication.getAccountManager().setActiveAccount(accountId);
                }
            }
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton floatingBtn = findViewById(R.id.floating_btn);
        ImageButton drawerToggle = findViewById(R.id.drawer_toggle);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.pager);

        floatingBtn.setOnClickListener(v -> {
            Intent composeIntent = new Intent(getApplicationContext(), ComposeActivity.class);
            startActivityForResult(composeIntent, COMPOSE_RESULT);
        });

        setupDrawer();

        // Setup the navigation drawer toggle button.
        ThemeUtils.setDrawableTint(this, drawerToggle.getDrawable(), R.attr.toolbar_icon_tint);
        drawerToggle.setOnClickListener(v -> drawer.openDrawer());

        /* Fetch user info while we're doing other things. This has to be done after setting up the
         * drawer, though, because its callback touches the header in the drawer. */
        fetchUserInfo();

        // Setup the tabs and timeline pager.
        TimelinePagerAdapter adapter = new TimelinePagerAdapter(getSupportFragmentManager());

        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);
        viewPager.setAdapter(adapter);

        tabLayout.setupWithViewPager(viewPager);

        int[] tabIcons = {
                R.drawable.ic_home_24dp,
                R.drawable.ic_notifications_24dp,
                R.drawable.ic_local_24dp,
                R.drawable.ic_public_24dp,
        };
        String[] pageTitles = {
                getString(R.string.title_home),
                getString(R.string.title_notifications),
                getString(R.string.title_public_local),
                getString(R.string.title_public_federated),
        };
        for (int i = 0; i < 4; i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            tab.setIcon(tabIcons[i]);
            tab.setContentDescription(pageTitles[i]);
        }

        if (tabPosition != 0) {
            TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);
            if (tab != null) {
                tab.select();
            } else {
                tabPosition = 0;
            }
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());

                tintTab(tab, true);

                if(tab.getPosition() == 1) {
                    NotificationHelper.clearNotificationsForActiveAccount(MainActivity.this);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                tintTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });

        for (int i = 0; i < 4; i++) {
            tintTab(tabLayout.getTabAt(i), i == tabPosition);
        }

        // Setup push notifications
        if (NotificationHelper.areNotificationsEnabled(this)) {
            enablePushNotifications();
        } else {
            disablePushNotifications();
        }

        composeButton = floatingBtn;
    }

    @Override
    protected void onResume() {
        super.onResume();

        NotificationHelper.clearNotificationsForActiveAccount(this);

        /* After editing a profile, the profile header in the navigation drawer needs to be
         * refreshed */
        SharedPreferences preferences = getPrivatePreferences();
        if (preferences.getBoolean("refreshProfileHeader", false)) {
            fetchUserInfo();
            preferences.edit()
                    .putBoolean("refreshProfileHeader", false)
                    .apply();
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPOSE_RESULT && resultCode == ComposeActivity.RESULT_OK) {
            Intent intent = new Intent(TimelineReceiver.Types.STATUS_COMPOSED);
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .sendBroadcast(intent);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if (viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU: {
                if (drawer.isDrawerOpen()) {
                    drawer.closeDrawer();
                } else {
                    drawer.openDrawer();
                }
                return true;
            }
            case KeyEvent.KEYCODE_SEARCH: {
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // Fix for GitHub issues #190, #259 (MainActivity won't restart on screen rotation.)
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void tintTab(TabLayout.Tab tab, boolean tinted) {
        int color = (tinted) ? R.attr.tab_icon_selected_tint : R.attr.toolbar_icon_tint;
        ThemeUtils.setDrawableTint(this, tab.getIcon(), color);
    }

    private void setupDrawer() {
        headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withDividerBelowHeader(false)
                .withHeaderBackgroundScaleType(ImageView.ScaleType.CENTER_CROP)
                .withCurrentProfileHiddenInList(true)
                .withOnAccountHeaderListener((view, profile, current) -> handleProfileClick(profile, current))
                .addProfiles(
                        new ProfileSettingDrawerItem()
                                .withIdentifier(DRAWER_ITEM_ADD_ACCOUNT)
                                .withName(R.string.add_account_name)
                                .withDescription(R.string.add_account_description)
                                .withIcon(GoogleMaterial.Icon.gmd_add))
                .build();

        headerResult.getView()
                .findViewById(R.id.material_drawer_account_header_current)
                .setContentDescription(getString(R.string.action_view_profile));

        ImageView background = headerResult.getHeaderBackgroundView();
        background.setColorFilter(ContextCompat.getColor(this, R.color.header_background_filter));
        background.setBackgroundColor(ContextCompat.getColor(this, R.color.window_background_dark));

        DrawerImageLoader.init(new AbstractDrawerImageLoader() {
            @Override
            public void set(ImageView imageView, Uri uri, Drawable placeholder, String tag) {
                Picasso.with(imageView.getContext()).load(uri).placeholder(placeholder).into(imageView);
            }

            @Override
            public void cancel(ImageView imageView) {
                Picasso.with(imageView.getContext()).cancelRequest(imageView);
            }
        });

        VectorDrawableCompat muteDrawable = VectorDrawableCompat.create(getResources(),
                R.drawable.ic_mute_24dp, getTheme());
        ThemeUtils.setDrawableTint(this, muteDrawable, R.attr.toolbar_icon_tint);

        List<IDrawerItem> listItem = new ArrayList<>();
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_EDIT_PROFILE).withName(getString(R.string.action_edit_profile)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_FAVOURITES).withName(getString(R.string.action_view_favourites)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_LISTS).withName(R.string.action_lists).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_list));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_MUTED_USERS).withName(getString(R.string.action_view_mutes)).withSelectable(false).withIcon(muteDrawable));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_BLOCKED_USERS).withName(getString(R.string.action_view_blocks)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_block));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SEARCH).withName(getString(R.string.action_search)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_search));
        listItem.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SAVED_TOOT).withName(getString(R.string.action_access_saved_toot)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_save));
        listItem.add(new DividerDrawerItem());
        listItem.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_PREFERENCES).withName(getString(R.string.action_view_preferences)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_settings));
        listItem.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ABOUT).withName(getString(R.string.about_title_activity)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_info));
        listItem.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_LOG_OUT).withName(getString(R.string.action_logout)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_exit_to_app));

        IDrawerItem[] array = new IDrawerItem[listItem.size()];
        listItem.toArray(array); // fill the array

        drawer = new DrawerBuilder()
                .withActivity(this)
                //.withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .withHasStableIds(true)
                .withSelectedItem(-1)
                .addDrawerItems(array)
                .withOnDrawerItemClickListener((view, position, drawerItem) -> {
                    if (drawerItem != null) {
                        long drawerItemIdentifier = drawerItem.getIdentifier();

                        if (drawerItemIdentifier == DRAWER_ITEM_EDIT_PROFILE) {
                            Intent intent = new Intent(MainActivity.this, EditProfileActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FAVOURITES) {
                            Intent intent = new Intent(MainActivity.this, FavouritesActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_MUTED_USERS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.MUTES);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_BLOCKED_USERS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.BLOCKS);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SEARCH) {
                            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_PREFERENCES) {
                            Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_ABOUT) {
                            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LOG_OUT) {
                            logout();
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FOLLOW_REQUESTS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.FOLLOW_REQUESTS);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SAVED_TOOT) {
                            Intent intent = new Intent(MainActivity.this, SavedTootActivity.class);
                            startActivity(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LISTS) {
                            startActivity(ListsActivity.newIntent(this));
                        }

                    }

                    return false;
                })
                .build();

        if(BuildConfig.DEBUG) {
            IDrawerItem debugItem = new SecondaryDrawerItem()
                    .withIdentifier(1337)
                    .withName("debug")
                    .withDisabledTextColor(Color.GREEN)
                    .withSelectable(false)
                    .withEnabled(false);
            drawer.addItem(debugItem);
        }

        updateProfiles();
    }

    private boolean handleProfileClick(IProfile profile, boolean current) {
        AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

        //open profile when active image was clicked
        if (current && activeAccount != null) {
            Intent intent = new Intent(MainActivity.this, AccountActivity.class);
            intent.putExtra("id", activeAccount.getAccountId());
            startActivity(intent);
            return true;
        }
        //open LoginActivity to add new account
        if(profile.getIdentifier() == DRAWER_ITEM_ADD_ACCOUNT ) {
            startActivity(LoginActivity.getIntent(this, true));
            return true;
        }
        //change Account
        changeAccount(profile.getIdentifier());
        return false;
    }


    private void changeAccount(long newSelectedId) {
        TuskyApplication.getAccountManager().setActiveAccount(newSelectedId);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();

        overridePendingTransition(R.anim.explode, R.anim.explode);
    }

    private void logout() {

        AccountEntity activeAccount = TuskyApplication.getAccountManager().getActiveAccount();

        if(activeAccount != null) {

            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_logout)
                    .setMessage(getString(R.string.action_logout_confirm, activeAccount.getFullName()))
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {

                        AccountManager accountManager = TuskyApplication.getAccountManager();

                        NotificationHelper.deleteNotificationChannelsForAccount(accountManager.getActiveAccount(), MainActivity.this);

                        AccountEntity newAccount = accountManager.logActiveAccountOut();

                        if (!NotificationHelper.areNotificationsEnabled(MainActivity.this)) disablePushNotifications();

                        Intent intent;
                        if (newAccount == null) {
                            intent = LoginActivity.getIntent(MainActivity.this, false);
                        } else {
                            intent = new Intent(MainActivity.this, MainActivity.class);
                        }
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
        }
    }

    private void fetchUserInfo() {

        mastodonApi.accountVerifyCredentials().enqueue(new Callback<Account>() {
            @Override
            public void onResponse(@NonNull Call<Account> call, @NonNull Response<Account> response) {
                if (response.isSuccessful()) {
                    onFetchUserInfoSuccess(response.body());
                } else {
                    onFetchUserInfoFailure(new Exception(response.message()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Account> call, @NonNull Throwable t) {
                onFetchUserInfoFailure((Exception) t);
            }
        });
    }

    private void onFetchUserInfoSuccess(Account me) {
        // Add the header image and avatar from the account, into the navigation drawer header.

        ImageView background = headerResult.getHeaderBackgroundView();

        Picasso.with(MainActivity.this)
                .load(me.header)
                .placeholder(R.drawable.account_header_default)
                .into(background);

        AccountManager am = TuskyApplication.getAccountManager();

        am.updateActiveAccount(me);

        NotificationHelper.createNotificationChannelsForAccount(am.getActiveAccount(), this);

        // Show follow requests in the menu, if this is a locked account.
        if (me.locked && drawer.getDrawerItem(DRAWER_ITEM_FOLLOW_REQUESTS) == null) {
            PrimaryDrawerItem followRequestsItem = new PrimaryDrawerItem()
                    .withIdentifier(DRAWER_ITEM_FOLLOW_REQUESTS)
                    .withName(R.string.action_view_follow_requests)
                    .withSelectable(false)
                    .withIcon(GoogleMaterial.Icon.gmd_person_add);
            drawer.addItemAtPosition(followRequestsItem, 3);
        }

        updateProfiles();

    }

    private void updateProfiles() {
        AccountManager am = TuskyApplication.getAccountManager();

        List<AccountEntity> allAccounts = am.getAllAccountsOrderedByActive();

        //remove profiles before adding them again to avoid duplicates
        List<IProfile> profiles = new ArrayList<>(headerResult.getProfiles());
        for(IProfile profile: profiles) {
            if(profile.getIdentifier() != DRAWER_ITEM_ADD_ACCOUNT) {
                headerResult.removeProfile(profile);
            }
        }

        for(AccountEntity acc: allAccounts) {
            headerResult.addProfiles(
                    new ProfileDrawerItem()
                            .withName(acc.getDisplayName())
                            .withIcon(acc.getProfilePictureUrl())
                            .withNameShown(true)
                            .withIdentifier(acc.getId())
                            .withEmail(acc.getFullName()));
        }

    }

    private void onFetchUserInfoFailure(Exception exception) {
        Log.e(TAG, "Failed to fetch user info. " + exception.getMessage());
    }

    @Nullable
    @Override
    public FloatingActionButton getActionButton() {
        return composeButton;
    }
}