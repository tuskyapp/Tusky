/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma;

import androidx.lifecycle.Lifecycle;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import androidx.emoji.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;

import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.emoji.text.EmojiCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager.widget.ViewPager;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import tech.bigfig.roma.appstore.CacheUpdater;
import tech.bigfig.roma.appstore.EventHub;
import tech.bigfig.roma.appstore.MainTabsChangedEvent;
import tech.bigfig.roma.appstore.ProfileEditedEvent;
import tech.bigfig.roma.components.conversation.ConversationsRepository;
import tech.bigfig.roma.db.AccountEntity;
import tech.bigfig.roma.entity.Account;
import tech.bigfig.roma.entity.push.PushAlerts;
import tech.bigfig.roma.entity.push.PushData;
import tech.bigfig.roma.entity.push.PushKeys;
import tech.bigfig.roma.entity.push.PushSubscription;
import tech.bigfig.roma.entity.push.PushSubscriptionRequest;
import tech.bigfig.roma.interfaces.ActionButtonActivity;
import tech.bigfig.roma.interfaces.ReselectableFragment;
import tech.bigfig.roma.pager.MainPagerAdapter;
import tech.bigfig.roma.service.push.DeleteFcmTokenWorker;
import tech.bigfig.roma.service.push.UpdateFcmTokenWorker;
import tech.bigfig.roma.util.CustomEmojiHelper;
import tech.bigfig.roma.util.NotificationHelper;
import tech.bigfig.roma.util.PushHelperKt;
import tech.bigfig.roma.util.ThemeUtils;

import static com.uber.autodispose.AutoDispose.autoDisposable;
import static com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from;
import static tech.bigfig.roma.util.MediaUtilsKt.deleteStaleCachedMedia;


