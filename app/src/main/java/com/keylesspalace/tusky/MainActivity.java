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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
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
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.pager.TimelinePagerAdapter;
import com.keylesspalace.tusky.receiver.TimelineReceiver;
import com.keylesspalace.tusky.util.ThemeUtils;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader;
import com.mikepenz.materialdrawer.util.DrawerImageLoader;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity implements ActionButtonActivity {
    private static final String TAG = "MainActivity"; // logging tag
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

    private static int COMPOSE_RESULT = 1;

    private FloatingActionButton composeButton;
    private String loggedInAccountId;
    private String loggedInAccountUsername;
    private Stack<Integer> pageHistory;
    private AccountHeader headerResult;
    private Drawer drawer;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pageHistory = new Stack<>();
        if (savedInstanceState != null) {
            List<Integer> restoredHistory = savedInstanceState.getIntegerArrayList("pageHistory");
            if (restoredHistory != null) {
                pageHistory.addAll(restoredHistory);
            }
        }

        FloatingActionButton floatingBtn = findViewById(R.id.floating_btn);
        ImageButton drawerToggle = findViewById(R.id.drawer_toggle);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.pager);

        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ComposeActivity.class);
                startActivityForResult(intent, COMPOSE_RESULT);
            }
        });

        setupDrawer();

        // Setup the navigation drawer toggle button.
        ThemeUtils.setDrawableTint(this, drawerToggle.getDrawable(), R.attr.toolbar_icon_tint);
        drawerToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.openDrawer();
            }
        });

        /* Fetch user info while we're doing other things. This has to be after setting up the
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

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());

                if (pageHistory.isEmpty()) {
                    pageHistory.push(0);
                }

                if (pageHistory.contains(tab.getPosition())) {
                    pageHistory.remove(pageHistory.indexOf(tab.getPosition()));
                }

                pageHistory.push(tab.getPosition());
                tintTab(tab, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                tintTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        Intent intent = getIntent();

        int tabSelected = 0;
        if (intent != null) {
            int tabPosition = intent.getIntExtra("tab_position", 0);
            if (tabPosition != 0) {
                TabLayout.Tab tab = tabLayout.getTabAt(tabPosition);
                if (tab != null) {
                    tab.select();
                    tabSelected = tabPosition;
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            tintTab(tabLayout.getTabAt(i), i == tabSelected);
        }

        // Setup push notifications
        if (arePushNotificationsEnabled()) {
            enablePushNotifications();
        } else {
            disablePushNotifications();
        }

        composeButton = floatingBtn;
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        ArrayList<Integer> pageHistoryList = new ArrayList<>();
        pageHistoryList.addAll(pageHistory);
        outState.putIntegerArrayList("pageHistory", pageHistoryList);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        clearNotifications();

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
        } else if (pageHistory.size() < 2) {
            super.onBackPressed();
        } else {
            pageHistory.pop();
            viewPager.setCurrentItem(pageHistory.peek());
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
                .withSelectionListEnabledForSingleProfile(false)
                .withDividerBelowHeader(false)
                .withHeaderBackgroundScaleType(ImageView.ScaleType.CENTER_CROP)
                .withOnAccountHeaderProfileImageListener(new AccountHeader.OnAccountHeaderProfileImageListener() {
                    @Override
                    public boolean onProfileImageClick(View view, IProfile profile, boolean current) {
                        if (current && loggedInAccountId != null) {
                            Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                            intent.putExtra("id", loggedInAccountId);
                            startActivity(intent);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onProfileImageLongClick(View view, IProfile profile, boolean current) {
                        return false;
                    }
                })
                .withCompactStyle(true)
                .build();
        headerResult.getView()
                .findViewById(R.id.material_drawer_account_header_current)
                .setContentDescription(getString(R.string.action_view_profile));

        DrawerImageLoader.init(new AbstractDrawerImageLoader() {
            @Override
            public void set(ImageView imageView, Uri uri, Drawable placeholder) {
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
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
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
                            }
                        }

                        return false;
                    }
                })
                .build();
    }

    private void logout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_logout)
                .setMessage(R.string.action_logout_confirm)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (arePushNotificationsEnabled()) disablePushNotifications();

                        getPrivatePreferences().edit()
                                .remove("domain")
                                .remove("accessToken")
                                .remove("appAccountId")
                                .apply();

                        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void fetchUserInfo() {
        SharedPreferences preferences = getPrivatePreferences();
        final String domain = preferences.getString("domain", null);
        String id = preferences.getString("loggedInAccountId", null);
        String username = preferences.getString("loggedInAccountUsername", null);

        if (id != null && username != null) {
            loggedInAccountId = id;
            loggedInAccountUsername = username;
        }

        mastodonApi.accountVerifyCredentials().enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
                if (response.isSuccessful()) {
                    onFetchUserInfoSuccess(response.body(), domain);
                } else {
                    onFetchUserInfoFailure(new Exception(response.message()));
                }
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onFetchUserInfoFailure((Exception) t);
            }
        });
    }

    private void onFetchUserInfoSuccess(Account me, String domain) {
        // Add the header image and avatar from the account, into the navigation drawer header.
        ImageView background = headerResult.getHeaderBackgroundView();
        background.setBackgroundColor(ContextCompat.getColor(this, R.color.window_background_dark));
        Picasso.with(MainActivity.this)
                .load(me.header)
                .placeholder(R.drawable.account_header_default)
                .into(background);

        headerResult.clear();
        headerResult.addProfiles(
                new ProfileDrawerItem()
                        .withName(me.getDisplayName())
                        .withEmail(String.format("%s@%s", me.username, domain))
                        .withIcon(me.avatar)
        );

        // Show follow requests in the menu, if this is a locked account.
        if (me.locked) {
            PrimaryDrawerItem followRequestsItem = new PrimaryDrawerItem()
                    .withIdentifier(DRAWER_ITEM_FOLLOW_REQUESTS)
                    .withName(R.string.action_view_follow_requests)
                    .withSelectable(false)
                    .withIcon(GoogleMaterial.Icon.gmd_person_add);
            drawer.addItemAtPosition(followRequestsItem, 3);
        }

        // Update the current login information.
        loggedInAccountId = me.id;
        loggedInAccountUsername = me.username;
        getPrivatePreferences().edit()
                .putString("loggedInAccountId", loggedInAccountId)
                .putString("loggedInAccountUsername", loggedInAccountUsername)
                .putBoolean("loggedInAccountLocked", me.locked)
                .apply();
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