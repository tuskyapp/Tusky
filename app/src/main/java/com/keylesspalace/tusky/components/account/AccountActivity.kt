/* Copyright 2018 Conny Duck
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

package com.keylesspalace.tusky.components.account

import android.animation.ArgbEvaluator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.Editable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.MarginPageTransformer
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.EditProfileActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.account.list.ListsForAccountFragment
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.report.ReportActivity
import com.keylesspalace.tusky.databinding.ActivityAccountBinding
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.DraftsAlert
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.DefaultTextWatcher
import com.keylesspalace.tusky.util.Error
import com.keylesspalace.tusky.util.Loading
import com.keylesspalace.tusky.util.Success
import com.keylesspalace.tusky.util.emojify
import com.keylesspalace.tusky.util.getDomain
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.loadAvatar
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.reduceSwipeSensitivity
import com.keylesspalace.tusky.util.setClickableText
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.view.showMuteAccountDialog
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class AccountActivity : BottomSheetActivity(), ActionButtonActivity, MenuProvider, HasAndroidInjector, LinkListener {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>
    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    @Inject
    lateinit var draftsAlert: DraftsAlert

    private val viewModel: AccountViewModel by viewModels { viewModelFactory }

    private val binding: ActivityAccountBinding by viewBinding(ActivityAccountBinding::inflate)

    private lateinit var accountFieldAdapter: AccountFieldAdapter

    private val preferences by unsafeLazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private var followState: FollowState = FollowState.NOT_FOLLOWING
    private var blocking: Boolean = false
    private var muting: Boolean = false
    private var blockingDomain: Boolean = false
    private var showingReblogs: Boolean = false
    private var subscribing: Boolean = false
    private var loadedAccount: Account? = null

    private var animateAvatar: Boolean = false
    private var animateEmojis: Boolean = false

    // fields for scroll animation
    private var hideFab: Boolean = false
    private var oldOffset: Int = 0
    @ColorInt
    private var toolbarColor: Int = 0
    @ColorInt
    private var statusBarColorTransparent: Int = 0
    @ColorInt
    private var statusBarColorOpaque: Int = 0

    private var avatarSize: Float = 0f
    @Px
    private var titleVisibleHeight: Int = 0
    private lateinit var domain: String

    private enum class FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED
    }

    private lateinit var adapter: AccountPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadResources()
        makeNotificationBarTransparent()
        setContentView(binding.root)
        addMenuProvider(this)

        // Obtain information to fill out the profile.
        viewModel.setAccountInfo(intent.getStringExtra(KEY_ACCOUNT_ID)!!)

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        animateAvatar = sharedPrefs.getBoolean("animateGifAvatars", false)
        animateEmojis = sharedPrefs.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
        hideFab = sharedPrefs.getBoolean("fabHide", false)

        handleWindowInsets()
        setupToolbar()
        setupTabs()
        setupAccountViews()
        setupRefreshLayout()
        subscribeObservables()

        if (viewModel.isSelf) {
            updateButtons()
            binding.saveNoteInfo.hide()
        } else {
            binding.saveNoteInfo.visibility = View.INVISIBLE
        }
    }

    /**
     * Load colors and dimensions from resources
     */
    private fun loadResources() {
        toolbarColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.BLACK)
        statusBarColorTransparent = getColor(R.color.transparent_statusbar_background)
        statusBarColorOpaque = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimaryDark, Color.BLACK)
        avatarSize = resources.getDimension(R.dimen.account_activity_avatar_size)
        titleVisibleHeight = resources.getDimensionPixelSize(R.dimen.account_activity_scroll_title_visible_height)
    }

    /**
     * Setup account widgets visibility and actions
     */
    private fun setupAccountViews() {
        // Initialise the default UI states.
        binding.accountFloatingActionButton.hide()
        binding.accountFollowButton.hide()
        binding.accountMuteButton.hide()
        binding.accountFollowsYouTextView.hide()

        // setup the RecyclerView for the account fields
        accountFieldAdapter = AccountFieldAdapter(this, animateEmojis)
        binding.accountFieldList.isNestedScrollingEnabled = false
        binding.accountFieldList.layoutManager = LinearLayoutManager(this)
        binding.accountFieldList.adapter = accountFieldAdapter

        val accountListClickListener = { v: View ->
            val type = when (v.id) {
                R.id.accountFollowers -> AccountListActivity.Type.FOLLOWERS
                R.id.accountFollowing -> AccountListActivity.Type.FOLLOWS
                else -> throw AssertionError()
            }
            val accountListIntent = AccountListActivity.newIntent(this, type, viewModel.accountId)
            startActivityWithSlideInAnimation(accountListIntent)
        }
        binding.accountFollowers.setOnClickListener(accountListClickListener)
        binding.accountFollowing.setOnClickListener(accountListClickListener)

        binding.accountStatuses.setOnClickListener {
            // Make nice ripple effect on tab
            binding.accountTabLayout.getTabAt(0)!!.select()
            val poorTabView = (binding.accountTabLayout.getChildAt(0) as ViewGroup).getChildAt(0)
            poorTabView.isPressed = true
            binding.accountTabLayout.postDelayed({ poorTabView.isPressed = false }, 300)
        }

        // If wellbeing mode is enabled, follow stats and posts count should be hidden
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val wellbeingEnabled = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_PROFILE, false)

        if (wellbeingEnabled) {
            binding.accountStatuses.hide()
            binding.accountFollowers.hide()
            binding.accountFollowing.hide()
        }
    }

    /**
     * Init timeline tabs
     */
    private fun setupTabs() {
        // Setup the tabs and timeline pager.
        adapter = AccountPagerAdapter(this, viewModel.accountId)

        binding.accountFragmentViewPager.reduceSwipeSensitivity()
        binding.accountFragmentViewPager.adapter = adapter
        binding.accountFragmentViewPager.offscreenPageLimit = 2

        val pageTitles = arrayOf(getString(R.string.title_posts), getString(R.string.title_posts_with_replies), getString(R.string.title_posts_pinned), getString(R.string.title_media))

        TabLayoutMediator(binding.accountTabLayout, binding.accountFragmentViewPager) { tab, position ->
            tab.text = pageTitles[position]
        }.attach()

        val pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        binding.accountFragmentViewPager.setPageTransformer(MarginPageTransformer(pageMargin))

        val enableSwipeForTabs = preferences.getBoolean(PrefKeys.ENABLE_SWIPE_FOR_TABS, true)
        binding.accountFragmentViewPager.isUserInputEnabled = enableSwipeForTabs

        binding.accountTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    (adapter.getFragment(position) as? ReselectableFragment)?.onReselect()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab?) {}
        })
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.accountCoordinatorLayout) { _, insets ->
            val top = insets.getInsets(systemBars()).top
            val toolbarParams = binding.accountToolbar.layoutParams as ViewGroup.MarginLayoutParams
            toolbarParams.topMargin = top

            val right = insets.getInsets(systemBars()).right
            val bottom = insets.getInsets(systemBars()).bottom
            val left = insets.getInsets(systemBars()).left
            binding.accountCoordinatorLayout.updatePadding(right = right, bottom = bottom, left = left)

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        // Setup the toolbar.
        setSupportActionBar(binding.accountToolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        val appBarElevation = resources.getDimension(R.dimen.actionbar_elevation)

        val toolbarBackground = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)
        toolbarBackground.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
        binding.accountToolbar.background = toolbarBackground

        // Provide a non-transparent background to the navigation and overflow icons to ensure
        // they remain visible over whatever the profile background image might be.
        val backgroundCircle = AppCompatResources.getDrawable(this, R.drawable.background_circle)!!
        backgroundCircle.alpha = 210 // Any lower than this and the backgrounds interfere
        binding.accountToolbar.navigationIcon = LayerDrawable(
            arrayOf(
                backgroundCircle,
                binding.accountToolbar.navigationIcon
            )
        )
        binding.accountToolbar.overflowIcon = LayerDrawable(
            arrayOf(
                backgroundCircle,
                binding.accountToolbar.overflowIcon
            )
        )

        binding.accountHeaderInfoContainer.background = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)

        val avatarBackground = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation).apply {
            fillColor = ColorStateList.valueOf(toolbarColor)
            elevation = appBarElevation
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(resources.getDimension(R.dimen.account_avatar_background_radius))
                .build()
        }
        binding.accountAvatarImageView.background = avatarBackground

        // Add a listener to change the toolbar icon color when it enters/exits its collapsed state.
        binding.accountAppBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {

            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {

                if (verticalOffset == oldOffset) {
                    return
                }
                oldOffset = verticalOffset

                if (titleVisibleHeight + verticalOffset < 0) {
                    supportActionBar?.setDisplayShowTitleEnabled(true)
                } else {
                    supportActionBar?.setDisplayShowTitleEnabled(false)
                }

                if (hideFab && !blocking) {
                    if (verticalOffset > oldOffset) {
                        binding.accountFloatingActionButton.show()
                    }
                    if (verticalOffset < oldOffset) {
                        binding.accountFloatingActionButton.hide()
                    }
                }

                val scaledAvatarSize = (avatarSize + verticalOffset) / avatarSize

                binding.accountAvatarImageView.scaleX = scaledAvatarSize
                binding.accountAvatarImageView.scaleY = scaledAvatarSize

                binding.accountAvatarImageView.visible(scaledAvatarSize > 0)

                val transparencyPercent = (abs(verticalOffset) / titleVisibleHeight.toFloat()).coerceAtMost(1f)

                window.statusBarColor = argbEvaluator.evaluate(transparencyPercent, statusBarColorTransparent, statusBarColorOpaque) as Int

                val evaluatedToolbarColor = argbEvaluator.evaluate(transparencyPercent, Color.TRANSPARENT, toolbarColor) as Int

                toolbarBackground.fillColor = ColorStateList.valueOf(evaluatedToolbarColor)

                binding.swipeToRefreshLayout.isEnabled = verticalOffset == 0
            }
        })
    }

    private fun makeNotificationBarTransparent() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = statusBarColorTransparent
    }

    /**
     * Subscribe to data loaded at the view model
     */
    private fun subscribeObservables() {
        viewModel.accountData.observe(this) {
            when (it) {
                is Success -> onAccountChanged(it.data)
                is Error -> {
                    Snackbar.make(binding.accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry) { viewModel.refresh() }
                        .show()
                }
                is Loading -> { }
            }
        }
        viewModel.relationshipData.observe(this) {
            val relation = it?.data
            if (relation != null) {
                onRelationshipChanged(relation)
            }

            if (it is Error) {
                Snackbar.make(binding.accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_retry) { viewModel.refresh() }
                    .show()
            }
        }
        viewModel.noteSaved.observe(this) {
            binding.saveNoteInfo.visible(it, View.INVISIBLE)
        }

        // "Post failed" dialog should display in this activity
        draftsAlert.observeInContext(this, true)
    }

    private fun onRefresh() {
        viewModel.refresh()
        adapter.refreshContent()
    }

    /**
     * Setup swipe to refresh layout
     */
    private fun setupRefreshLayout() {
        binding.swipeToRefreshLayout.setOnRefreshListener { onRefresh() }
        viewModel.isRefreshing.observe(
            this
        ) { isRefreshing ->
            binding.swipeToRefreshLayout.isRefreshing = isRefreshing == true
        }
        binding.swipeToRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
    }

    private fun onAccountChanged(account: Account?) {
        loadedAccount = account ?: return

        val usernameFormatted = getString(R.string.post_username_format, account.username)
        binding.accountUsernameTextView.text = usernameFormatted
        binding.accountDisplayNameTextView.text = account.name.emojify(account.emojis, binding.accountDisplayNameTextView, animateEmojis)

        // Long press on username to copy it to clipboard
        for (view in listOf(binding.accountUsernameTextView, binding.accountDisplayNameTextView)) {
            view.setOnLongClickListener {
                loadedAccount?.let { loadedAccount ->
                    val fullUsername = getFullUsername(loadedAccount)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, fullUsername))
                    Snackbar.make(binding.root, getString(R.string.account_username_copied), Snackbar.LENGTH_SHORT)
                        .show()
                }
                true
            }
        }

        val emojifiedNote = account.note.parseAsMastodonHtml().emojify(account.emojis, binding.accountNoteTextView, animateEmojis)
        setClickableText(binding.accountNoteTextView, emojifiedNote, emptyList(), null, this)

        accountFieldAdapter.fields = account.fields.orEmpty()
        accountFieldAdapter.emojis = account.emojis.orEmpty()
        accountFieldAdapter.notifyDataSetChanged()

        binding.accountLockedImageView.visible(account.locked)
        binding.accountBadgeTextView.visible(account.bot)

        updateAccountAvatar()
        updateToolbar()
        updateMovedAccount()
        updateRemoteAccount()
        updateAccountJoinedDate()
        updateAccountStats()
        invalidateOptionsMenu()

        binding.accountMuteButton.setOnClickListener {
            viewModel.unmuteAccount()
            updateMuteButton()
        }
    }

    private fun updateAccountJoinedDate() {
        loadedAccount?.let { account ->
            try {
                binding.accountDateJoined.text = resources.getString(
                    R.string.account_date_joined,
                    SimpleDateFormat("MMMM, yyyy", Locale.getDefault()).format(account.createdAt)
                )
                binding.accountDateJoined.visibility = View.VISIBLE
            } catch (e: ParseException) {
                binding.accountDateJoined.visibility = View.GONE
            }
        }
    }

    /**
     * Load account's avatar and header image
     */
    private fun updateAccountAvatar() {
        loadedAccount?.let { account ->

            loadAvatar(
                account.avatar,
                binding.accountAvatarImageView,
                resources.getDimensionPixelSize(R.dimen.avatar_radius_94dp),
                animateAvatar
            )

            Glide.with(this)
                .asBitmap()
                .load(account.header)
                .centerCrop()
                .into(binding.accountHeaderImageView)

            binding.accountAvatarImageView.setOnClickListener { view ->
                viewImage(view, account.avatar)
            }
            binding.accountHeaderImageView.setOnClickListener { view ->
                viewImage(view, account.header)
            }
        }
    }

    private fun viewImage(view: View, uri: String) {
        view.transitionName = uri
        startActivity(
            ViewMediaActivity.newSingleImageIntent(view.context, uri),
            ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, uri).toBundle()
        )
    }

    /**
     * Update toolbar views for loaded account
     */
    private fun updateToolbar() {
        loadedAccount?.let { account ->
            supportActionBar?.title = account.name.emojify(account.emojis, binding.accountToolbar, animateEmojis)
            supportActionBar?.subtitle = String.format(getString(R.string.post_username_format), account.username)
        }
    }

    /**
     * Update moved account info
     */
    private fun updateMovedAccount() {
        loadedAccount?.moved?.let { movedAccount ->

            binding.accountMovedView.show()

            binding.accountMovedView.setOnClickListener {
                onViewAccount(movedAccount.id)
            }

            binding.accountMovedDisplayName.text = movedAccount.name
            binding.accountMovedUsername.text = getString(R.string.post_username_format, movedAccount.username)

            val avatarRadius = resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)

            loadAvatar(movedAccount.avatar, binding.accountMovedAvatar, avatarRadius, animateAvatar)

            binding.accountMovedText.text = getString(R.string.account_moved_description, movedAccount.name)
        }
    }

    /**
     * Check is account remote and update info if so
     */
    private fun updateRemoteAccount() {
        loadedAccount?.let { account ->
            if (account.isRemote()) {
                binding.accountRemoveView.show()
                binding.accountRemoveView.setOnClickListener {
                    openLink(account.url)
                }
            }
        }
    }

    /**
     * Update account stat info
     */
    private fun updateAccountStats() {
        loadedAccount?.let { account ->
            val numberFormat = NumberFormat.getNumberInstance()
            binding.accountFollowersTextView.text = numberFormat.format(account.followersCount)
            binding.accountFollowingTextView.text = numberFormat.format(account.followingCount)
            binding.accountStatusesTextView.text = numberFormat.format(account.statusesCount)

            binding.accountFloatingActionButton.setOnClickListener { mention() }

            binding.accountFollowButton.setOnClickListener {
                if (viewModel.isSelf) {
                    val intent = Intent(this@AccountActivity, EditProfileActivity::class.java)
                    startActivity(intent)
                    return@setOnClickListener
                }

                if (blocking) {
                    viewModel.changeBlockState()
                    return@setOnClickListener
                }

                when (followState) {
                    FollowState.NOT_FOLLOWING -> {
                        viewModel.changeFollowState()
                    }
                    FollowState.REQUESTED -> {
                        showFollowRequestPendingDialog()
                    }
                    FollowState.FOLLOWING -> {
                        showUnfollowWarningDialog()
                    }
                }
                updateFollowButton()
                updateSubscribeButton()
            }
        }
    }

    private fun onRelationshipChanged(relation: Relationship) {
        followState = when {
            relation.following -> FollowState.FOLLOWING
            relation.requested -> FollowState.REQUESTED
            else -> FollowState.NOT_FOLLOWING
        }
        blocking = relation.blocking
        muting = relation.muting
        blockingDomain = relation.blockingDomain
        showingReblogs = relation.showingReblogs

        // If wellbeing mode is enabled, "follows you" text should not be visible
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val wellbeingEnabled = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_PROFILE, false)

        binding.accountFollowsYouTextView.visible(relation.followedBy && !wellbeingEnabled)

        // because subscribing is Pleroma extension, enable it __only__ when we have non-null subscribing field
        // it's also now supported in Mastodon 3.3.0rc but called notifying and use different API call
        if (!viewModel.isSelf && followState == FollowState.FOLLOWING &&
            (relation.subscribing != null || relation.notifying != null)
        ) {
            binding.accountSubscribeButton.show()
            binding.accountSubscribeButton.setOnClickListener {
                viewModel.changeSubscribingState()
            }
            if (relation.notifying != null)
                subscribing = relation.notifying
            else if (relation.subscribing != null)
                subscribing = relation.subscribing
        }

        // remove the listener so it doesn't fire on non-user changes
        binding.accountNoteTextInputLayout.editText?.removeTextChangedListener(noteWatcher)

        binding.accountNoteTextInputLayout.visible(relation.note != null)
        binding.accountNoteTextInputLayout.editText?.setText(relation.note)

        binding.accountNoteTextInputLayout.editText?.addTextChangedListener(noteWatcher)

        updateButtons()
    }

    private val noteWatcher = object : DefaultTextWatcher() {
        override fun afterTextChanged(s: Editable) {
            viewModel.noteChanged(s.toString())
        }
    }

    private fun updateFollowButton() {
        if (viewModel.isSelf) {
            binding.accountFollowButton.setText(R.string.action_edit_own_profile)
            return
        }
        if (blocking) {
            binding.accountFollowButton.setText(R.string.action_unblock)
            return
        }
        when (followState) {
            FollowState.NOT_FOLLOWING -> {
                binding.accountFollowButton.setText(R.string.action_follow)
            }
            FollowState.REQUESTED -> {
                binding.accountFollowButton.setText(R.string.state_follow_requested)
            }
            FollowState.FOLLOWING -> {
                binding.accountFollowButton.setText(R.string.action_unfollow)
            }
        }
    }

    private fun updateMuteButton() {
        if (muting) {
            binding.accountMuteButton.setIconResource(R.drawable.ic_unmute_24dp)
        } else {
            binding.accountMuteButton.hide()
        }
    }

    private fun updateSubscribeButton() {
        if (followState != FollowState.FOLLOWING) {
            binding.accountSubscribeButton.hide()
        }

        if (subscribing) {
            binding.accountSubscribeButton.setIconResource(R.drawable.ic_notifications_active_24dp)
            binding.accountSubscribeButton.contentDescription = getString(R.string.action_unsubscribe_account)
        } else {
            binding.accountSubscribeButton.setIconResource(R.drawable.ic_notifications_24dp)
            binding.accountSubscribeButton.contentDescription = getString(R.string.action_subscribe_account)
        }
    }

    private fun updateButtons() {
        invalidateOptionsMenu()

        if (loadedAccount?.moved == null) {

            binding.accountFollowButton.show()
            updateFollowButton()
            updateSubscribeButton()

            if (blocking) {
                binding.accountFloatingActionButton.hide()
                binding.accountMuteButton.hide()
            } else {
                binding.accountFloatingActionButton.show()
                binding.accountMuteButton.visible(muting)
                updateMuteButton()
            }
        } else {
            binding.accountFloatingActionButton.hide()
            binding.accountFollowButton.hide()
            binding.accountMuteButton.hide()
            binding.accountSubscribeButton.hide()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.account_toolbar, menu)

        val openAsItem = menu.findItem(R.id.action_open_as)
        val title = openAsText
        if (title == null) {
            openAsItem.isVisible = false
        } else {
            openAsItem.title = title
        }

        if (!viewModel.isSelf) {

            val block = menu.findItem(R.id.action_block)
            block.title = if (blocking) {
                getString(R.string.action_unblock)
            } else {
                getString(R.string.action_block)
            }

            val mute = menu.findItem(R.id.action_mute)
            mute.title = if (muting) {
                getString(R.string.action_unmute)
            } else {
                getString(R.string.action_mute)
            }

            loadedAccount?.let { loadedAccount ->
                val muteDomain = menu.findItem(R.id.action_mute_domain)
                domain = getDomain(loadedAccount.url)
                if (domain.isEmpty()) {
                    // If we can't get the domain, there's no way we can mute it anyway...
                    menu.removeItem(R.id.action_mute_domain)
                } else {
                    if (blockingDomain) {
                        muteDomain.title = getString(R.string.action_unmute_domain, domain)
                    } else {
                        muteDomain.title = getString(R.string.action_mute_domain, domain)
                    }
                }
            }

            if (followState == FollowState.FOLLOWING) {
                val showReblogs = menu.findItem(R.id.action_show_reblogs)
                showReblogs.title = if (showingReblogs) {
                    getString(R.string.action_hide_reblogs)
                } else {
                    getString(R.string.action_show_reblogs)
                }
            } else {
                menu.removeItem(R.id.action_show_reblogs)
            }
        } else {
            // It shouldn't be possible to block, mute or report yourself.
            menu.removeItem(R.id.action_block)
            menu.removeItem(R.id.action_mute)
            menu.removeItem(R.id.action_mute_domain)
            menu.removeItem(R.id.action_show_reblogs)
            menu.removeItem(R.id.action_report)
        }

        if (!viewModel.isSelf && followState != FollowState.FOLLOWING) {
            menu.removeItem(R.id.action_add_or_remove_from_list)
        }

        menu.findItem(R.id.action_search)?.apply {
            icon = IconicsDrawable(this@AccountActivity, GoogleMaterial.Icon.gmd_search).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.collapsingToolbar, android.R.attr.textColorPrimary)
            }
        }
    }

    private fun showFollowRequestPendingDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_message_cancel_follow_request)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUnfollowWarningDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_unfollow_warning)
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleBlockDomain(instance: String) {
        if (blockingDomain) {
            viewModel.unblockDomain(instance)
        } else {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.mute_domain_warning, instance))
                .setPositiveButton(getString(R.string.mute_domain_warning_dialog_ok)) { _, _ -> viewModel.blockDomain(instance) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun toggleBlock() {
        if (viewModel.relationshipData.value?.data?.blocking != true) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.dialog_block_warning, loadedAccount?.username))
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeBlockState() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            viewModel.changeBlockState()
        }
    }

    private fun toggleMute() {
        if (viewModel.relationshipData.value?.data?.muting != true) {
            loadedAccount?.let {
                showMuteAccountDialog(
                    this,
                    it.username
                ) { notifications, duration ->
                    viewModel.muteAccount(notifications, duration)
                }
            }
        } else {
            viewModel.unmuteAccount()
        }
    }

    private fun mention() {
        loadedAccount?.let {
            val options = if (viewModel.isSelf) {
                ComposeActivity.ComposeOptions(kind = ComposeActivity.ComposeKind.NEW)
            } else {
                ComposeActivity.ComposeOptions(
                    mentionedUsernames = setOf(it.username),
                    kind = ComposeActivity.ComposeKind.NEW
                )
            }
            val intent = ComposeActivity.startIntent(this, options)
            startActivity(intent)
        }
    }

    override fun onViewTag(tag: String) {
        val intent = StatusListActivity.newHashtagIntent(this, tag)
        startActivityWithSlideInAnimation(intent)
    }

    override fun onViewAccount(id: String) {
        val intent = Intent(this, AccountActivity::class.java)
        intent.putExtra("id", id)
        startActivityWithSlideInAnimation(intent)
    }

    override fun onViewUrl(url: String) {
        viewUrl(url)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_in_web -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    openLink(loadedAccount.url)
                }
                return true
            }
            R.id.action_open_as -> {
                loadedAccount?.let { loadedAccount ->
                    showAccountChooserDialog(
                        item.title, false,
                        object : AccountSelectionListener {
                            override fun onAccountSelected(account: AccountEntity) {
                                openAsAccount(loadedAccount.url, account)
                            }
                        }
                    )
                }
            }
            R.id.action_share_account_link -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    val url = loadedAccount.url
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, url)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_account_link_to)))
                }
                return true
            }
            R.id.action_share_account_username -> {
                // If the account isn't loaded yet, eat the input.
                loadedAccount?.let { loadedAccount ->
                    val fullUsername = getFullUsername(loadedAccount)
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, fullUsername)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_account_username_to)))
                }
                return true
            }
            R.id.action_block -> {
                toggleBlock()
                return true
            }
            R.id.action_mute -> {
                toggleMute()
                return true
            }
            R.id.action_add_or_remove_from_list -> {
                ListsForAccountFragment.newInstance(viewModel.accountId).show(supportFragmentManager, null)
                return true
            }
            R.id.action_mute_domain -> {
                toggleBlockDomain(domain)
                return true
            }
            R.id.action_show_reblogs -> {
                viewModel.changeShowReblogsState()
                return true
            }
            R.id.action_refresh -> {
                binding.swipeToRefreshLayout.isRefreshing = true
                onRefresh()
                return true
            }
            R.id.action_report -> {
                loadedAccount?.let { loadedAccount ->
                    startActivity(ReportActivity.getIntent(this, viewModel.accountId, loadedAccount.username))
                }
                return true
            }
        }
        return false
    }

    override fun getActionButton(): FloatingActionButton? {
        return if (!blocking) {
            binding.accountFloatingActionButton
        } else null
    }

    private fun getFullUsername(account: Account): String {
        return if (account.isRemote()) {
            "@" + account.username
        } else {
            val localUsername = account.localUsername
            // Note: !! here will crash if this pane is ever shown to a logged-out user. With AccountActivity this is believed to be impossible.
            val domain = accountManager.activeAccount!!.domain
            "@$localUsername@$domain"
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {

        private const val KEY_ACCOUNT_ID = "id"
        private val argbEvaluator = ArgbEvaluator()

        @JvmStatic
        fun getIntent(context: Context, accountId: String): Intent {
            val intent = Intent(context, AccountActivity::class.java)
            intent.putExtra(KEY_ACCOUNT_ID, accountId)
            return intent
        }
    }
}
