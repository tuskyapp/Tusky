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

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
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
import com.keylesspalace.tusky.AccountActivity.Companion.getIntent
import com.keylesspalace.tusky.LoginActivity.Companion.getIntent
import com.keylesspalace.tusky.PreferencesActivity.Companion.newIntent
import com.keylesspalace.tusky.StatusListActivity.Companion.newBookmarksIntent
import com.keylesspalace.tusky.StatusListActivity.Companion.newFavouritesIntent
import com.keylesspalace.tusky.appstore.*
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.canHandleMimeType
import com.keylesspalace.tusky.components.conversation.ConversationsRepository
import com.keylesspalace.tusky.components.scheduled.ScheduledTootActivity
import com.keylesspalace.tusky.components.search.SearchActivity.Companion.getIntent
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.fragment.SFragment
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.pager.MainPagerAdapter
import com.keylesspalace.tusky.util.*
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.*
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import com.uber.autodispose.AutoDispose
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
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

    private lateinit var header: AccountHeader
    private lateinit var drawer: Drawer

    private var notificationTabPosition = 0

    private var adapter: MainPagerAdapter? = null

    private val emojiInitCallback: InitCallback = object : InitCallback() {
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
        composeButton.setOnClickListener(View.OnClickListener { v: View? ->
            val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
            startActivity(composeIntent)
        })
        setupDrawer()

        /* Fetch user info while we're doing other things. This has to be done after setting up the
         * drawer, though, because its callback touches the header in the drawer. */fetchUserInfo()
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
                .`as`(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)))
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
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
        } else if (viewPager.currentItem != 0) {
            viewPager.currentItem = 0
        } else {
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (drawer.isDrawerOpen) {
                    drawer.closeDrawer()
                } else {
                    drawer.openDrawer()
                }
                return true
            }
            KeyEvent.KEYCODE_SEARCH -> {
                startActivityWithSlideInAnimation(getIntent(this))
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
        val intent = intent
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

    private fun setupDrawer() {
        header = AccountHeaderBuilder()
                .withActivity(this)
                .withDividerBelowHeader(false)
                .withHeaderBackgroundScaleType(ImageView.ScaleType.CENTER_CROP)
                .withCurrentProfileHiddenInList(true)
                .withOnAccountHeaderListener { view: View?, profile: IProfile<*>, current: Boolean -> handleProfileClick(profile, current) }
                .addProfiles(
                        ProfileSettingDrawerItem()
                                .withIdentifier(DRAWER_ITEM_ADD_ACCOUNT)
                                .withName(R.string.add_account_name)
                                .withDescription(R.string.add_account_description)
                                .withIcon(GoogleMaterial.Icon.gmd_add))
                .build()
        header.view
                .findViewById<View>(R.id.material_drawer_account_header_current).contentDescription = getString(R.string.action_view_profile)
        val background = header.headerBackgroundView
        background.setColorFilter(ContextCompat.getColor(this, R.color.header_background_filter))
        background.setBackgroundColor(ContextCompat.getColor(this, R.color.tusky_grey_10))
        val animateAvatars = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("animateGifAvatars", false)
        DrawerImageLoader.init(object : AbstractDrawerImageLoader() {
            override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String) {
                if (animateAvatars) {
                    Glide.with(this@MainActivity)
                            .load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                } else {
                    Glide.with(this@MainActivity)
                            .asBitmap()
                            .load(uri)
                            .placeholder(placeholder)
                            .into(imageView)
                }
            }

            override fun cancel(imageView: ImageView) {
                Glide.with(this@MainActivity).clear(imageView)
            }
        })
        val listItems: MutableList<IDrawerItem<*, *>> = ArrayList(11)
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_EDIT_PROFILE).withName(R.string.action_edit_profile).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_person))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_FAVOURITES).withName(R.string.action_view_favourites).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_star))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_BOOKMARKS).withName(R.string.action_view_bookmarks).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_bookmark))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_LISTS).withName(R.string.action_lists).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_list))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SEARCH).withName(R.string.action_search).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_search))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SAVED_TOOT).withName(R.string.action_access_saved_toot).withSelectable(false).withIcon(R.drawable.ic_notebook).withIconTintingEnabled(true))
        listItems.add(PrimaryDrawerItem().withIdentifier(DRAWER_ITEM_SCHEDULED_TOOT).withName(R.string.action_access_scheduled_toot).withSelectable(false).withIcon(R.drawable.ic_access_time).withIconTintingEnabled(true))
        listItems.add(DividerDrawerItem())
        listItems.add(SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ACCOUNT_SETTINGS).withName(R.string.action_view_account_preferences).withSelectable(false).withIcon(R.drawable.ic_account_settings).withIconTintingEnabled(true))
        listItems.add(SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_SETTINGS).withName(R.string.action_view_preferences).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_settings))
        listItems.add(SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_ABOUT).withName(R.string.about_title_activity).withSelectable(false).withIcon(GoogleMaterial.Icon.gmd_info))
        listItems.add(SecondaryDrawerItem().withIdentifier(DRAWER_ITEM_LOG_OUT).withName(R.string.action_logout).withSelectable(false).withIcon(R.drawable.ic_logout).withIconTintingEnabled(true))
        drawer = DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(header)
                .withHasStableIds(true)
                .withSelectedItem(-1)
                .withDrawerItems(listItems)
                .withToolbar(mainToolbar)
                .withOnDrawerItemClickListener { _: View?, _: Int, drawerItem: IDrawerItem<*, *>? ->
                    if (drawerItem != null) {
                        val drawerItemIdentifier = drawerItem.identifier
                        if (drawerItemIdentifier == DRAWER_ITEM_EDIT_PROFILE) {
                            val intent = Intent(this@MainActivity, EditProfileActivity::class.java)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FAVOURITES) {
                            val intent = newFavouritesIntent(this@MainActivity)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_BOOKMARKS) {
                            val intent = newBookmarksIntent(this@MainActivity)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SEARCH) {
                            startActivityWithSlideInAnimation(getIntent(this))
                        } else if (drawerItemIdentifier == DRAWER_ITEM_ACCOUNT_SETTINGS) {
                            val intent = newIntent(this@MainActivity, PreferencesActivity.ACCOUNT_PREFERENCES)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SETTINGS) {
                            val intent = newIntent(this@MainActivity, PreferencesActivity.GENERAL_PREFERENCES)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_ABOUT) {
                            val intent = Intent(this@MainActivity, AboutActivity::class.java)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LOG_OUT) {
                            logout()
                        } else if (drawerItemIdentifier == DRAWER_ITEM_FOLLOW_REQUESTS) {
                            val intent = Intent(this@MainActivity, AccountListActivity::class.java)
                            intent.putExtra("type", AccountListActivity.Type.FOLLOW_REQUESTS)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SAVED_TOOT) {
                            val intent = Intent(this@MainActivity, SavedTootActivity::class.java)
                            startActivityWithSlideInAnimation(intent)
                        } else if (drawerItemIdentifier == DRAWER_ITEM_SCHEDULED_TOOT) {
                            startActivityWithSlideInAnimation(ScheduledTootActivity.newIntent(this))
                        } else if (drawerItemIdentifier == DRAWER_ITEM_LISTS) {
                            startActivityWithSlideInAnimation(ListsActivity.newIntent(this))
                        }
                    }
                    false
                }
                .build()
        if (BuildConfig.DEBUG) {
            val debugItem: IDrawerItem<*, *> = SecondaryDrawerItem()
                    .withIdentifier(1337)
                    .withName("debug")
                    .withDisabledTextColor(Color.GREEN)
                    .withSelectable(false)
                    .withEnabled(false)
            drawer.addItem(debugItem)
        }
        EmojiCompat.get().registerInitCallback(emojiInitCallback)
    }

    private fun setupTabs(selectNotificationTab: Boolean) {
        val tabs = accountManager.activeAccount!!.tabPreferences
        adapter = MainPagerAdapter(tabs, this)
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager, TabConfigurationStrategy { _: TabLayout.Tab?, _: Int -> }).attach()
        tabLayout.removeAllTabs()
        for (i in tabs.indices) {
            val tab = tabLayout!!.newTab()
                    .setIcon(tabs[i].icon)
            if (tabs[i].id == LIST) {
                tab.contentDescription = tabs[i].arguments[1]
            } else {
                tab.setContentDescription(tabs[i].text)
            }
            tabLayout!!.addTab(tab)
            if (tabs[i].id == NOTIFICATIONS) {
                notificationTabPosition = i
                if (selectNotificationTab) {
                    tab.select()
                }
            }
        }
    }

    private fun handleProfileClick(profile: IProfile<*>, current: Boolean): Boolean {
        val activeAccount = accountManager.activeAccount

        //open profile when active image was clicked
        if (current && activeAccount != null) {
            val intent = getIntent(this, activeAccount.accountId)
            startActivityWithSlideInAnimation(intent)
            Handler().postDelayed({ drawer.closeDrawer() }, 100)
            return true
        }
        //open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(getIntent(this, true))
            Handler().postDelayed({ drawer.closeDrawer() }, 100)
            return true
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
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.action_logout)
                    .setMessage(getString(R.string.action_logout_confirm, activeAccount.fullName))
                    .setPositiveButton(android.R.string.yes) { _: DialogInterface?, _: Int ->
                        NotificationHelper.deleteNotificationChannelsForAccount(accountManager.activeAccount!!, this@MainActivity)
                        cacheUpdater.clearForUser(activeAccount.id)
                        conversationRepository.deleteCacheForAccount(activeAccount.id)
                        removeShortcut(this, activeAccount)
                        val newAccount = accountManager.logActiveAccountOut()
                        if (!NotificationHelper.areNotificationsEnabled(this@MainActivity, accountManager)) {
                            NotificationHelper.disablePullNotifications()
                        }
                        val intent: Intent
                        intent = if (newAccount == null) {
                            getIntent(this@MainActivity, false)
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
                .`as`(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)))
                .subscribe({ me: Account -> onFetchUserInfoSuccess(me) }) { throwable: Throwable -> onFetchUserInfoFailure(throwable) }
    }

    private fun onFetchUserInfoSuccess(me: Account) {

        // Add the header image and avatar from the account, into the navigation drawer header.
        val background = header.headerBackgroundView
        Glide.with(this@MainActivity)
                .asBitmap()
                .load(me.header)
                .into(background)
        accountManager.updateActiveAccount(me)
        NotificationHelper.createNotificationChannelsForAccount(accountManager.activeAccount!!, this)

        // Show follow requests in the menu, if this is a locked account.
        if (me.locked && drawer.getDrawerItem(DRAWER_ITEM_FOLLOW_REQUESTS) == null) {
            val followRequestsItem = PrimaryDrawerItem()
                    .withIdentifier(DRAWER_ITEM_FOLLOW_REQUESTS)
                    .withName(R.string.action_view_follow_requests)
                    .withSelectable(false)
                    .withIcon(GoogleMaterial.Icon.gmd_person_add)
            drawer.addItemAtPosition(followRequestsItem, 4)
        } else if (!me.locked) {
            drawer.removeItem(DRAWER_ITEM_FOLLOW_REQUESTS)
        }
        updateProfiles()
        updateShortcut(this, accountManager.activeAccount!!)
    }

    private fun updateProfiles() {
        val allAccounts = accountManager.getAllAccountsOrderedByActive()
        val profiles: MutableList<IProfile<*>> = ArrayList(allAccounts.size + 1)
        for (acc in allAccounts) {
            var emojifiedName: CharSequence? = CustomEmojiHelper.emojifyString(acc.displayName, acc.emojis, header.view)
            emojifiedName = EmojiCompat.get().process(emojifiedName!!)
            profiles.add(
                    ProfileDrawerItem()
                            .withSetSelected(acc.isActive)
                            .withName(emojifiedName)
                            .withIcon(acc.profilePictureUrl)
                            .withNameShown(true)
                            .withIdentifier(acc.id)
                            .withEmail(acc.fullName))
        }

        // reuse the already existing "add account" item
        for (profile in header.profiles) {
            if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
                profiles.add(profile)
                break
            }
        }
        header.clear()
        header.profiles = profiles
        header.setActiveProfile(accountManager.activeAccount!!.id)
    }

    override fun getActionButton(): FloatingActionButton? {
        return composeButton
    }

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
        private fun onFetchUserInfoFailure(throwable: Throwable) {
            Log.e(TAG, "Failed to fetch user info. " + throwable.message)
        }
    }
}