public final class MainActivity extends BottomSheetActivity implements ActionButtonActivity,
        HasSupportFragmentInjector {

    private static final String TAG = "MainActivity"; // logging tag
    private static final long DRAWER_ITEM_ADD_ACCOUNT = -13;
    private static final long DRAWER_ITEM_EDIT_PROFILE = 0;
    private static final long DRAWER_ITEM_FAVOURITES = 1;
    private static final long DRAWER_ITEM_LISTS = 2;
    private static final long DRAWER_ITEM_SEARCH = 3;
    private static final long DRAWER_ITEM_SAVED_TOOT = 4;
    private static final long DRAWER_ITEM_ACCOUNT_SETTINGS = 5;
    private static final long DRAWER_ITEM_SETTINGS = 6;
    private static final long DRAWER_ITEM_ABOUT = 7;
    private static final long DRAWER_ITEM_LOG_OUT = 8;
    private static final long DRAWER_ITEM_FOLLOW_REQUESTS = 9;
    public static final String STATUS_URL = "statusUrl";

    @Inject
    public DispatchingAndroidInjector<Fragment> fragmentInjector;
    @Inject
    public EventHub eventHub;
    @Inject
    public CacheUpdater cacheUpdater;
    @Inject
    ConversationsRepository conversationRepository;

    private FloatingActionButton composeButton;
    private AccountHeader headerResult;
    private Drawer drawer;
    private TabLayout tabLayout;
    private ViewPager viewPager;

    private int notificationTabPosition;
    private MainPagerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (accountManager.getActiveAccount() == null) {
            // will be redirected to LoginActivity by BaseActivity
            return;
        }

        Intent intent = getIntent();
        boolean showNotificationTab = false;

        if (intent != null) {
            long accountId = intent.getLongExtra(NotificationHelper.ACCOUNT_ID, -1);
            boolean accountRequested = (accountId != -1);

            if (accountRequested) {
                AccountEntity account = accountManager.getActiveAccount();
                if (account == null || accountId != account.getId()) {
                    accountManager.setActiveAccount(accountId);
                }
            }

            if (ComposeActivity.canHandleMimeType(intent.getType())) {
                // Sharing to Roma from an external app
                if (accountRequested) {
                    // The correct account is already active
                    forwardShare(intent);
                } else {
                    // No account was provided, show the chooser
                    showAccountChooserDialog(getString(R.string.action_share_as), true, account -> {
                        long requestedId = account.getId();
                        AccountEntity activeAccount = accountManager.getActiveAccount();
                        if (activeAccount != null && requestedId == activeAccount.getId()) {
                            // The correct account is already active
                            forwardShare(intent);
                        } else {
                            // A different account was requested, restart the activity
                            intent.putExtra(NotificationHelper.ACCOUNT_ID, requestedId);
                            changeAccount(requestedId, intent);
                        }
                    });
                }
            } else if (accountRequested) {
                // user clicked a notification, show notification tab and switch user if necessary
                showNotificationTab = true;
            }
        }
        setContentView(R.layout.activity_main);

        composeButton = findViewById(R.id.floating_btn);
        ImageButton drawerToggle = findViewById(R.id.drawer_toggle);
        tabLayout = findViewById(R.id.tab_layout);
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

        setupTabs(showNotificationTab);

        int pageMargin = getResources().getDimensionPixelSize(R.dimen.tab_page_margin);
        viewPager.setPageMargin(pageMargin);
        Drawable pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark);
        viewPager.setPageMarginDrawable(pageMarginDrawable);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());

                if (tab.getPosition() == notificationTabPosition) {
                    NotificationHelper.clearNotificationsForActiveAccount(MainActivity.this, accountManager);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (adapter != null) {
                    Fragment fragment = adapter.getFragment(tab.getPosition());
                    if (fragment instanceof ReselectableFragment) {
                        ((ReselectableFragment) fragment).onReselect();
                    }
                }
            }
        });

        AccountEntity entity = accountManager.getActiveAccount();
        if (entity!=null) {
            // Setup push notifications
            if (NotificationHelper.areNotificationsEnabled(this, accountManager)) {
                NotificationHelper.enablePullNotifications(entity.getUsername());
            } else {
                NotificationHelper.disablePullNotifications(entity.getUsername(),entity.getDomain());
            }
        }

        eventHub.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .as(autoDisposable(from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe(event -> {
                    if (event instanceof ProfileEditedEvent) {
                        onFetchUserInfoSuccess(((ProfileEditedEvent) event).getNewProfileData());
                    }
                    if (event instanceof MainTabsChangedEvent) {
                        setupTabs(false);
                    }
                });

        // Flush old media that was cached for sharing
        deleteStaleCachedMedia(getApplicationContext().getExternalFilesDir("Roma"));
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

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            String statusUrl = intent.getStringExtra(STATUS_URL);
            if (statusUrl != null) {
                viewUrl(statusUrl);
            }
        }
    }

    private void forwardShare(Intent intent) {
        Intent composeIntent = new Intent(this, ComposeActivity.class);
        composeIntent.setAction(intent.getAction());
        composeIntent.setType(intent.getType());
        composeIntent.putExtras(intent);
        composeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(composeIntent);
        finish();
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

        List<IDrawerItem> listItems = new ArrayList<>(10);
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_EDIT_PROFILE).withName(R.string.action_edit_profile).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_FAVOURITES).withName(R.string.action_view_favourites).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_LISTS).withName(R.string.action_lists).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_list));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SEARCH).withName(R.string.action_search).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_search));
        listItems.add(new PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SAVED_TOOT).withName(R.string.action_access_saved_toot).withSelectable(false).withIcon(R.drawable.ic_notebook).withIconTintingEnabled(true));
        listItems.add(new DividerDrawerItem());
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ACCOUNT_SETTINGS).withName(R.string.action_view_account_preferences).withSelectable(false).withIcon(R.drawable.ic_account_settings).withIconTintingEnabled(true));
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_SETTINGS).withName(R.string.action_view_preferences).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_settings));
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ABOUT).withName(R.string.about_title_activity).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_info));
        listItems.add(new SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_LOG_OUT).withName(R.string.action_logout).withSelectable(false).withIcon(R.drawable.ic_logout).withIconTintingEnabled(true));

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
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SEARCH) {
                            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_ACCOUNT_SETTINGS) {
                            Intent intent = PreferencesActivity.newIntent(MainActivity.this, PreferencesActivity.ACCOUNT_PREFERENCES);
                            startActivityWithSlideInAnimation(intent);
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SETTINGS) {
                            Intent intent = PreferencesActivity.newIntent(MainActivity.this, PreferencesActivity.GENERAL_PREFERENCES);
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

    private void setupTabs(boolean selectNotificationTab) {
        List<TabData> tabs = accountManager.getActiveAccount().getTabPreferences();

        adapter = new MainPagerAdapter(tabs, getSupportFragmentManager());
        viewPager.setAdapter(adapter);

        tabLayout.setupWithViewPager(viewPager);
        tabLayout.removeAllTabs();
        for (int i = 0; i < tabs.size(); i++) {
            TabLayout.Tab tab = tabLayout.newTab()
                    .setIcon(tabs.get(i).getIcon())
                    .setContentDescription(tabs.get(i).getText());
            tabLayout.addTab(tab);
            if (tabs.get(i).getId().equals(TabDataKt.NOTIFICATIONS)) {
                notificationTabPosition = i;
                if (selectNotificationTab) {
                    tab.select();
                }
            }
        }

    }

    private boolean handleProfileClick(IProfile profile, boolean current) {
        AccountEntity activeAccount = accountManager.getActiveAccount();

        //open profile when active image was clicked
        if (current && activeAccount != null) {
            Intent intent = AccountActivity.getIntent(this, activeAccount.getAccountId());
            startActivityWithSlideInAnimation(intent);
            new Handler().postDelayed(() -> drawer.closeDrawer(), 100);
            return true;
        }
        //open LoginActivity to add new account
        if (profile.getIdentifier() == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(LoginActivity.getIntent(this, true));
            new Handler().postDelayed(() -> drawer.closeDrawer(), 100);
            return true;
        }
        //change Account
        changeAccount(profile.getIdentifier(), null);
        return false;
    }


    private void changeAccount(long newSelectedId, @Nullable Intent forward) {
        cacheUpdater.stop();
        accountManager.setActiveAccount(newSelectedId);

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (forward != null) {
            intent.setType(forward.getType());
            intent.setAction(forward.getAction());
            intent.putExtras(forward);
        }
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
                        unsubscribePushEvents(activeAccount);
                        cacheUpdater.clearForUser(activeAccount.getId());
                        conversationRepository.deleteCacheForAccount(activeAccount.getId());

                        AccountEntity newAccount = accountManager.logActiveAccountOut();

                        /*if (!NotificationHelper.areNotificationsEnabled(MainActivity.this, accountManager)) {
                            NotificationHelper.disablePullNotifications();
                        }*/

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

    private void unsubscribePushEvents(AccountEntity activeAccount) {
        DeleteFcmTokenWorker.Companion.removeTokens(activeAccount.getAccessToken(),activeAccount.getDomain());
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
        } else if (!me.getLocked()) {
            drawer.removeItem(DRAWER_ITEM_FOLLOW_REQUESTS);
        }

        updateProfiles();

    }

    private void updateProfiles() {

        List<AccountEntity> allAccounts = accountManager.getAllAccountsOrderedByActive();

        List<IProfile> profiles = new ArrayList<>(allAccounts.size() + 1);

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
        for (IProfile profile : headerResult.getProfiles()) {
            if (profile.getIdentifier() == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile);
                break;
            }
        }
        headerResult.clear();
        headerResult.setProfiles(profiles);
        headerResult.setActiveProfile(accountManager.getActiveAccount().getId());
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