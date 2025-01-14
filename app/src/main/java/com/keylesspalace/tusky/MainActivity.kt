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
import android.annotation.SuppressLint
import android.app.NotificationManager
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
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.SHOW_AS_ACTION_NEVER
import android.view.View
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.MarginPageTransformer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.FixedSizeDrawable
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.R as materialR
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator
import com.keylesspalace.tusky.appstore.CacheUpdater
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.account.AccountActivity
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.announcements.AnnouncementsActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.canHandleMimeType
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.components.login.LoginActivity
import com.keylesspalace.tusky.components.preference.PreferencesActivity
import com.keylesspalace.tusky.components.scheduled.ScheduledStatusActivity
import com.keylesspalace.tusky.components.search.SearchActivity
import com.keylesspalace.tusky.components.trending.TrendingActivity
import com.keylesspalace.tusky.databinding.ActivityMainBinding
import com.keylesspalace.tusky.db.DraftsAlert
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.pager.MainPagerAdapter
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.DeveloperToolsUseCase
import com.keylesspalace.tusky.usecase.LogoutUsecase
import com.keylesspalace.tusky.util.ActivityConstants
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getDimension
import com.keylesspalace.tusky.util.getParcelableExtraCompat
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.overrideActivityTransitionCompat
import com.keylesspalace.tusky.util.reduceSwipeSensitivity
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.viewBinding
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
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.migration.OptionalInject
import de.c1710.filemojicompat_ui.helpers.EMOJI_PREFERENCE
import javax.inject.Inject
import kotlinx.coroutines.launch

@OptionalInject
@AndroidEntryPoint
class MainActivity : BottomSheetActivity(), ActionButtonActivity, MenuProvider {

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

    private val viewModel: MainViewModel by viewModels()

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private lateinit var activeAccount: AccountEntity

    private lateinit var header: AccountHeaderView

    private var onTabSelectedListener: OnTabSelectedListener? = null

    // We need to know if the emoji pack has been changed
    private var selectedEmojiPack: String? = null

    /** Mediate between binding.viewPager and the chosen tab layout */
    private var tabLayoutMediator: TabLayoutMediator? = null

    /** Adapter for the different timeline tabs */
    private lateinit var tabAdapter: MainPagerAdapter

