/* Copyright 2020 Tusky Contributors
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

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.emoji.text.EmojiCompat.InitCallback
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.MarginPageTransformer
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.canHandleMimeType
import com.keylesspalace.tusky.components.conversation.ConversationsRepository
import com.keylesspalace.tusky.components.scheduled.ScheduledTootActivity
import com.keylesspalace.tusky.components.search.SearchActivity
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.pager.MainPagerAdapter
import com.keylesspalace.tusky.util.*
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.materialdrawer.iconics.iconicsIcon
import com.mikepenz.materialdrawer.model.*
import com.mikepenz.materialdrawer.model.interfaces.*
import com.mikepenz.materialdrawer.util.*
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import com.uber.autodispose.android.lifecycle.autoDispose
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject

class MainActivity : BottomSheetActivity(), ActionButtonActivity, HasAndroidInjector {
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var cacheUpdater: CacheUpdater

    @Inject
    lateinit var conversationRepository: ConversationsRepository

    private lateinit var header: AccountHeaderView
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var notificationTabPosition = 0

    private var adapter: MainPagerAdapter? = null

    private val emojiInitCallback = object : InitCallback() {
        override fun onInitialized() {
            if (!isDestroyed) {
                updateProfiles()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (accountManager.activeAccount == null) {
            // will be redirected to LoginActivity by BaseActivity
            return
        }
        var showNotificationTab = false
        if (intent != null) {
            /** there are two possibilities the accountId can be passed to MainActivity:
             * - from our code as long 'account_id'
             * - from share shortcuts as String 'android.intent.extra.shortcut.ID'
             */
            var accountId = intent.getLongExtra(NotificationHelper.ACCOUNT_ID, -1)
            if (accountId == -1L) {
                val accountIdString = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
                if (accountIdString != null) {
                    accountId = accountIdString.toLong()
                }
            }
            val accountRequested = accountId != -1L
            if (accountRequested) {
                val account = accountManager.activeAccount
                if (account == null || accountId != account.id) {
                    accountManager.setActiveAccount(accountId)
                }
            }
            if (canHandleMimeType(intent.type)) {
                // Sharing to Tusky from an external app
                if (accountRequested) {
                    // The correct account is already active
                    forwardShare(intent)
                } else {
                    // No account was provided, show the chooser
                    showAccountChooserDialog(getString(R.string.action_share_as), true, object : AccountSelectionListener {
                        override fun onAccountSelected(account: AccountEntity) {
                            val requestedId = account.id
                            val activeAccount = accountManager.activeAccount
                            if (activeAccount != null && requestedId == activeAccount.id) {
                                // The correct account is already active
                                forwardShare(intent)
                            } else {
                                // A different account was requested, restart the activity
                                intent.putExtra(NotificationHelper.ACCOUNT_ID, requestedId)
                                changeAccount(requestedId, intent)
                            }
                        }
                    })
                }
            } else if (accountRequested) {
                // user clicked a notification, show notification tab and switch user if necessary
                showNotificationTab = true
            }
        }
        setContentView(R.layout.activity_main)
        composeButton.setOnClickListener {
            val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
            startActivity(composeIntent)
        }
        setupDrawer(savedInstanceState)

        /* Fetch user info while we're doing other things. This has to be done after setting up the
         * drawer, though, because its callback touches the header in the drawer. */
        fetchUserInfo()

        setupTabs(showNotificationTab)

        val pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        viewPager.setPageTransformer(MarginPageTransformer(pageMargin))
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == notificationTabPosition) {
                    NotificationHelper.clearNotificationsForActiveAccount(this@MainActivity, accountManager)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                val fragment = adapter?.getFragment(tab.position)
                if (fragment is ReselectableFragment) {
                    (fragment as ReselectableFragment).onReselect()
                }
            }
        })

        // Setup push notifications
        if (NotificationHelper.areNotificationsEnabled(this, accountManager)) {
            NotificationHelper.enablePullNotifications()
        } else {
            NotificationHelper.disablePullNotifications()
        }
        eventHub.events
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe { event: Event? ->
                    if (event is ProfileEditedEvent) {
                        onFetchUserInfoSuccess(event.newProfileData)
                    }
                    if (event is MainTabsChangedEvent) {
                        setupTabs(false)
                    }
                }

        // Flush old media that was cached for sharing
        deleteStaleCachedMedia(applicationContext.getExternalFilesDir("Tusky"))
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.clearNotificationsForActiveAccount(this, accountManager)
    }

    override fun onBackPressed() {
        if (mainDrawerLayout.isOpen) {
            mainDrawerLayout.close()
        } else if (viewPager.currentItem != 0) {
            viewPager.currentItem = 0
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (mainDrawerLayout.isOpen) {
                    mainDrawerLayout.close()
                } else {
                    mainDrawerLayout.open()
                }
                return true
            }
            KeyEvent.KEYCODE_SEARCH -> {
                startActivityWithSlideInAnimation(SearchActivity.getIntent(this))
                return true
            }
        }
        if (event.isCtrlPressed || event.isShiftPressed) {
            // FIXME: blackberry keyONE raises SHIFT key event even CTRL IS PRESSED
            when (keyCode) {
                KeyEvent.KEYCODE_N -> {

                    // open compose activity by pressing SHIFT + N (or CTRL + N)
                    val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
                    startActivity(composeIntent)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    public override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        drawerToggle.syncState()

        if (intent != null) {
            val statusUrl = intent.getStringExtra(STATUS_URL)
            if (statusUrl != null) {
                viewUrl(statusUrl, PostLookupFallbackBehavior.DISPLAY_ERROR)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EmojiCompat.get().unregisterInitCallback(emojiInitCallback)
    }

    private fun forwardShare(intent: Intent) {
        val composeIntent = Intent(this, ComposeActivity::class.java)
        composeIntent.action = intent.action
        composeIntent.type = intent.type
        composeIntent.putExtras(intent)
        composeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(composeIntent)
        finish()
    }

    private fun setupDrawer(savedInstanceState: Bundle?) {

        drawerToggle = ActionBarDrawerToggle(this, mainDrawerLayout, mainToolbar, com.mikepenz.materialdrawer.R.string.material_drawer_open, com.mikepenz.materialdrawer.R.string.material_drawer_close)

        header = AccountHeaderView(this).apply {
            headerBackgroundScaleType = ImageView.ScaleType.CENTER_CROP
            currentHiddenInList = true
            onAccountHeaderListener = { _: View?, profile: IProfile, current: Boolean -> handleProfileClick(profile, current) }
            addProfile(ProfileSettingDrawerItem().apply {
                identifier = DRAWER_ITEM_ADD_ACCOUNT
                nameRes = R.string.add_account_name
                descriptionRes = R.string.add_account_description
                iconicsIcon = GoogleMaterial.Icon.gmd_add
            }, 0)
            attachToSliderView(mainDrawer)
            dividerBelowHeader = false
            closeDrawerOnProfileListClick = true
        }

        header.accountHeaderBackground.setColorFilter(ContextCompat.getColor(this, R.color.header_background_filter))
        header.accountHeaderBackground.setBackgroundColor(ContextCompat.getColor(this, R.color.tusky_grey_10))
        val animateAvatars = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("animateGifAvatars", false)

        DrawerImageLoader.init(object : AbstractDrawerImageLoader() {
            override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
                if (animateAvatars) {
                    Glide.with(imageView.context)
                            .load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                } else {
                    Glide.with(imageView.context)
                            .asBitmap()
                            .load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                }
            }

            override fun cancel(imageView: ImageView) {
                Glide.with(imageView.context).clear(imageView)
            }

            override fun placeholder(ctx: Context, tag: String?): Drawable {
                if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
                    return ctx.getDrawable(R.drawable.avatar_default)!!
                }

                return super.placeholder(ctx, tag)
            }
        })

        mainDrawer.apply {
            addItems(
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_EDIT_PROFILE; nameRes = R.string.action_edit_profile; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_person },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_FAVOURITES; nameRes = R.string.action_view_favourites; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_star },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_BOOKMARKS; nameRes = R.string.action_view_bookmarks; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_bookmark },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_LISTS; nameRes = R.string.action_lists; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_list },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_SEARCH; nameRes = R.string.action_search; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_search },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_SAVED_TOOT; nameRes = R.string.action_access_saved_toot; isSelectable = false; iconRes = R.drawable.ic_notebook; isIconTinted = true },
                    PrimaryDrawerItem().apply { identifier = DRAWER_ITEM_SCHEDULED_TOOT; nameRes = R.string.action_access_scheduled_toot; isSelectable = false; iconRes = R.drawable.ic_access_time; isIconTinted = true },
                    DividerDrawerItem(),
                    SecondaryDrawerItem().apply { identifier = DRAWER_ITEM_ACCOUNT_SETTINGS; nameRes = R.string.action_view_account_preferences; isSelectable = false; iconRes = R.drawable.ic_account_settings; isIconTinted = true },
                    SecondaryDrawerItem().apply { identifier = DRAWER_ITEM_SETTINGS; nameRes = R.string.action_view_preferences; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_settings },
                    SecondaryDrawerItem().apply { identifier = DRAWER_ITEM_ABOUT; nameRes = R.string.about_title_activity; isSelectable = false; iconicsIcon = GoogleMaterial.Icon.gmd_info },
                    SecondaryDrawerItem().apply { identifier = DRAWER_ITEM_LOG_OUT; nameRes = R.string.action_logout; isSelectable = false; iconRes = R.drawable.ic_logout; isIconTinted = true }
            )
            onDrawerItemClickListener = { _: View?, drawerItem: IDrawerItem<*>, _: Int ->
                when (drawerItem.identifier) {
                    DRAWER_ITEM_EDIT_PROFILE -> {
                        val intent = Intent(context, EditProfileActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_FAVOURITES -> {
                        val intent = StatusListActivity.newFavouritesIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_BOOKMARKS -> {
                        val intent = StatusListActivity.newBookmarksIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_SEARCH -> {
                        startActivityWithSlideInAnimation(SearchActivity.getIntent(context))
                    }
                    DRAWER_ITEM_ACCOUNT_SETTINGS -> {
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.ACCOUNT_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_SETTINGS -> {
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.GENERAL_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_ABOUT -> {
                        val intent = Intent(context, AboutActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_LOG_OUT -> {
                        logout()
                    }
                    DRAWER_ITEM_FOLLOW_REQUESTS -> {
                        val intent = Intent(context, AccountListActivity::class.java)
                        intent.putExtra("type", AccountListActivity.Type.FOLLOW_REQUESTS)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_SAVED_TOOT -> {
                        val intent = Intent(context, SavedTootActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                    DRAWER_ITEM_SCHEDULED_TOOT -> {
                        startActivityWithSlideInAnimation(ScheduledTootActivity.newIntent(context))
                    }
                    DRAWER_ITEM_LISTS -> {
                        startActivityWithSlideInAnimation(ListsActivity.newIntent(context))
                    }
                }
                true
            }
            setSavedInstance(savedInstanceState)
        }

        if (BuildConfig.DEBUG) {
            mainDrawer.addItems(
                    SecondaryDrawerItem().apply {
                        identifier = 1337
                        nameText = "debug"
                        isEnabled = false
                        textColor = ColorStateList.valueOf(Color.GREEN)
                        isSelectable = false
                    }
            )
        }
        EmojiCompat.get().registerInitCallback(emojiInitCallback)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(mainDrawer.saveInstanceState(outState))
    }

    private fun setupTabs(selectNotificationTab: Boolean) {
        val tabs = accountManager.activeAccount!!.tabPreferences
        adapter = MainPagerAdapter(tabs, this)
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager, TabConfigurationStrategy { _: TabLayout.Tab?, _: Int -> }).attach()
        tabLayout.removeAllTabs()
        for (i in tabs.indices) {
            val tab = tabLayout.newTab()
                    .setIcon(tabs[i].icon)
            if (tabs[i].id == LIST) {
                tab.contentDescription = tabs[i].arguments[1]
            } else {
                tab.setContentDescription(tabs[i].text)
            }
            tabLayout.addTab(tab)
            if (tabs[i].id == NOTIFICATIONS) {
                notificationTabPosition = i
                if (selectNotificationTab) {
                    tab.select()
                }
            }
        }
    }

    private fun handleProfileClick(profile: IProfile, current: Boolean): Boolean {
        val activeAccount = accountManager.activeAccount

        //open profile when active image was clicked
        if (current && activeAccount != null) {
            val intent = AccountActivity.getIntent(this, activeAccount.accountId)
            startActivityWithSlideInAnimation(intent)
            return false
        }
        //open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(LoginActivity.getIntent(this, true))
            return false
        }
        //change Account
        changeAccount(profile.identifier, null)
        return false
    }

    private fun changeAccount(newSelectedId: Long, forward: Intent?) {
        cacheUpdater.stop()
        SFragment.flushFilters()
        accountManager.setActiveAccount(newSelectedId)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (forward != null) {
            intent.type = forward.type
            intent.action = forward.action
            intent.putExtras(forward)
        }
        startActivity(intent)
        finishWithoutSlideOutAnimation()
        overridePendingTransition(R.anim.explode, R.anim.explode)
    }

    private fun logout() {
        accountManager.activeAccount?.let { activeAccount ->
            AlertDialog.Builder(this)
                    .setTitle(R.string.action_logout)
                    .setMessage(getString(R.string.action_logout_confirm, activeAccount.fullName))
                    .setPositiveButton(android.R.string.yes) { _: DialogInterface?, _: Int ->
                        NotificationHelper.deleteNotificationChannelsForAccount(activeAccount, this@MainActivity)
                        cacheUpdater.clearForUser(activeAccount.id)
                        conversationRepository.deleteCacheForAccount(activeAccount.id)
                        removeShortcut(this, activeAccount)
                        val newAccount = accountManager.logActiveAccountOut()
                        if (!NotificationHelper.areNotificationsEnabled(this@MainActivity, accountManager)) {
                            NotificationHelper.disablePullNotifications()
                        }
                        val intent: Intent
                        intent = if (newAccount == null) {
                            LoginActivity.getIntent(this@MainActivity, false)
                        } else {
                            Intent(this@MainActivity, MainActivity::class.java)
                        }
                        startActivity(intent)
                        finishWithoutSlideOutAnimation()
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
        }
    }

    private fun fetchUserInfo() {
        mastodonApi.accountVerifyCredentials()
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe(
                        { userInfo ->
                            onFetchUserInfoSuccess(userInfo)
                        },
                        { throwable ->
                            Log.e(TAG, "Failed to fetch user info. " + throwable.message)
                        }
                )
    }

    private fun onFetchUserInfoSuccess(me: Account) {
        Glide.with(this)
                .asBitmap()
                .load(me.header)
                .into(header.accountHeaderBackground)

        accountManager.updateActiveAccount(me)
        NotificationHelper.createNotificationChannelsForAccount(accountManager.activeAccount!!, this)

        // Show follow requests in the menu, if this is a locked account.
        if (me.locked && mainDrawer.getDrawerItem(DRAWER_ITEM_FOLLOW_REQUESTS) == null) {
            val followRequestsItem = PrimaryDrawerItem().apply {
                identifier = DRAWER_ITEM_FOLLOW_REQUESTS
                nameRes = R.string.action_view_follow_requests
                isSelectable = false
                iconicsIcon = GoogleMaterial.Icon.gmd_person_add
            }
            mainDrawer.addItemAtPosition(4, followRequestsItem)
        } else if (!me.locked) {
            mainDrawer.removeItems(DRAWER_ITEM_FOLLOW_REQUESTS)
        }
        updateProfiles()
        updateShortcut(this, accountManager.activeAccount!!)
    }

    private fun updateProfiles() {
        val profiles: MutableList<IProfile> = accountManager.getAllAccountsOrderedByActive().map { acc ->
            val emojifiedName = EmojiCompat.get().process(CustomEmojiHelper.emojifyString(acc.displayName, acc.emojis, header))

            ProfileDrawerItem().apply {
                isSelected = acc.isActive
                nameText = emojifiedName
                iconUrl = acc.profilePictureUrl
                isNameShown = true
                identifier = acc.id
                descriptionText = acc.fullName
            }
        }.toMutableList()

        // reuse the already existing "add account" item
        for (profile in header.profiles.orEmpty()) {
            if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile)
                break
            }
        }
        header.clear()
        header.profiles = profiles
        header.setActiveProfile(accountManager.activeAccount!!.id)
    }

    override fun getActionButton(): FloatingActionButton? = composeButton

    override fun androidInjector() = androidInjector

    companion object {
        private const val TAG = "MainActivity" // logging tag
        private const val DRAWER_ITEM_ADD_ACCOUNT: Long = -13
        private const val DRAWER_ITEM_EDIT_PROFILE: Long = 0
        private const val DRAWER_ITEM_FAVOURITES: Long = 1
        private const val DRAWER_ITEM_BOOKMARKS: Long = 2
        private const val DRAWER_ITEM_LISTS: Long = 3
        private const val DRAWER_ITEM_SEARCH: Long = 4
        private const val DRAWER_ITEM_SAVED_TOOT: Long = 5
        private const val DRAWER_ITEM_ACCOUNT_SETTINGS: Long = 6
        private const val DRAWER_ITEM_SETTINGS: Long = 7
        private const val DRAWER_ITEM_ABOUT: Long = 8
        private const val DRAWER_ITEM_LOG_OUT: Long = 9
        private const val DRAWER_ITEM_FOLLOW_REQUESTS: Long = 10
        private const val DRAWER_ITEM_SCHEDULED_TOOT: Long = 11
        const val STATUS_URL = "statusUrl"
    }
}