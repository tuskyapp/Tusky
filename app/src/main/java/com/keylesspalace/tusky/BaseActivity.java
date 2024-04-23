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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.keylesspalace.tusky.adapter.AccountSelectionAdapter;
import com.keylesspalace.tusky.components.login.LoginActivity;
import com.keylesspalace.tusky.db.AccountEntity;
import com.keylesspalace.tusky.db.AccountManager;
import com.keylesspalace.tusky.di.Injectable;
import com.keylesspalace.tusky.interfaces.AccountSelectionListener;
import com.keylesspalace.tusky.settings.AppTheme;
import com.keylesspalace.tusky.settings.PrefKeys;
import com.keylesspalace.tusky.util.ActivityExtensions;
import com.keylesspalace.tusky.util.ThemeUtils;

import java.util.List;

import javax.inject.Inject;

import static com.keylesspalace.tusky.settings.PrefKeys.APP_THEME;
import static com.keylesspalace.tusky.util.ActivityExtensions.supportsOverridingActivityTransitions;

public abstract class BaseActivity extends AppCompatActivity implements Injectable {

    public static final String OPEN_WITH_SLIDE_IN = "OPEN_WITH_SLIDE_IN";

    private static final String TAG = "BaseActivity";

    @Inject
    @NonNull
    public AccountManager accountManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (supportsOverridingActivityTransitions() && activityTransitionWasRequested()) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.activity_open_enter, R.anim.activity_open_exit);
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.activity_close_enter, R.anim.activity_close_exit);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        /* There isn't presently a way to globally change the theme of a whole application at
         * runtime, just individual activities. So, each activity has to set its theme before any
         * views are created. */
        String theme = preferences.getString(APP_THEME, AppTheme.DEFAULT.getValue());
        Log.d("activeTheme", theme);
        if (ThemeUtils.isBlack(getResources().getConfiguration(), theme)) {
            setTheme(R.style.TuskyBlackTheme);
        }

        /* set the taskdescription programmatically, the theme would turn it blue */
        String appName = getString(R.string.app_name);
        Bitmap appIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        int recentsBackgroundColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK);

        setTaskDescription(new ActivityManager.TaskDescription(appName, appIcon, recentsBackgroundColor));

        int style = textStyle(preferences.getString(PrefKeys.STATUS_TEXT_SIZE, "medium"));
        getTheme().applyStyle(style, true);

        if(requiresLogin()) {
            redirectIfNotLoggedIn();
        }
    }

    private boolean activityTransitionWasRequested() {
        return getIntent().getBooleanExtra(OPEN_WITH_SLIDE_IN, false);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(newBase);

        // Scale text in the UI from PrefKeys.UI_TEXT_SCALE_RATIO
        float uiScaleRatio = preferences.getFloat(PrefKeys.UI_TEXT_SCALE_RATIO, 100F);

        Configuration configuration = newBase.getResources().getConfiguration();

        // Adjust `fontScale` in the configuration.
        //
        // You can't repeatedly adjust the `fontScale` in `newBase` because that will contain the
        // result of previous adjustments. E.g., going from 100% to 80% to 100% does not return
        // you to the original 100%, it leaves it at 80%.
        //
        // Instead, calculate the new scale from the application context. This is unaffected by
        // changes to the base context. It does contain contain any changes to the font scale from
        // "Settings > Display > Font size" in the device settings, so scaling performed here
        // is in addition to any scaling in the device settings.
        Configuration appConfiguration = newBase.getApplicationContext().getResources().getConfiguration();

        // This only adjusts the fonts, anything measured in `dp` is unaffected by this.
        // You can try to adjust `densityDpi` as shown in the commented out code below. This
        // works, to a point. However, dialogs do not react well to this. Beyond a certain
        // scale (~ 120%) the right hand edge of the dialog will clip off the right of the
        // screen.
        //
        // So for now, just adjust the font scale
        //
        // val displayMetrics = appContext.resources.displayMetrics
        // configuration.densityDpi = ((displayMetrics.densityDpi * uiScaleRatio).toInt())
        configuration.fontScale = appConfiguration.fontScale * uiScaleRatio / 100F;

        Context fontScaleContext = newBase.createConfigurationContext(configuration);

        super.attachBaseContext(fontScaleContext);
    }

    protected boolean requiresLogin() {
        return true;
    }

    private static int textStyle(String name) {
        int style;
        switch (name) {
            case "smallest":
                style = R.style.TextSizeSmallest;
                break;
            case "small":
                style = R.style.TextSizeSmall;
                break;
            case "medium":
            default:
                style = R.style.TextSizeMedium;
                break;
            case "large":
                style = R.style.TextSizeLarge;
                break;
            case "largest":
                style = R.style.TextSizeLargest;
                break;
        }
        return style;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        // if this activity was opened with slide-in, close it with slide out
        if (!supportsOverridingActivityTransitions() && activityTransitionWasRequested()) {
            overridePendingTransition(R.anim.activity_close_enter, R.anim.activity_close_exit);
        }
    }

    protected void redirectIfNotLoggedIn() {
        AccountEntity account = accountManager.getActiveAccount();
        if (account == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityExtensions.startActivityWithSlideInAnimation(this, intent);
            finish();
        }
    }

    protected void showErrorDialog(@Nullable View anyView, @StringRes int descriptionId, @StringRes int actionId, @Nullable View.OnClickListener listener) {
        if (anyView != null) {
            Snackbar bar = Snackbar.make(anyView, getString(descriptionId), Snackbar.LENGTH_SHORT);
            bar.setAction(actionId, listener);
            bar.show();
        }
    }

    public void showAccountChooserDialog(@Nullable CharSequence dialogTitle, boolean showActiveAccount, @NonNull AccountSelectionListener listener) {
        List<AccountEntity> accounts = accountManager.getAllAccountsOrderedByActive();
        AccountEntity activeAccount = accountManager.getActiveAccount();

        switch(accounts.size()) {
            case 1:
                listener.onAccountSelected(activeAccount);
                return;
            case 2:
                if (!showActiveAccount) {
                    for (AccountEntity account : accounts) {
                        if (activeAccount != account) {
                            listener.onAccountSelected(account);
                            return;
                        }
                    }
                }
                break;
        }

        if (!showActiveAccount && activeAccount != null) {
            accounts.remove(activeAccount);
        }
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(this);
        adapter.addAll(accounts);

        new AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setAdapter(adapter, (dialogInterface, index) -> listener.onAccountSelected(accounts.get(index)))
            .show();
    }

    public @Nullable String getOpenAsText() {
        List<AccountEntity> accounts = accountManager.getAllAccountsOrderedByActive();
        switch (accounts.size()) {
            case 0:
            case 1:
                return null;
            case 2:
                for (AccountEntity account : accounts) {
                    if (account != accountManager.getActiveAccount()) {
                        return String.format(getString(R.string.action_open_as), account.getFullName());
                    }
                }
                return null;
            default:
                return String.format(getString(R.string.action_open_as), "…");
        }
    }

    public void openAsAccount(@NonNull String url, @NonNull AccountEntity account) {
        accountManager.setActiveAccount(account.getId());
        Intent intent = MainActivity.redirectIntent(this, account.getId(), url);

        startActivity(intent);
        finish();
    }
}
