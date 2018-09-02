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

import android.arch.lifecycle.Lifecycle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.text.emoji.EmojiCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.keylesspalace.tusky.appstore.EventHub;
import com.keylesspalace.tusky.appstore.ProfileEditedEvent;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.entity.Account;
import com.keylesspalace.tusky.interfaces.ActionButtonActivity;
import com.keylesspalace.tusky.pager.TimelinePagerAdapter;
import com.keylesspalace.tusky.util.CustomEmojiHelper;
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

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;
import io.reactivex.android.schedulers.AndroidSchedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;

public final class MainActivity extends BottomSheetActivity implements ActionButtonActivity,
        HasSupportFragmentInjector {

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

    @Inject
    public DispatchingAndroidInjector<Fragment> fragmentInjector;
    @Inject
    public EventHub eventHub;

    private FloatingActionButton composeButton;
    private AccountHeader headerResult;
    private Drawer drawer;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        int tabPosition = 0;

        if (intent != null) {
            long accountId = intent.getLongExtra(NotificationHelper.ACCOUNT_ID, -1);

            if (accountId != -1) {
                // user clicked a notification, show notification tab and switch user if necessary
                tabPosition = 1;
                AccountEntity account = accountManager.getActiveAccount();

                if (account == null || accountId != account.getId()) {
                    accountManager.setActiveAccount(accountId);
                }
            }
        }

        setContentView(R.layout.activity_main);

        composeButton = findViewById(R.id.floating_btn);
        ImageButton drawerToggle = findViewById(R.id.drawer_toggle);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.pager);

        composeButton.setOnClickListener(v -> {
            Intent composeIntent = new Intent(getApplicationContext(), ComposeActivity.class);
            startActivity(composeIntent);
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

                if (tab.getPosition() == 1) {
                    NotificationHelper.clearNotificationsForActiveAccount(MainActivity.this, accountManager);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                tintTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        for (int i = 0; i < 4; i++) {
            tintTab(tabLayout.getTabAt(i), i == tabPosition);
        }

        // Setup push notifications
        if (NotificationHelper.areNotificationsEnabled(this, accountManager)) {
            enablePushNotifications();
        } else {
            disablePushNotifications();
        }

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(event -> {
                    if (event instanceof ProfileEditedEvent) {
                        onFetchUserInfoSuccess(((ProfileEditedEvent) event).getNewProfileData());
                    }
                });

    }

    @Override
    protected void onResume() {
        super.onResume();

        NotificationHelper.clearNotificationsForActiveAccount(this, accountManager);

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
                startActivityWithSlideInAnimation(new Intent(this, SearchActivity.class));
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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

        List<IDrawerItem> listItems = new ArrayList<>(11);
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_EDIT_PROFILE).withName(R.string.action_edit_profile).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_FAVOURITES).withName(R.string.action_view_favourites).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_LISTS).withName(R.string.action_lists).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_list));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_MUTED_USERS).withName(R.string.action_view_mutes).withSelectable(false).withIcon(muteDrawable));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_BLOCKED_USERS).withName(R.string.action_view_blocks).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_block));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SEARCH).withName(R.string.action_search).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_search));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SAVED_TOOT).withName(R.string.action_access_saved_toot).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_save));
        listItems.add(new DividerDrawerItem());
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_PREFERENCES).withName(R.string.action_view_preferences).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_settings));
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ABOUT).withName(R.string.about_title_activity).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_info));
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_LOG_OUT).withName(R.string.action_logout).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_exit_to_app));

        drawer = new DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(headerResult)
                .withHasStableIds(true)
                .withSelectedItem(-1)
                .withDrawerItems(listItems)
                .withOnDrawerItemClickListener((view, position, drawerItem) -> {
                    if (drawerItem != null) {
                        long drawerItemIdentifier = drawerItem.getIdentifier();

                        if (drawerItemIdentifier == DRAWER_ITEM_EDIT_PROFILE) {
                            Intent intent = new Intent(MainActivity.this, EditProfileActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FAVOURITES) {
                            Intent intent = new Intent(MainActivity.this, FavouritesActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_MUTED_USERS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.MUTES);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_BLOCKED_USERS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.BLOCKS);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SEARCH) {
                            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_PREFERENCES) {
                            Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_ABOUT) {
                            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LOG_OUT) {
                            logout();
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FOLLOW_REQUESTS) {
                            Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                            intent.putExtra("type", AccountListActivity.Type.FOLLOW_REQUESTS);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SAVED_TOOT) {
                            Intent intent = new Intent(MainActivity.this, SavedTootActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LISTS) {
                            startActivityWithSlideInAnimation(ListsActivity.newIntent(this));
                        }

                    }

                    return false;
                })
                .build();

        if (BuildConfig.DEBUG) {
            IDrawerItem debugItem = new SecondaryDrawerItem()
                    .withIdentifier(1337)
                    .withName("debug")
                    .withDisabledTextColor(Color.GREEN)
                    .withSelectable(false)
                    .withEnabled(false);
            drawer.addItem(debugItem);
        }

        EmojiCompat.get().registerInitCallback(new EmojiCompat.InitCallback() {
            @Override
            public void onInitialized() {
                updateProfiles();
            }
        });
    }

    private boolean handleProfileClick(IProfile profile, boolean current) {
        AccountEntity activeAccount = accountManager.getActiveAccount();

        //open profile when active image was clicked
        if (current && activeAccount != null) {
            Intent intent = AccountActivity.getIntent(this, activeAccount.getAccountId());
            startActivityWithSlideInAnimation(intent);
            return true;
        }
        //open LoginActivity to add new account
        if (profile.getIdentifier() == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(LoginActivity.getIntent(this, true));
            return true;
        }
        //change Account
        changeAccount(profile.getIdentifier());
        return false;
    }


    private void changeAccount(long newSelectedId) {
        accountManager.setActiveAccount(newSelectedId);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishWithoutSlideOutAnimation();

        overridePendingTransition(R.anim.explode, R.anim.explode);
    }

    private void logout() {

        AccountEntity activeAccount = accountManager.getActiveAccount();

        if (activeAccount != null) {

            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_logout)
                    .setMessage(getString(R.string.action_logout_confirm, activeAccount.getFullName()))
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {

                        NotificationHelper.deleteNotificationChannelsForAccount(accountManager.getActiveAccount(), MainActivity.this);

                        AccountEntity newAccount = accountManager.logActiveAccountOut();

                        if (!NotificationHelper.areNotificationsEnabled(MainActivity.this, accountManager))
                            disablePushNotifications();

                        Intent intent;
                        if (newAccount == null) {
                            intent = LoginActivity.getIntent(MainActivity.this, false);
                        } else {
                            intent = new Intent(MainActivity.this, MainActivity.class);
                        }
                        startActivity(intent);
                        finishWithoutSlideOutAnimation();
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
                .load(me.getHeader())
                .into(background);

        accountManager.updateActiveAccount(me);

        NotificationHelper.createNotificationChannelsForAccount(accountManager.getActiveAccount(), this);

        // Show follow requests in the menu, if this is a locked account.
        if (me.getLocked() && drawer.getDrawerItem(DRAWER_ITEM_FOLLOW_REQUESTS) == null) {
            PrimaryDrawerItem followRequestsItem = new PrimaryDrawerItem()
                    .withIdentifier(DRAWER_ITEM_FOLLOW_REQUESTS)
                    .withName(R.string.action_view_follow_requests)
                    .withSelectable(false)
                    .withIcon(GoogleMaterial.Icon.gmd_person_add);
            drawer.addItemAtPosition(followRequestsItem, 3);
        } else if(!me.getLocked()){
            drawer.removeItem(DRAWER_ITEM_FOLLOW_REQUESTS);
        }

        updateProfiles();

    }

    private void updateProfiles() {

        List<AccountEntity> allAccounts = accountManager.getAllAccountsOrderedByActive();

        List<IProfile> profiles = new ArrayList<>(allAccounts.size()+1);

        for (AccountEntity acc : allAccounts) {
            CharSequence emojifiedName = CustomEmojiHelper.emojifyString(acc.getDisplayName(), acc.getEmojis(), headerResult.getView());
            emojifiedName = EmojiCompat.get().process(emojifiedName);

            profiles.add(
                    new ProfileDrawerItem()
                            .withSetSelected(acc.isActive())
                            .withName(emojifiedName)
                            .withIcon(acc.getProfilePictureUrl())
                            .withNameShown(true)
                            .withIdentifier(acc.getId())
                            .withEmail(acc.getFullName()));

        }

        // reuse the already existing "add account" item
        for (IProfile profile: headerResult.getProfiles()) {
            if (profile.getIdentifier() == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile);
                break;
            }
        }
        headerResult.clear();
        headerResult.setProfiles(profiles);

    }

    private void onFetchUserInfoFailure(Exception exception) {
        Log.e(TAG, "Failed to fetch user info. " + exception.getMessage());
    }

    @Nullable
    @Override
    public FloatingActionButton getActionButton() {
        return composeButton;
    }

    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return fragmentInjector;
    }

}