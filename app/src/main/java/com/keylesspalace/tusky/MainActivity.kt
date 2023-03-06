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

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.MarginPageTransformer
import at.connyduck.calladapter.networkresult.fold
import autodispose2.androidx.lifecycle.autoDispose
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.FixedSizeDrawable
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.keylesspalace.tusky.appstore.AnnouncementReadEvent
import com.keylesspalace.tusky.appstore.CacheUpdater
import com.keylesspalace.tusky.appstore.Event
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MainTabsChangedEvent
import com.keylesspalace.tusky.appstore.ProfileEditedEvent
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.announcements.AnnouncementsActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.canHandleMimeType
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.components.notifications.disableAllNotifications
import com.keylesspalace.tusky.components.notifications.enablePushNotificationsWithFallback
import com.keylesspalace.tusky.components.notifications.showMigrationNoticeIfNecessary
import com.keylesspalace.tusky.components.preference.PreferencesActivity
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusActivity
import com.keylesspalace.tusky.components.search.SearchActivity
import com.keylesspalace.tusky.components.trending.TrendingActivity
import com.keylesspalace.tusky.databinding.ActivityMainBinding
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.DraftsAlert
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.FabFragment
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.pager.MainPagerAdapter
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.DeveloperToolsUseCase
import com.keylesspalace.tusky.usecase.LogoutUsecase
import com.keylesspalace.tusky.util.deleteStaleCachedMedia
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getDimension
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.reduceSwipeSensitivity
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.updateShortcut
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.materialdrawer.holder.BadgeStyle
import com.mikepenz.materialdrawer.holder.ColorHolder
import com.mikepenz.materialdrawer.holder.StringHolder
import com.mikepenz.materialdrawer.iconics.iconicsIcon
import com.mikepenz.materialdrawer.model.AbstractDrawerItem
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import com.mikepenz.materialdrawer.model.interfaces.descriptionRes
import com.mikepenz.materialdrawer.model.interfaces.descriptionText
import com.mikepenz.materialdrawer.model.interfaces.iconRes
import com.mikepenz.materialdrawer.model.interfaces.iconUrl
import com.mikepenz.materialdrawer.model.interfaces.nameRes
import com.mikepenz.materialdrawer.model.interfaces.nameText
import com.mikepenz.materialdrawer.util.AbstractDrawerImageLoader
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import com.mikepenz.materialdrawer.util.addItems
import com.mikepenz.materialdrawer.util.addItemsAtPosition
import com.mikepenz.materialdrawer.util.updateBadge
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import de.c1710.filemojicompat_ui.helpers.EMOJI_PREFERENCE
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainActivity : BottomSheetActivity(), ActionButtonActivity, HasAndroidInjector, MenuProvider {
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var eventHub: EventHub

    @Inject
    lateinit var cacheUpdater: CacheUpdater

    @Inject
    lateinit var logoutUsecase: LogoutUsecase

    @Inject
    lateinit var draftsAlert: DraftsAlert

    @Inject
    lateinit var developerToolsUseCase: DeveloperToolsUseCase

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private lateinit var header: AccountHeaderView

    private var notificationTabPosition = 0
    private var onTabSelectedListener: OnTabSelectedListener? = null

    private var unreadAnnouncementsCount = 0

    private val preferences by unsafeLazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private lateinit var glide: RequestManager

    private var accountLocked: Boolean = false

    // We need to know if the emoji pack has been changed
    private var selectedEmojiPack: String? = null

    /** Mediate between binding.viewPager and the chosen tab layout */
    private var tabLayoutMediator: TabLayoutMediator? = null

    /** Adapter for the different timeline tabs */
    private lateinit var tabAdapter: MainPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeAccount = accountManager.activeAccount
            ?: return // will be redirected to LoginActivity by BaseActivity

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
            if (accountRequested && accountId != activeAccount.id) {
                accountManager.setActiveAccount(accountId)
            }

            val openDrafts = intent.getBooleanExtra(OPEN_DRAFTS, false)

            if (canHandleMimeType(intent.type)) {
                // Sharing to Tusky from an external app
                if (accountRequested) {
                    // The correct account is already active
                    forwardShare(intent)
                } else {
                    // No account was provided, show the chooser
                    showAccountChooserDialog(
                        getString(R.string.action_share_as), true,
                        object : AccountSelectionListener {
                            override fun onAccountSelected(account: AccountEntity) {
                                val requestedId = account.id
                                if (requestedId == activeAccount.id) {
                                    // The correct account is already active
                                    forwardShare(intent)
                                } else {
                                    // A different account was requested, restart the activity
                                    intent.putExtra(NotificationHelper.ACCOUNT_ID, requestedId)
                                    changeAccount(requestedId, intent)
                                }
                            }
                        }
                    )
                }
            } else if (openDrafts) {
                val intent = DraftsActivity.newIntent(this)
                startActivity(intent)
            } else if (accountRequested && savedInstanceState == null) {
                // user clicked a notification, show follow requests for type FOLLOW_REQUEST,
                // otherwise show notification tab
                if (intent.getStringExtra(NotificationHelper.TYPE) == Notification.Type.FOLLOW_REQUEST.name) {
                    val intent = AccountListActivity.newIntent(this, AccountListActivity.Type.FOLLOW_REQUESTS, accountLocked = true)
                    startActivityWithSlideInAnimation(intent)
                } else {
                    showNotificationTab = true
                }
            }
        }
        window.statusBarColor = Color.TRANSPARENT // don't draw a status bar, the DrawerLayout and the MaterialDrawerLayout have their own
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)

        glide = Glide.with(this)

        binding.composeButton.setOnClickListener {
            val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
            startActivity(composeIntent)
        }

        val hideTopToolbar = preferences.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        binding.mainToolbar.visible(!hideTopToolbar)

        loadDrawerAvatar(activeAccount.profilePictureUrl, true)

        addMenuProvider(this)

        binding.viewPager.reduceSwipeSensitivity()

        setupDrawer(
            savedInstanceState,
            addSearchButton = hideTopToolbar,
            addTrendingButton = !accountManager.activeAccount!!.tabPreferences.hasTab(TRENDING)
        )

        /* Fetch user info while we're doing other things. This has to be done after setting up the
         * drawer, though, because its callback touches the header in the drawer. */
        fetchUserInfo()

        fetchAnnouncements()

        // Initialise the tab adapter and set to viewpager. Fragments appear to be leaked if the
        // adapter changes over the life of the viewPager (the adapter, not its contents), so set
        // the initial list of tabs to empty, and set the full list later in setupTabs(). See
        // https://github.com/tuskyapp/Tusky/issues/3251 for details.
        tabAdapter = MainPagerAdapter(emptyList(), this)
        binding.viewPager.adapter = tabAdapter

        setupTabs(showNotificationTab)

        eventHub.events
            .observeOn(AndroidSchedulers.mainThread())
            .autoDispose(this, Lifecycle.Event.ON_DESTROY)
            .subscribe { event: Event? ->
                when (event) {
                    is ProfileEditedEvent -> onFetchUserInfoSuccess(event.newProfileData)
                    is MainTabsChangedEvent -> {
                        refreshMainDrawerItems(
                            addSearchButton = hideTopToolbar,
                            addTrendingButton = !event.newTabs.hasTab(TRENDING),
                        )

                        setupTabs(false)
                    }

                    is AnnouncementReadEvent -> {
                        unreadAnnouncementsCount--
                        updateAnnouncementsBadge()
                    }
                }
            }

        Schedulers.io().scheduleDirect {
            // Flush old media that was cached for sharing
            deleteStaleCachedMedia(applicationContext.getExternalFilesDir("Tusky"))
        }

        selectedEmojiPack = preferences.getString(EMOJI_PREFERENCE, "")

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        binding.mainDrawerLayout.isOpen -> {
                            binding.mainDrawerLayout.close()
                        }
                        binding.viewPager.currentItem != 0 -> {
                            binding.viewPager.currentItem = 0
                        }
                        else -> {
                            finish()
                        }
                    }
                }
            }
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this, true)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.activity_main, menu)
        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@MainActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.mainToolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                startActivity(SearchActivity.getIntent(this@MainActivity))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        val currentEmojiPack = preferences.getString(EMOJI_PREFERENCE, "")
        if (currentEmojiPack != selectedEmojiPack) {
            Log.d(
                TAG,
                "onResume: EmojiPack has been changed from %s to %s"
                    .format(selectedEmojiPack, currentEmojiPack)
            )
            selectedEmojiPack = currentEmojiPack
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        // For some reason the navigation drawer is opened when the activity is recreated
        if (binding.mainDrawerLayout.isOpen) {
            binding.mainDrawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (binding.mainDrawerLayout.isOpen) {
                    binding.mainDrawerLayout.close()
                } else {
                    binding.mainDrawerLayout.open()
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

        if (intent != null) {
            val redirectUrl = intent.getStringExtra(REDIRECT_URL)
            if (redirectUrl != null) {
                viewUrl(redirectUrl, PostLookupFallbackBehavior.DISPLAY_ERROR)
            }
        }
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

    private fun setupDrawer(
        savedInstanceState: Bundle?,
        addSearchButton: Boolean,
        addTrendingButton: Boolean
    ) {

        val drawerOpenClickListener = View.OnClickListener { binding.mainDrawerLayout.open() }

        binding.mainToolbar.setNavigationOnClickListener(drawerOpenClickListener)
        binding.topNavAvatar.setOnClickListener(drawerOpenClickListener)
        binding.bottomNavAvatar.setOnClickListener(drawerOpenClickListener)

        header = AccountHeaderView(this).apply {
            headerBackgroundScaleType = ImageView.ScaleType.CENTER_CROP
            currentHiddenInList = true
            onAccountHeaderListener = { _: View?, profile: IProfile, current: Boolean -> handleProfileClick(profile, current) }
            addProfile(
                ProfileSettingDrawerItem().apply {
                    identifier = DRAWER_ITEM_ADD_ACCOUNT
                    nameRes = R.string.add_account_name
                    descriptionRes = R.string.add_account_description
                    iconicsIcon = GoogleMaterial.Icon.gmd_add
                },
                0
            )
            attachToSliderView(binding.mainDrawer)
            dividerBelowHeader = false
            closeDrawerOnProfileListClick = true
        }

        header.accountHeaderBackground.setColorFilter(getColor(R.color.headerBackgroundFilter))
        header.accountHeaderBackground.setBackgroundColor(MaterialColors.getColor(header, R.attr.colorBackgroundAccent))
        val animateAvatars = preferences.getBoolean("animateGifAvatars", false)

        DrawerImageLoader.init(object : AbstractDrawerImageLoader() {
            override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
                if (animateAvatars) {
                    glide.load(uri)
                        .placeholder(placeholder)
                        .into(imageView)
                } else {
                    glide.asBitmap()
                        .load(uri)
                        .placeholder(placeholder)
                        .into(imageView)
                }
            }

            override fun cancel(imageView: ImageView) {
                glide.clear(imageView)
            }

            override fun placeholder(ctx: Context, tag: String?): Drawable {
                if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
                    return ctx.getDrawable(R.drawable.avatar_default)!!
                }

                return super.placeholder(ctx, tag)
            }
        })

        binding.mainDrawer.apply {
            refreshMainDrawerItems(addSearchButton, addTrendingButton)
            setSavedInstance(savedInstanceState)
        }
    }

    private fun refreshMainDrawerItems(addSearchButton: Boolean, addTrendingButton: Boolean) {
        binding.mainDrawer.apply {
            itemAdapter.clear()
            tintStatusBar = true
            addItems(
                primaryDrawerItem {
                    nameRes = R.string.action_edit_profile
                    iconicsIcon = GoogleMaterial.Icon.gmd_person
                    onClick = {
                        val intent = Intent(context, EditProfileActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_favourites
                    isSelectable = false
                    iconicsIcon = GoogleMaterial.Icon.gmd_star
                    onClick = {
                        val intent = StatusListActivity.newFavouritesIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_bookmarks
                    iconicsIcon = GoogleMaterial.Icon.gmd_bookmark
                    onClick = {
                        val intent = StatusListActivity.newBookmarksIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_view_follow_requests
                    iconicsIcon = GoogleMaterial.Icon.gmd_person_add
                    onClick = {
                        val intent = AccountListActivity.newIntent(context, AccountListActivity.Type.FOLLOW_REQUESTS, accountLocked = accountLocked)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_lists
                    iconicsIcon = GoogleMaterial.Icon.gmd_list
                    onClick = {
                        startActivityWithSlideInAnimation(ListsActivity.newIntent(context))
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_access_drafts
                    iconRes = R.drawable.ic_notebook
                    onClick = {
                        val intent = DraftsActivity.newIntent(context)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                primaryDrawerItem {
                    nameRes = R.string.action_access_scheduled_posts
                    iconRes = R.drawable.ic_access_time
                    onClick = {
                        startActivityWithSlideInAnimation(ScheduledStatusActivity.newIntent(context))
                    }
                },
                primaryDrawerItem {
                    identifier = DRAWER_ITEM_ANNOUNCEMENTS
                    nameRes = R.string.title_announcements
                    iconRes = R.drawable.ic_bullhorn_24dp
                    onClick = {
                        startActivityWithSlideInAnimation(AnnouncementsActivity.newIntent(context))
                    }
                    badgeStyle = BadgeStyle().apply {
                        textColor = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, com.google.android.material.R.attr.colorOnPrimary))
                        color = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, com.google.android.material.R.attr.colorPrimary))
                    }
                },
                DividerDrawerItem(),
                secondaryDrawerItem {
                    nameRes = R.string.action_view_account_preferences
                    iconRes = R.drawable.ic_account_settings
                    onClick = {
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.ACCOUNT_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_view_preferences
                    iconicsIcon = GoogleMaterial.Icon.gmd_settings
                    onClick = {
                        val intent = PreferencesActivity.newIntent(context, PreferencesActivity.GENERAL_PREFERENCES)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.about_title_activity
                    iconicsIcon = GoogleMaterial.Icon.gmd_info
                    onClick = {
                        val intent = Intent(context, AboutActivity::class.java)
                        startActivityWithSlideInAnimation(intent)
                    }
                },
                secondaryDrawerItem {
                    nameRes = R.string.action_logout
                    iconRes = R.drawable.ic_logout
                    onClick = ::logout
                }
            )

            if (addSearchButton) {
                binding.mainDrawer.addItemsAtPosition(
                    4,
                    primaryDrawerItem {
                        nameRes = R.string.action_search
                        iconicsIcon = GoogleMaterial.Icon.gmd_search
                        onClick = {
                            startActivityWithSlideInAnimation(SearchActivity.getIntent(context))
                        }
                    }
                )
            }

            if (addTrendingButton) {
                binding.mainDrawer.addItemsAtPosition(
                    5,
                    primaryDrawerItem {
                        nameRes = R.string.title_public_trending_hashtags
                        iconicsIcon = GoogleMaterial.Icon.gmd_trending_up
                        onClick = {
                            startActivityWithSlideInAnimation(TrendingActivity.getIntent(context))
                        }
                    }
                )
            }
        }

        if (BuildConfig.DEBUG) {
            // Add a "Developer tools" entry. Code that makes it easier to
            // set the app state at runtime belongs here, it will never
            // be exposed to users.
            binding.mainDrawer.addItems(
                DividerDrawerItem(),
                secondaryDrawerItem {
                    nameText = "Developer tools"
                    isEnabled = true
                    iconicsIcon = GoogleMaterial.Icon.gmd_developer_mode
                    onClick = {
                        buildDeveloperToolsDialog().show()
                    }
                }
            )
        }
    }

    private fun buildDeveloperToolsDialog(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("Developer Tools")
            .setItems(
                arrayOf("Create \"Load more\" gap")
            ) { _, which ->
                Log.d(TAG, "Developer tools: $which")
                when (which) {
                    0 -> {
                        Log.d(TAG, "Creating \"Load more\" gap")
                        lifecycleScope.launch {
                            accountManager.activeAccount?.let {
                                developerToolsUseCase.createLoadMoreGap(
                                    it.id
                                )
                            }
                        }
                    }
                }
            }
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(binding.mainDrawer.saveInstanceState(outState))
    }

    private fun setupTabs(selectNotificationTab: Boolean) {
        val activeTabLayout = if (preferences.getString(PrefKeys.MAIN_NAV_POSITION, "top") == "bottom") {
            val actionBarSize = getDimension(this, androidx.appcompat.R.attr.actionBarSize)
            val fabMargin = resources.getDimensionPixelSize(R.dimen.fabMargin)
            (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = actionBarSize + fabMargin
            binding.topNav.hide()
            binding.bottomTabLayout
        } else {
            binding.bottomNav.hide()
            (binding.viewPager.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = 0
            (binding.composeButton.layoutParams as CoordinatorLayout.LayoutParams).anchorId = R.id.viewPager
            binding.tabLayout
        }

        // Save the previous tab so it can be restored later
        val previousTab = tabAdapter.tabs.getOrNull(binding.viewPager.currentItem)

        val tabs = accountManager.activeAccount!!.tabPreferences

        // Detach any existing mediator before changing tab contents and attaching a new mediator
        tabLayoutMediator?.detach()

        tabAdapter.tabs = tabs
        tabAdapter.notifyItemRangeChanged(0, tabs.size)

        tabLayoutMediator = TabLayoutMediator(activeTabLayout, binding.viewPager, true) {
            tab: TabLayout.Tab, position: Int ->
            tab.icon = AppCompatResources.getDrawable(this@MainActivity, tabs[position].icon)
            tab.contentDescription = when (tabs[position].id) {
                LIST -> tabs[position].arguments[1]
                else -> getString(tabs[position].text)
            }
        }.also { it.attach() }

        // Selected tab is either
        // - Notification tab (if appropriate)
        // - The previously selected tab (if it hasn't been removed)
        // - Left-most tab
        val position = if (selectNotificationTab) {
            tabs.indexOfFirst { it.id == NOTIFICATIONS }
        } else {
            previousTab?.let { tabs.indexOfFirst { it == previousTab } }
        }.takeIf { it != -1 } ?: 0
        binding.viewPager.setCurrentItem(position, false)

        val pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        binding.viewPager.setPageTransformer(MarginPageTransformer(pageMargin))

        val enableSwipeForTabs = preferences.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.viewPager.isUserInputEnabled = enableSwipeForTabs

        onTabSelectedListener?.let {
            activeTabLayout.removeOnTabSelectedListener(it)
        }

        onTabSelectedListener = object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.mainToolbar.title = tabs[tab.position].title(this@MainActivity)

                refreshComposeButtonState(tabAdapter, tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                val fragment = tabAdapter.getFragment(tab.position)
                if (fragment is ReselectableFragment) {
                    (fragment as ReselectableFragment).onReselect()
                }

                refreshComposeButtonState(tabAdapter, tab.position)
            }
        }.also {
            activeTabLayout.addOnTabSelectedListener(it)
        }

        val activeTabPosition = if (selectNotificationTab) notificationTabPosition else 0
        supportActionBar?.title = tabs[activeTabPosition].title(this@MainActivity)
        binding.mainToolbar.setOnClickListener {
            (tabAdapter.getFragment(activeTabLayout.selectedTabPosition) as? ReselectableFragment)?.onReselect()
        }

        updateProfiles()
    }

    private fun refreshComposeButtonState(adapter: MainPagerAdapter, tabPosition: Int) {
        adapter.getFragment(tabPosition)?.also { fragment ->
            if (fragment is FabFragment) {
                if (fragment.isFabVisible()) {
                    binding.composeButton.show()
                } else {
                    binding.composeButton.hide()
                }
            } else {
                binding.composeButton.show()
            }
        }
    }

    private fun handleProfileClick(profile: IProfile, current: Boolean): Boolean {
        val activeAccount = accountManager.activeAccount

        // open profile when active image was clicked
        if (current && activeAccount != null) {
            val intent = AccountActivity.getIntent(this, activeAccount.accountId)
            startActivityWithSlideInAnimation(intent)
            return false
        }
        // open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(LoginActivity.getIntent(this, LoginActivity.MODE_ADDITIONAL_LOGIN))
            return false
        }
        // change Account
        changeAccount(profile.identifier, null)
        return false
    }

    private fun changeAccount(newSelectedId: Long, forward: Intent?) {
        cacheUpdater.stop()
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
                .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                    binding.appBar.hide()
                    binding.viewPager.hide()
                    binding.progressBar.show()
                    binding.bottomNav.hide()
                    binding.composeButton.hide()

                    lifecycleScope.launch {
                        val otherAccountAvailable = logoutUsecase.logout()
                        val intent = if (otherAccountAvailable) {
                            Intent(this@MainActivity, MainActivity::class.java)
                        } else {
                            LoginActivity.getIntent(this@MainActivity, LoginActivity.MODE_DEFAULT)
                        }
                        startActivity(intent)
                        finishWithoutSlideOutAnimation()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun fetchUserInfo() = lifecycleScope.launch {
        mastodonApi.accountVerifyCredentials().fold(
            { userInfo ->
                onFetchUserInfoSuccess(userInfo)
            },
            { throwable ->
                Log.e(TAG, "Failed to fetch user info. " + throwable.message)
            }
        )
    }

    private fun onFetchUserInfoSuccess(me: Account) {
        glide.asBitmap()
            .load(me.header)
            .into(header.accountHeaderBackground)

        loadDrawerAvatar(me.avatar, false)

        accountManager.updateActiveAccount(me)
        NotificationHelper.createNotificationChannelsForAccount(accountManager.activeAccount!!, this)

        // Setup push notifications
        showMigrationNoticeIfNecessary(this, binding.mainCoordinatorLayout, binding.composeButton, accountManager)
        if (NotificationHelper.areNotificationsEnabled(this, accountManager)) {
            lifecycleScope.launch {
                enablePushNotificationsWithFallback(this@MainActivity, mastodonApi, accountManager)
            }
        } else {
            disableAllNotifications(this, accountManager)
        }

        accountLocked = me.locked

        updateProfiles()
        updateShortcut(this, accountManager.activeAccount!!)
    }

    private fun loadDrawerAvatar(avatarUrl: String, showPlaceholder: Boolean) {

        val hideTopToolbar = preferences.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        val animateAvatars = preferences.getBoolean("animateGifAvatars", false)

        if (hideTopToolbar) {
            val navOnBottom = preferences.getString("mainNavPosition", "top") == "bottom"

            val avatarView = if (navOnBottom) {
                binding.bottomNavAvatar.show()
                binding.bottomNavAvatar
            } else {
                binding.topNavAvatar.show()
                binding.topNavAvatar
            }

            if (animateAvatars) {
                Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatarView)
            } else {
                Glide.with(this)
                    .asBitmap()
                    .load(avatarUrl)
                    .placeholder(R.drawable.avatar_default)
                    .into(avatarView)
            }
        } else {

            binding.bottomNavAvatar.hide()
            binding.topNavAvatar.hide()

            val navIconSize = resources.getDimensionPixelSize(R.dimen.avatar_toolbar_nav_icon_size)

            if (animateAvatars) {
                glide.asDrawable()
                    .load(avatarUrl)
                    .transform(
                        RoundedCorners(resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp))
                    )
                    .apply {
                        if (showPlaceholder) {
                            placeholder(R.drawable.avatar_default)
                        }
                    }
                    .into(object : CustomTarget<Drawable>(navIconSize, navIconSize) {

                        override fun onLoadStarted(placeholder: Drawable?) {
                            if (placeholder != null) {
                                binding.mainToolbar.navigationIcon =
                                    FixedSizeDrawable(placeholder, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            if (resource is Animatable) {
                                resource.start()
                            }
                            binding.mainToolbar.navigationIcon =
                                FixedSizeDrawable(resource, navIconSize, navIconSize)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            if (placeholder != null) {
                                binding.mainToolbar.navigationIcon =
                                    FixedSizeDrawable(placeholder, navIconSize, navIconSize)
                            }
                        }
                    })
            } else {
                glide.asBitmap()
                    .load(avatarUrl)
                    .transform(
                        RoundedCorners(resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp))
                    )
                    .apply {
                        if (showPlaceholder) {
                            placeholder(R.drawable.avatar_default)
                        }
                    }
                    .into(object : CustomTarget<Bitmap>(navIconSize, navIconSize) {

                        override fun onLoadStarted(placeholder: Drawable?) {
                            if (placeholder != null) {
                                binding.mainToolbar.navigationIcon =
                                    FixedSizeDrawable(placeholder, navIconSize, navIconSize)
                            }
                        }

                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            binding.mainToolbar.navigationIcon = FixedSizeDrawable(
                                BitmapDrawable(resources, resource),
                                navIconSize,
                                navIconSize
                            )
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            if (placeholder != null) {
                                binding.mainToolbar.navigationIcon =
                                    FixedSizeDrawable(placeholder, navIconSize, navIconSize)
                            }
                        }
                    })
            }
        }
    }

    private fun fetchAnnouncements() {
        lifecycleScope.launch {
            mastodonApi.listAnnouncements(false)
                .fold(
                    { announcements ->
                        unreadAnnouncementsCount = announcements.count { !it.read }
                        updateAnnouncementsBadge()
                    },
                    { throwable ->
                        Log.w(TAG, "Failed to fetch announcements.", throwable)
                    }
                )
        }
    }

    private fun updateAnnouncementsBadge() {
        binding.mainDrawer.updateBadge(DRAWER_ITEM_ANNOUNCEMENTS, StringHolder(if (unreadAnnouncementsCount <= 0) null else unreadAnnouncementsCount.toString()))
    }

    private fun updateProfiles() {
        val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val profiles: MutableList<IProfile> =
            accountManager.getAllAccountsOrderedByActive().map { acc ->
                ProfileDrawerItem().apply {
                    isSelected = acc.isActive
                    nameText = acc.displayName.emojify(acc.emojis, header, animateEmojis)
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
        binding.mainToolbar.subtitle = if (accountManager.shouldDisplaySelfUsername(this)) {
            accountManager.activeAccount!!.fullName
        } else null
    }

    override fun getActionButton() = binding.composeButton

    override fun androidInjector() = androidInjector

    companion object {
        private const val TAG = "MainActivity" // logging tag
        private const val DRAWER_ITEM_ADD_ACCOUNT: Long = -13
        private const val DRAWER_ITEM_ANNOUNCEMENTS: Long = 14
        const val REDIRECT_URL = "redirectUrl"
        const val OPEN_DRAFTS = "draft"
    }
}

private inline fun primaryDrawerItem(block: PrimaryDrawerItem.() -> Unit): PrimaryDrawerItem {
    return PrimaryDrawerItem()
        .apply {
            isSelectable = false
            isIconTinted = true
        }
        .apply(block)
}

private inline fun secondaryDrawerItem(block: SecondaryDrawerItem.() -> Unit): SecondaryDrawerItem {
    return SecondaryDrawerItem()
        .apply {
            isSelectable = false
            isIconTinted = true
        }
        .apply(block)
}

private var AbstractDrawerItem<*, *>.onClick: () -> Unit
    get() = throw UnsupportedOperationException()
    set(value) {
        onDrawerItemClickListener = { _, _, _ ->
            value()
            false
        }
    }