    private var directMessageTab: TabLayout.Tab? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.viewPager.currentItem = 0
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Newer Android versions don't need to install the compat Splash Screen
        // and it can cause theming bugs.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            installSplashScreen()
        }
        super.onCreate(savedInstanceState)

        // make sure MainActivity doesn't hide other activities when launcher icon is clicked again
        if (!isTaskRoot &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            intent.action == Intent.ACTION_MAIN
        ) {
            finish()
            return
        }

        // will be redirected to LoginActivity by BaseActivity
        activeAccount = accountManager.activeAccount ?: return

        if (explodeAnimationWasRequested()) {
            overrideActivityTransitionCompat(
                ActivityConstants.OVERRIDE_TRANSITION_OPEN,
                R.anim.explode,
                R.anim.activity_open_exit
            )
        }

        selectedEmojiPack = preferences.getString(EMOJI_PREFERENCE, "")

        var showNotificationTab = false

        // check for savedInstanceState in order to not handle intent events more than once
        if (intent != null && savedInstanceState == null) {
            showNotificationTab = handleIntent(intent, activeAccount)
            if (isFinishing) {
                // handleIntent() finished this activity and started a new one - no need to continue initialization
                return
            }
        }

        window.statusBarColor = Color.TRANSPARENT // don't draw a status bar, the DrawerLayout and the MaterialDrawerLayout have their own
        setContentView(binding.root)

        binding.composeButton.setOnClickListener {
            val composeIntent = Intent(applicationContext, ComposeActivity::class.java)
            startActivity(composeIntent)
        }

        // Determine which of the three toolbars should be the supportActionBar (which hosts
        // the options menu).
        val hideTopToolbar = preferences.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        if (hideTopToolbar) {
            when (preferences.getString(PrefKeys.MAIN_NAV_POSITION, "top")) {
                "top" -> setSupportActionBar(binding.topNav)
                "bottom" -> setSupportActionBar(binding.bottomNav)
            }
            binding.mainToolbar.hide()
            // There's not enough space in the top/bottom bars to show the title as well.
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } else {
            setSupportActionBar(binding.mainToolbar)
            binding.mainToolbar.show()
        }

        addMenuProvider(this)

        binding.viewPager.reduceSwipeSensitivity()

        setupDrawer(
            savedInstanceState,
            addSearchButton = hideTopToolbar,
            addTrendingTagsButton = !activeAccount.tabPreferences.hasTab(
                TRENDING_TAGS
            ),
            addTrendingStatusesButton = !activeAccount.tabPreferences.hasTab(
                TRENDING_STATUSES
            )
        )

        lifecycleScope.launch {
            viewModel.accounts.collect(::updateProfiles)
        }

        lifecycleScope.launch {
            viewModel.unreadAnnouncementsCount.collect(::updateAnnouncementsBadge)
        }

        // Initialise the tab adapter and set to viewpager. Fragments appear to be leaked if the
        // adapter changes over the life of the viewPager (the adapter, not its contents), so set
        // the initial list of tabs to empty, and set the full list later in setupTabs(). See
        // https://github.com/tuskyapp/Tusky/issues/3251 for details.
        tabAdapter = MainPagerAdapter(emptyList(), this@MainActivity)
        binding.viewPager.adapter = tabAdapter

        lifecycleScope.launch {
            viewModel.tabs.collect(::setupTabs)
        }
        if (showNotificationTab) {
            val position = viewModel.tabs.value.indexOfFirst { it.id == NOTIFICATIONS }
            if (position != -1) {
                binding.viewPager.setCurrentItem(position, false)
            }
        }

        lifecycleScope.launch {
            viewModel.showDirectMessagesBadge.collect { showBadge ->
                updateDirectMessageBadge(showBadge)
            }
        }

        onBackPressedDispatcher.addCallback(this@MainActivity, onBackPressedCallback)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this@MainActivity, true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val showNotificationTab = handleIntent(intent, activeAccount)
        if (showNotificationTab) {
            val tabs = activeAccount.tabPreferences
            val position = tabs.indexOfFirst { it.id == NOTIFICATIONS }
            if (position != -1) {
                binding.viewPager.setCurrentItem(position, false)
            }
        }
    }

    override fun onDestroy() {
        cacheUpdater.stop()
        super.onDestroy()
    }

    /** Handle an incoming Intent,
     * @returns true when the intent is coming from an notification and the interface should show the notification tab.
     */
    private fun handleIntent(intent: Intent, activeAccount: AccountEntity): Boolean {
        val notificationId = intent.getIntExtra(NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            // opened from a notification action, cancel the notification
            val notificationManager = getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.cancel(intent.getStringExtra(NOTIFICATION_TAG), notificationId)
        }

        /** there are two possibilities the accountId can be passed to MainActivity:
         * - from our code as Long Intent Extra TUSKY_ACCOUNT_ID
         * - from share shortcuts as String 'android.intent.extra.shortcut.ID'
         */
        var tuskyAccountId = intent.getLongExtra(TUSKY_ACCOUNT_ID, -1)
        if (tuskyAccountId == -1L) {
            val accountIdString = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
            if (accountIdString != null) {
                tuskyAccountId = accountIdString.toLong()
            }
        }
        val accountRequested = tuskyAccountId != -1L
        if (accountRequested && tuskyAccountId != activeAccount.id) {
            changeAccount(tuskyAccountId, intent)
            return false
        }

        val openDrafts = intent.getBooleanExtra(OPEN_DRAFTS, false)

        if (canHandleMimeType(intent.type) || intent.hasExtra(COMPOSE_OPTIONS)) {
            // Sharing to Tusky from an external app
            if (accountRequested) {
                // The correct account is already active
                forwardToComposeActivity(intent)
            } else {
                // No account was provided, show the chooser
                showAccountChooserDialog(
                    getString(R.string.action_share_as),
                    true,
                    object : AccountSelectionListener {
                        override fun onAccountSelected(account: AccountEntity) {
                            val requestedId = account.id
                            if (requestedId == activeAccount.id) {
                                // The correct account is already active
                                forwardToComposeActivity(intent)
                            } else {
                                // A different account was requested, restart the activity
                                intent.putExtra(TUSKY_ACCOUNT_ID, requestedId)
                                changeAccount(requestedId, intent)
                            }
                        }
                    }
                )
            }
        } else if (openDrafts) {
            val draftsIntent = DraftsActivity.newIntent(this)
            startActivity(draftsIntent)
        } else if (accountRequested && intent.hasExtra(NOTIFICATION_TYPE)) {
            // user clicked a notification, show follow requests for type FOLLOW_REQUEST,
            // otherwise show notification tab
            if (intent.getStringExtra(NOTIFICATION_TYPE) == Notification.Type.FOLLOW_REQUEST.name) {
                val accountListIntent = AccountListActivity.newIntent(
                    this,
                    AccountListActivity.Type.FOLLOW_REQUESTS
                )
                startActivityWithSlideInAnimation(accountListIntent)
            } else {
                return true
            }
        }
        return false
    }

    private fun updateDirectMessageBadge(showBadge: Boolean) {
        directMessageTab?.badge?.isVisible = showBadge
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

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)

        // If the main toolbar is hidden then there's no space in the top/bottomNav to show
        // the menu items as icons, so forceably disable them
        if (!binding.mainToolbar.isVisible) {
            menu.forEach {
                it.setShowAsAction(
                    SHOW_AS_ACTION_NEVER
                )
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Allow software back press to be properly dispatched to drawer layout
        val handled = when (event.action) {
            KeyEvent.ACTION_DOWN -> binding.mainDrawerLayout.onKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> binding.mainDrawerLayout.onKeyUp(event.keyCode, event)
            else -> false
        }
        return handled || super.dispatchKeyEvent(event)
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

    private fun forwardToComposeActivity(intent: Intent) {
        val composeOptions =
            intent.getParcelableExtraCompat<ComposeActivity.ComposeOptions>(COMPOSE_OPTIONS)
        val composeIntent = if (composeOptions != null) {
            ComposeActivity.startIntent(this, composeOptions)
        } else {
            Intent(this, ComposeActivity::class.java).apply {
                action = intent.action
                type = intent.type
                putExtras(intent)
            }
        }
        startActivity(composeIntent)
    }

    private fun setupDrawer(
        savedInstanceState: Bundle?,
        addSearchButton: Boolean,
        addTrendingTagsButton: Boolean,
        addTrendingStatusesButton: Boolean
    ) {
        val drawerOpenClickListener = View.OnClickListener { binding.mainDrawerLayout.open() }

        binding.mainToolbar.setNavigationOnClickListener(drawerOpenClickListener)
        binding.topNav.setNavigationOnClickListener(drawerOpenClickListener)
        binding.bottomNav.setNavigationOnClickListener(drawerOpenClickListener)

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

        header.currentProfileName.maxLines = 1
        header.currentProfileName.ellipsize = TextUtils.TruncateAt.END

        header.accountHeaderBackground.setColorFilter(getColor(R.color.headerBackgroundFilter))
        header.accountHeaderBackground.setBackgroundColor(
            MaterialColors.getColor(header, R.attr.colorBackgroundAccent)
        )
        val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)

        DrawerImageLoader.init(object : AbstractDrawerImageLoader() {
            override fun set(imageView: ImageView, uri: Uri, placeholder: Drawable, tag: String?) {
                if (animateAvatars) {
                    Glide.with(imageView)
                        .load(uri)
                        .placeholder(placeholder)
                        .into(imageView)
                } else {
                    Glide.with(imageView)
                        .asBitmap()
                        .load(uri)
                        .placeholder(placeholder)
                        .into(imageView)
                }
            }

            override fun cancel(imageView: ImageView) {
                // nothing to do, Glide already handles cancellation automatically
            }

            override fun placeholder(ctx: Context, tag: String?): Drawable {
                if (tag == DrawerImageLoader.Tags.PROFILE.name || tag == DrawerImageLoader.Tags.PROFILE_DRAWER_ITEM.name) {
                    return AppCompatResources.getDrawable(ctx, R.drawable.avatar_default)!!
                }

                return super.placeholder(ctx, tag)
            }
        })

        binding.mainDrawer.apply {
            refreshMainDrawerItems(
                addSearchButton = addSearchButton,
                addTrendingTagsButton = addTrendingTagsButton,
                addTrendingStatusesButton = addTrendingStatusesButton
            )
            setSavedInstance(savedInstanceState)
        }
    }

    private fun refreshMainDrawerItems(
        addSearchButton: Boolean,
        addTrendingTagsButton: Boolean,
        addTrendingStatusesButton: Boolean
    ) {
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
                        val intent = AccountListActivity.newIntent(context, AccountListActivity.Type.FOLLOW_REQUESTS)
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
                        textColor = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, materialR.attr.colorOnPrimary))
                        color = ColorHolder.fromColor(MaterialColors.getColor(binding.mainDrawer, materialR.attr.colorPrimary))
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

            if (addTrendingTagsButton) {
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

            if (addTrendingStatusesButton) {
                binding.mainDrawer.addItemsAtPosition(
                    6,
                    primaryDrawerItem {
                        nameRes = R.string.title_public_trending_statuses
                        iconicsIcon = GoogleMaterial.Icon.gmd_local_fire_department
                        onClick = {
                            startActivityWithSlideInAnimation(StatusListActivity.newTrendingIntent(context))
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
                        showDeveloperToolsDialog()
                    }
                }
            )
        }
    }

    private fun showDeveloperToolsDialog(): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle("Developer Tools")
            .setItems(
                arrayOf("Create \"Load more\" gap")
            ) { _, which ->
                Log.d(TAG, "Developer tools: $which")
                when (which) {
                    0 -> {
                        Log.d(TAG, "Creating \"Load more\" gap")
                        lifecycleScope.launch {
                            developerToolsUseCase.createLoadMoreGap(
                                activeAccount.id
                            )
                        }
                    }
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(binding.mainDrawer.saveInstanceState(outState))
    }

    private fun setupTabs(tabs: List<TabData>) {
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

        // Detach any existing mediator before changing tab contents and attaching a new mediator
        tabLayoutMediator?.detach()

        directMessageTab = null

        tabAdapter.tabs = tabs
        tabAdapter.notifyItemRangeChanged(0, tabs.size)

        tabLayoutMediator = TabLayoutMediator(activeTabLayout, binding.viewPager, true) {
                tab: TabLayout.Tab, position: Int ->
            tab.icon = AppCompatResources.getDrawable(this@MainActivity, tabs[position].icon)
            tab.contentDescription = tabs[position].title(this)
            if (tabs[position].id == DIRECT) {
                val badge = tab.orCreateBadge
                badge.isVisible = activeAccount.hasDirectMessageBadge
                badge.backgroundColor = MaterialColors.getColor(binding.mainDrawer, materialR.attr.colorPrimary)
                directMessageTab = tab
            }
        }.also { it.attach() }
        updateDirectMessageBadge(viewModel.showDirectMessagesBadge.value)

        val position = previousTab?.let { tabs.indexOfFirst { it == previousTab } }
            .takeIf { it != -1 } ?: 0
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
                onBackPressedCallback.isEnabled = tab.position > 0

                binding.mainToolbar.title = tab.contentDescription

                if (tab == directMessageTab) {
                    viewModel.dismissDirectMessagesBadge()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                val fragment = tabAdapter.getFragment(tab.position)
                if (fragment is ReselectableFragment) {
                    fragment.onReselect()
                }
            }
        }.also {
            activeTabLayout.addOnTabSelectedListener(it)
        }

        supportActionBar?.title = tabs[position].title(this@MainActivity)
        binding.mainToolbar.setOnClickListener {
            (
                tabAdapter.getFragment(
                    activeTabLayout.selectedTabPosition
                ) as? ReselectableFragment
                )?.onReselect()
        }
    }

    private fun handleProfileClick(profile: IProfile, current: Boolean): Boolean {
        // open profile when active image was clicked
        if (current) {
            val intent = AccountActivity.getIntent(this, activeAccount.accountId)
            startActivityWithSlideInAnimation(intent)
            return false
        }
        // open LoginActivity to add new account
        if (profile.identifier == DRAWER_ITEM_ADD_ACCOUNT) {
            startActivityWithSlideInAnimation(
                LoginActivity.getIntent(this, LoginActivity.MODE_ADDITIONAL_LOGIN)
            )
            return false
        }
        // change Account
        changeAccount(profile.identifier, null)
        return false
    }

    private fun changeAccount(
        newSelectedId: Long,
        forward: Intent?,
    ) = lifecycleScope.launch {
        cacheUpdater.stop()
        accountManager.setActiveAccount(newSelectedId)
        val intent = Intent(this@MainActivity, MainActivity::class.java)
        if (forward != null) {
            intent.type = forward.type
            intent.action = forward.action
            intent.putExtras(forward)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun logout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_logout)
            .setMessage(getString(R.string.action_logout_confirm, activeAccount.fullName))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                binding.appBar.hide()
                binding.viewPager.hide()
                binding.progressBar.show()
                binding.bottomNav.hide()
                binding.composeButton.hide()

                lifecycleScope.launch {
                    val otherAccountAvailable = logoutUsecase.logout(activeAccount)
                    val intent = if (otherAccountAvailable) {
                        Intent(this@MainActivity, MainActivity::class.java)
                    } else {
                        LoginActivity.getIntent(this@MainActivity, LoginActivity.MODE_DEFAULT)
                    }
                    startActivity(intent)
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("CheckResult")
    private fun loadDrawerAvatar(avatarUrl: String, showPlaceholder: Boolean = true) {
        val hideTopToolbar = preferences.getBoolean(PrefKeys.HIDE_TOP_TOOLBAR, false)
        val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)

        val activeToolbar = if (hideTopToolbar) {
            val navOnBottom = preferences.getString(PrefKeys.MAIN_NAV_POSITION, "top") == "bottom"
            if (navOnBottom) {
                binding.bottomNav
            } else {
                binding.topNav
            }
        } else {
            binding.mainToolbar
        }

        val navIconSize = resources.getDimensionPixelSize(R.dimen.avatar_toolbar_nav_icon_size)

        if (animateAvatars) {
            Glide.with(this)
                .asDrawable()
                .load(avatarUrl)
                .transform(
                    RoundedCorners(resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp))
                )
                .apply {
                    if (showPlaceholder) placeholder(R.drawable.avatar_default)
                }
                .into(object : CustomTarget<Drawable>(navIconSize, navIconSize) {

                    override fun onLoadStarted(placeholder: Drawable?) {
                        placeholder?.let {
                            activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                        }
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        if (resource is Animatable) resource.start()
                        activeToolbar.navigationIcon = FixedSizeDrawable(resource, navIconSize, navIconSize)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        placeholder?.let {
                            activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                        }
                    }
                })
        } else {
            Glide.with(this)
                .asBitmap()
                .load(avatarUrl)
                .transform(
                    RoundedCorners(resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp))
                )
                .apply {
                    if (showPlaceholder) placeholder(R.drawable.avatar_default)
                }
                .into(object : CustomTarget<Bitmap>(navIconSize, navIconSize) {
                    override fun onLoadStarted(placeholder: Drawable?) {
                        placeholder?.let {
                            activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                        }
                    }

                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        activeToolbar.navigationIcon = FixedSizeDrawable(
                            BitmapDrawable(resources, resource),
                            navIconSize,
                            navIconSize
                        )
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        placeholder?.let {
                            activeToolbar.navigationIcon = FixedSizeDrawable(it, navIconSize, navIconSize)
                        }
                    }
                })
        }
    }

    private fun updateAnnouncementsBadge(unreadAnnouncementsCount: Int) {
        binding.mainDrawer.updateBadge(
            DRAWER_ITEM_ANNOUNCEMENTS,
            StringHolder(
                if (unreadAnnouncementsCount <= 0) null else unreadAnnouncementsCount.toString()
            )
        )
    }

    private fun updateProfiles(accounts: List<AccountViewData>) {
        if (accounts.isEmpty()) {
            return
        }
        val activeProfile = accounts.first()

        loadDrawerAvatar(activeProfile.profilePictureUrl)

        Glide.with(header.accountHeaderBackground)
            .asBitmap()
            .load(activeProfile.profileHeaderUrl)
            .into(header.accountHeaderBackground)

        val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        val profiles: MutableList<IProfile> =
            accounts.map { acc ->
                ProfileDrawerItem().apply {
                    isSelected = acc == activeProfile
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
        header.setActiveProfile(activeProfile.id)
        binding.mainToolbar.subtitle = if (accountManager.shouldDisplaySelfUsername()) {
            activeProfile.fullName
        } else {
            null
        }
    }

    private fun explodeAnimationWasRequested(): Boolean {
        return intent.getBooleanExtra(OPEN_WITH_EXPLODE_ANIMATION, false)
    }

    override fun getActionButton() = binding.composeButton

    companion object {
        const val OPEN_WITH_EXPLODE_ANIMATION = "explode"

        private const val TAG = "MainActivity" // logging tag
        private const val DRAWER_ITEM_ADD_ACCOUNT: Long = -13
        private const val DRAWER_ITEM_ANNOUNCEMENTS: Long = 14
        private const val REDIRECT_URL = "redirectUrl"
        private const val OPEN_DRAFTS = "draft"
        private const val TUSKY_ACCOUNT_ID = "tuskyAccountId"
        private const val COMPOSE_OPTIONS = "composeOptions"
        private const val NOTIFICATION_TYPE = "notificationType"
        private const val NOTIFICATION_TAG = "notificationTag"
        private const val NOTIFICATION_ID = "notificationId"

        /**
         * Switches the active account to the provided accountId and then stays on MainActivity
         */
        @JvmStatic
        fun accountSwitchIntent(context: Context, tuskyAccountId: Long): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(TUSKY_ACCOUNT_ID, tuskyAccountId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        /**
         * Switches the active account to the accountId and takes the user to the correct place according to the notification they clicked
         */
        @JvmStatic
        fun openNotificationIntent(
            context: Context,
            tuskyAccountId: Long,
            type: Notification.Type
        ): Intent {
            return accountSwitchIntent(context, tuskyAccountId).apply {
                putExtra(NOTIFICATION_TYPE, type.name)
            }
        }

        /**
         * Switches the active account to the accountId and then opens ComposeActivity with the provided options
         * @param tuskyAccountId the id of the Tusky account to open the screen with. Set to -1 for current account.
         * @param notificationId optional id of the notification that should be cancelled when this intent is opened
         * @param notificationTag optional tag of the notification that should be cancelled when this intent is opened
         */
        @JvmStatic
        fun composeIntent(
            context: Context,
            options: ComposeActivity.ComposeOptions,
            tuskyAccountId: Long = -1,
            notificationTag: String? = null,
            notificationId: Int = -1
        ): Intent {
            return accountSwitchIntent(context, tuskyAccountId).apply {
                action = Intent.ACTION_SEND // so it can be opened via shortcuts
                putExtra(COMPOSE_OPTIONS, options)
                putExtra(NOTIFICATION_TAG, notificationTag)
                putExtra(NOTIFICATION_ID, notificationId)
            }
        }

        /**
         * switches the active account to the accountId and then tries to resolve and show the provided url
         */
        @JvmStatic
        fun redirectIntent(context: Context, tuskyAccountId: Long, url: String): Intent {
            return accountSwitchIntent(context, tuskyAccountId).apply {
                putExtra(REDIRECT_URL, url)
            }
        }

        /**
         * switches the active account to the provided accountId and then opens drafts
         */
        fun draftIntent(context: Context, tuskyAccountId: Long): Intent {
            return accountSwitchIntent(context, tuskyAccountId).apply {
                putExtra(OPEN_DRAFTS, true)
            }
        }
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
