/* Copyright 2024 Tusky Contributors
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
package com.keylesspalace.tusky

import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider.Factory
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.MainActivity.Companion.redirectIntent
import com.keylesspalace.tusky.adapter.AccountSelectionAdapter
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.components.login.LoginActivity.Companion.getIntent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.PreferencesEntryPoint
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.ActivityConstants
import com.keylesspalace.tusky.util.isBlack
import com.keylesspalace.tusky.util.overrideActivityTransitionCompat
import dagger.hilt.EntryPoints
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * All activities inheriting from BaseActivity must be annotated with @AndroidEntryPoint
 */
abstract class BaseActivity : AppCompatActivity() {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var preferences: SharedPreferences

    /**
     * Allows overriding the default ViewModelProvider.Factory for testing purposes.
     */
    var viewModelProviderFactory: Factory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (activityTransitionWasRequested()) {
            overrideActivityTransitionCompat(
                ActivityConstants.OVERRIDE_TRANSITION_OPEN,
                R.anim.activity_open_enter,
                R.anim.activity_open_exit
            )
            overrideActivityTransitionCompat(
                ActivityConstants.OVERRIDE_TRANSITION_CLOSE,
                R.anim.activity_close_enter,
                R.anim.activity_close_exit
            )
        }

        /* There isn't presently a way to globally change the theme of a whole application at
         * runtime, just individual activities. So, each activity has to set its theme before any
         * views are created. */
        val theme = preferences.getString(PrefKeys.APP_THEME, AppTheme.DEFAULT.value)
        if (isBlack(resources.configuration, theme)) {
            setTheme(R.style.TuskyBlackTheme)
        } else if (this is MainActivity) {
            // Replace the SplashTheme of MainActivity
            setTheme(R.style.TuskyTheme)
        }

        /* set the taskdescription programmatically, the theme would turn it blue */
        val appName = getString(R.string.app_name)
        val appIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val recentsBackgroundColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            Color.BLACK
        )

        setTaskDescription(TaskDescription(appName, appIcon, recentsBackgroundColor))

        val style = textStyle(preferences.getString(PrefKeys.STATUS_TEXT_SIZE, "medium"))
        getTheme().applyStyle(style, true)

        if (requiresLogin()) {
            redirectIfNotLoggedIn()
        }
    }

    private fun activityTransitionWasRequested(): Boolean {
        return intent.getBooleanExtra(OPEN_WITH_SLIDE_IN, false)
    }

    override fun attachBaseContext(newBase: Context) {
        // injected preferences not yet available at this point of the lifecycle
        val preferences =
            EntryPoints.get(newBase.applicationContext, PreferencesEntryPoint::class.java)
                .preferences()

        // Scale text in the UI from PrefKeys.UI_TEXT_SCALE_RATIO
        val uiScaleRatio = preferences.getFloat(PrefKeys.UI_TEXT_SCALE_RATIO, 100f)

        val configuration = newBase.resources.configuration

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
        val appConfiguration = newBase.applicationContext.resources.configuration

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
        configuration.fontScale = appConfiguration.fontScale * uiScaleRatio / 100f

        val fontScaleContext = newBase.createConfigurationContext(configuration)

        super.attachBaseContext(fontScaleContext)
    }

    override val defaultViewModelProviderFactory: Factory
        get() = viewModelProviderFactory ?: super.defaultViewModelProviderFactory

    protected open fun requiresLogin(): Boolean = true

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun redirectIfNotLoggedIn() {
        val currentAccounts = accountManager.accounts

        if (currentAccounts.isEmpty()) {
            val intent = getIntent(this@BaseActivity, LoginActivity.MODE_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    fun showAccountChooserDialog(
        dialogTitle: CharSequence?,
        showActiveAccount: Boolean,
        listener: AccountSelectionListener
    ) {
        val accounts = accountManager.getAllAccountsOrderedByActive().toMutableList()
        val activeAccount = accountManager.activeAccount

        when (accounts.size) {
            1 -> {
                listener.onAccountSelected(activeAccount!!)
                return
            }
            2 -> if (!showActiveAccount) {
                for (account in accounts) {
                    if (activeAccount !== account) {
                        listener.onAccountSelected(account)
                        return
                    }
                }
            }
        }
        if (!showActiveAccount && activeAccount != null) {
            accounts.remove(activeAccount)
        }
        val adapter = AccountSelectionAdapter(
            this,
            preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        )
        adapter.addAll(accounts)

        MaterialAlertDialogBuilder(this)
            .setTitle(dialogTitle)
            .setAdapter(adapter) { _: DialogInterface?, index: Int ->
                listener.onAccountSelected(accounts[index])
            }
            .show()
    }

    val openAsText: String?
        get() {
            val accounts = accountManager.getAllAccountsOrderedByActive()
            when (accounts.size) {
                0, 1 -> return null
                2 -> {
                    for (account in accounts) {
                        if (account !== accountManager.activeAccount) {
                            return getString(R.string.action_open_as, account.fullName)
                        }
                    }
                    return null
                }

                else -> return getString(R.string.action_open_as, "…")
            }
        }

    fun openAsAccount(url: String, account: AccountEntity) {
        lifecycleScope.launch {
            accountManager.setActiveAccount(account.id)
            val intent = redirectIntent(this@BaseActivity, account.id, url)

            startActivity(intent)
            finish()
        }
    }

    companion object {
        const val OPEN_WITH_SLIDE_IN = "OPEN_WITH_SLIDE_IN"

        @StyleRes
        private fun textStyle(name: String?): Int = when (name) {
            "smallest" -> R.style.TextSizeSmallest
            "small" -> R.style.TextSizeSmall
            "medium" -> R.style.TextSizeMedium
            "large" -> R.style.TextSizeLarge
            "largest" -> R.style.TextSizeLargest
            else -> R.style.TextSizeMedium
        }
    }
}
