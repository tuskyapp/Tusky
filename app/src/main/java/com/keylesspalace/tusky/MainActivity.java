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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.SearchSuggestionsAdapter;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.keylesspalace.tusky.entity.Account;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity"; // logging tag
    protected static int COMPOSE_RESULT = 1;

    private String loggedInAccountId;
    private String loggedInAccountUsername;
    private Stack<Integer> pageHistory;
    private AccountHeader headerResult;
    private Drawer drawer;

    @BindView(R.id.floating_search_view) FloatingSearchView searchView;
    @BindView(R.id.floating_btn) FloatingActionButton floatingBtn;
    @BindView(R.id.tab_layout) TabLayout tabLayout;
    @BindView(R.id.pager) ViewPager viewPager;

    public FloatingActionButton composeButton;

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

        ButterKnife.bind(this);

        floatingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ComposeActivity.class);
                startActivityForResult(intent, COMPOSE_RESULT);
            }
        });

        setupDrawer();
        setupSearchView();

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
            public void onTabReselected(TabLayout.Tab tab) {}
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
    protected void onResume() {
        super.onResume();

        SharedPreferences notificationPreferences = getApplicationContext()
                .getSharedPreferences("Notifications", MODE_PRIVATE);
        notificationPreferences.edit()
                .putString("current", "[]")
                .apply();

        pushNotificationClient.clearNotifications(this);

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
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        ArrayList<Integer> pageHistoryList = new ArrayList<>();
        pageHistoryList.addAll(pageHistory);
        outState.putIntegerArrayList("pageHistory", pageHistoryList);
        super.onSaveInstanceState(outState, outPersistentState);
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

        drawer = new DrawerBuilder()
                .withActivity(this)
                //.withToolbar(toolbar)
                .withAccountHeader(headerResult)
                .withHasStableIds(true)
                .withSelectedItem(-1)
                .addDrawerItems(
                        new PrimaryDrawerItem().withIdentifier(0).withName(getString(R.string.action_edit_profile)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person),
                        new PrimaryDrawerItem().withIdentifier(1).withName(getString(R.string.action_view_favourites)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star),
                        new PrimaryDrawerItem().withIdentifier(2).withName(getString(R.string.action_view_mutes)).withSelectable(false).withIcon(muteDrawable),
                        new PrimaryDrawerItem().withIdentifier(3).withName(getString(R.string.action_view_blocks)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_block),
                        new DividerDrawerItem(),
                        new SecondaryDrawerItem().withIdentifier(4).withName(getString(R.string.action_view_preferences)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_settings),
                        new SecondaryDrawerItem().withIdentifier(5).withName(getString(R.string.about_title_activity)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_info),
                        new SecondaryDrawerItem().withIdentifier(6).withName(getString(R.string.action_logout)).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_exit_to_app)
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            long drawerItemIdentifier = drawerItem.getIdentifier();

                            if (drawerItemIdentifier == 0) {
                                Intent intent = new Intent(MainActivity.this, EditProfileActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 1) {
                                Intent intent = new Intent(MainActivity.this, FavouritesActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 2) {
                                Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                                intent.putExtra("type", AccountListActivity.Type.MUTES);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 3) {
                                Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                                intent.putExtra("type", AccountListActivity.Type.BLOCKS);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 4) {
                                Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 5) {
                                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                                startActivity(intent);
                            } else if (drawerItemIdentifier == 6) {
                                logout();
                            } else if (drawerItemIdentifier == 7) {
                                Intent intent = new Intent(MainActivity.this, AccountListActivity.class);
                                intent.putExtra("type", AccountListActivity.Type.FOLLOW_REQUESTS);
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

    private void setupSearchView() {
        searchView.attachNavigationDrawerToMenuButton(drawer.getDrawerLayout());

        // Setup content descriptions for the different elements in the search view.
        final View leftAction = searchView.findViewById(R.id.left_action);
        leftAction.setContentDescription(getString(R.string.action_open_drawer));
        searchView.setOnFocusChangeListener(new FloatingSearchView.OnFocusChangeListener() {
            @Override
            public void onFocus() {
                leftAction.setContentDescription(getString(R.string.action_close));
            }

            @Override
            public void onFocusCleared() {
                leftAction.setContentDescription(getString(R.string.action_open_drawer));
            }
        });
        View clearButton = searchView.findViewById(R.id.clear_btn);
        clearButton.setContentDescription(getString(R.string.action_clear));

        searchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, String newQuery) {
                if (!oldQuery.equals("") && newQuery.equals("")) {
                    searchView.clearSuggestions();
                    return;
                }

                if (newQuery.length() < 3) {
                    return;
                }

                searchView.showProgress();

                mastodonAPI.searchAccounts(newQuery, false, 5).enqueue(new Callback<List<Account>>() {
                    @Override
                    public void onResponse(Call<List<Account>> call, Response<List<Account>> response) {
                        if (response.isSuccessful()) {
                            searchView.swapSuggestions(response.body());
                            searchView.hideProgress();
                        } else {
                            searchView.hideProgress();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Account>> call, Throwable t) {
                        searchView.hideProgress();
                    }
                });
            }
        });

        searchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSuggestionClicked(SearchSuggestion searchSuggestion) {
                Account accountSuggestion = (Account) searchSuggestion;
                Intent intent = new Intent(MainActivity.this, AccountActivity.class);
                intent.putExtra("id", accountSuggestion.id);
                startActivity(intent);
            }

            @Override
            public void onSearchAction(String currentQuery) {}
        });

        searchView.setOnBindSuggestionCallback(new SearchSuggestionsAdapter.OnBindSuggestionCallback() {
            @Override
            public void onBindSuggestion(View suggestionView, ImageView leftIcon, TextView textView, SearchSuggestion item, int itemPosition) {
                Account accountSuggestion = ((Account) item);

                Picasso.with(MainActivity.this)
                        .load(accountSuggestion.avatar)
                        .placeholder(R.drawable.avatar_default)
                        .into(leftIcon);

                String searchStr = accountSuggestion.getDisplayName() + " " + accountSuggestion.username;
                final SpannableStringBuilder str = new SpannableStringBuilder(searchStr);

                str.setSpan(new StyleSpan(Typeface.BOLD), 0, accountSuggestion.getDisplayName().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                textView.setText(str);
                textView.setMaxLines(1);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            }
        });
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

        mastodonAPI.accountVerifyCredentials().enqueue(new Callback<Account>() {
            @Override
            public void onResponse(Call<Account> call, Response<Account> response) {
                if (!response.isSuccessful()) {
                    onFetchUserInfoFailure(new Exception(response.message()));
                    return;
                }
                onFetchUserInfoSuccess(response.body(), domain);
            }

            @Override
            public void onFailure(Call<Account> call, Throwable t) {
                onFetchUserInfoFailure((Exception) t);
            }
        });
    }

    private void onFetchUserInfoSuccess(Account me, String domain) {
        // Add the header image and avatar from the account, into the navigation drawer header.
        headerResult.clear();

        ImageView background = headerResult.getHeaderBackgroundView();
        int backgroundWidth = background.getWidth();
        int backgroundHeight = background.getHeight();
        if (backgroundWidth == 0 || backgroundHeight == 0) {
            /* The header ImageView may not be layed out when the verify credentials call returns so
             * measure the dimensions and use those. */
            background.measure(View.MeasureSpec.EXACTLY, View.MeasureSpec.EXACTLY);
            backgroundWidth = background.getMeasuredWidth();
            backgroundHeight = background.getMeasuredHeight();
        }

        background.setBackgroundColor(ContextCompat.getColor(this, R.color.window_background_dark));

        Picasso.with(MainActivity.this)
                .load(me.header)
                .placeholder(R.drawable.account_header_default)
                .resize(backgroundWidth, backgroundHeight)
                .centerCrop()
                .into(background);

        headerResult.addProfiles(
                new ProfileDrawerItem()
                        .withName(me.getDisplayName())
                        .withEmail(String.format("%s@%s", me.username, domain))
                        .withIcon(me.avatar)
        );

        // Show follow requests in the menu, if this is a locked account.
        if (me.locked) {
            PrimaryDrawerItem followRequestsItem = new PrimaryDrawerItem()
                    .withIdentifier(7)
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
        if(drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else if(pageHistory.size() < 2) {
            super.onBackPressed();
        } else {
            pageHistory.pop();
            viewPager.setCurrentItem(pageHistory.peek());
        }
    }

    // Fix for GitHub issues #190, #259 (MainActivity won't restart on screen rotation.)
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}