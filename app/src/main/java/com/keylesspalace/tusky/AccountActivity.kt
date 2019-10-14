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

package com.keylesspalace.tusky

import android.animation.ArgbEvaluator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.emoji.text.EmojiCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.keylesspalace.tusky.adapter.AccountFieldAdapter
import com.keylesspalace.tusky.components.report.ReportActivity
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.interfaces.ReselectableFragment
import com.keylesspalace.tusky.pager.AccountPagerAdapter
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.viewmodel.AccountViewModel
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.view_account_moved.*
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.abs

class AccountActivity : BottomSheetActivity(), ActionButtonActivity, HasAndroidInjector, LinkListener {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: AccountViewModel

    private val accountFieldAdapter = AccountFieldAdapter(this)

    private var followState: FollowState = FollowState.NOT_FOLLOWING
    private var blocking: Boolean = false
    private var muting: Boolean = false
    private var showingReblogs: Boolean = false
    private var loadedAccount: Account? = null

    private var animateAvatar: Boolean = false

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
        setContentView(R.layout.activity_account)

        viewModel = ViewModelProviders.of(this, viewModelFactory)[AccountViewModel::class.java]

        // Obtain information to fill out the profile.
        viewModel.setAccountInfo(intent.getStringExtra(KEY_ACCOUNT_ID))

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        animateAvatar = sharedPrefs.getBoolean("animateGifAvatars", false)
        hideFab = sharedPrefs.getBoolean("fabHide", false)

        setupToolbar()
        setupTabs()
        setupAccountViews()
        setupRefreshLayout()
        subscribeObservables()

        if (viewModel.isSelf) {
            updateButtons()
        }
    }

    /**
     * Load colors and dimensions from resources
     */
    private fun loadResources() {
        toolbarColor = ThemeUtils.getColor(this, R.attr.colorSurface)
        statusBarColorTransparent = ContextCompat.getColor(this, R.color.header_background_filter)
        statusBarColorOpaque = ThemeUtils.getColor(this, R.attr.colorPrimaryDark)
        avatarSize = resources.getDimension(R.dimen.account_activity_avatar_size)
        titleVisibleHeight = resources.getDimensionPixelSize(R.dimen.account_activity_scroll_title_visible_height)
    }

    /**
     * Setup account widgets visibility and actions
     */
    private fun setupAccountViews() {
        // Initialise the default UI states.
        accountFloatingActionButton.hide()
        accountFollowButton.hide()
        accountMuteButton.hide()
        accountFollowsYouTextView.hide()


        // setup the RecyclerView for the account fields
        accountFieldList.isNestedScrollingEnabled = false
        accountFieldList.layoutManager = LinearLayoutManager(this)
        accountFieldList.adapter = accountFieldAdapter


        val accountListClickListener = { v: View ->
            val type = when (v.id) {
                R.id.accountFollowers -> AccountListActivity.Type.FOLLOWERS
                R.id.accountFollowing -> AccountListActivity.Type.FOLLOWS
                else -> throw AssertionError()
            }
            val accountListIntent = AccountListActivity.newIntent(this, type, viewModel.accountId)
            startActivityWithSlideInAnimation(accountListIntent)
        }
        accountFollowers.setOnClickListener(accountListClickListener)
        accountFollowing.setOnClickListener(accountListClickListener)

        accountStatuses.setOnClickListener {
            // Make nice ripple effect on tab
            accountTabLayout.getTabAt(0)!!.select()
            val poorTabView = (accountTabLayout.getChildAt(0) as ViewGroup).getChildAt(0)
            poorTabView.isPressed = true
            accountTabLayout.postDelayed({ poorTabView.isPressed = false }, 300)
        }

    }

    /**
     * Init timeline tabs
     */
    private fun setupTabs() {
        // Setup the tabs and timeline pager.
        adapter = AccountPagerAdapter(supportFragmentManager, viewModel.accountId)
        val pageTitles = arrayOf(getString(R.string.title_statuses), getString(R.string.title_statuses_with_replies), getString(R.string.title_statuses_pinned), getString(R.string.title_media))
        adapter.setPageTitles(pageTitles)
        accountFragmentViewPager.pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        val pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark)
        accountFragmentViewPager.setPageMarginDrawable(pageMarginDrawable)
        accountFragmentViewPager.adapter = adapter
        accountFragmentViewPager.offscreenPageLimit = 2
        accountTabLayout.setupWithViewPager(accountFragmentViewPager)
        accountTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    (adapter.getFragment(position) as? ReselectableFragment)?.onReselect()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab?) {}

        })
    }

    private fun setupToolbar() {
        // set toolbar top margin according to system window insets
        accountCoordinatorLayout.setOnApplyWindowInsetsListener { _, insets ->
            val top = insets.systemWindowInsetTop

            val toolbarParams = accountToolbar.layoutParams as CollapsingToolbarLayout.LayoutParams
            toolbarParams.topMargin = top

            insets.consumeSystemWindowInsets()
        }

        // Setup the toolbar.
        setSupportActionBar(accountToolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        ThemeUtils.setDrawableTint(this, accountToolbar.navigationIcon, R.attr.account_toolbar_icon_tint_uncollapsed)
        ThemeUtils.setDrawableTint(this, accountToolbar.overflowIcon, R.attr.account_toolbar_icon_tint_uncollapsed)

        val appBarElevation = resources.getDimension(R.dimen.actionbar_elevation)

        val toolbarBackground = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)
        toolbarBackground.fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
        accountToolbar.background = toolbarBackground

        accountHeaderInfoContainer.background = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation)

        val avatarBackground = MaterialShapeDrawable.createWithElevationOverlay(this, appBarElevation).apply {
            fillColor = ColorStateList.valueOf(toolbarColor)
            elevation = appBarElevation
            shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(resources.getDimension(R.dimen.account_avatar_background_radius))
                    .build()
        }
        accountAvatarImageView.background = avatarBackground

        // Add a listener to change the toolbar icon color when it enters/exits its collapsed state.
        accountAppBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {

            @AttrRes
            var priorAttribute = R.attr.account_toolbar_icon_tint_uncollapsed

            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {

                if(verticalOffset == oldOffset) {
                    return
                }
                oldOffset = verticalOffset

                @AttrRes val attribute = if (titleVisibleHeight + verticalOffset < 0) {
                    supportActionBar?.setDisplayShowTitleEnabled(true)

                    R.attr.account_toolbar_icon_tint_collapsed
                } else {
                    supportActionBar?.setDisplayShowTitleEnabled(false)

                    R.attr.account_toolbar_icon_tint_uncollapsed
                }
                if (attribute != priorAttribute) {
                    priorAttribute = attribute
                    val context = accountToolbar.context
                    ThemeUtils.setDrawableTint(context, accountToolbar.navigationIcon, attribute)
                    ThemeUtils.setDrawableTint(context, accountToolbar.overflowIcon, attribute)
                }

                if (hideFab && !viewModel.isSelf && !blocking) {
                    if (verticalOffset > oldOffset) {
                        accountFloatingActionButton.show()
                    }
                    if (verticalOffset < oldOffset) {
                        accountFloatingActionButton.hide()
                    }
                }

                val scaledAvatarSize = (avatarSize + verticalOffset) / avatarSize

                accountAvatarImageView.scaleX = scaledAvatarSize
                accountAvatarImageView.scaleY = scaledAvatarSize

                accountAvatarImageView.visible(scaledAvatarSize > 0)

                val transparencyPercent = (abs(verticalOffset) / titleVisibleHeight.toFloat()).coerceAtMost(1f)

                window.statusBarColor = argbEvaluator.evaluate(transparencyPercent, statusBarColorTransparent, statusBarColorOpaque) as Int

                val evaluatedToolbarColor = argbEvaluator.evaluate(transparencyPercent, Color.TRANSPARENT, toolbarColor) as Int

                toolbarBackground.fillColor = ColorStateList.valueOf(evaluatedToolbarColor)

                swipeToRefreshLayout.isEnabled = verticalOffset == 0
            }
        })

    }

    private fun makeNotificationBarTransparent() {
        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = statusBarColorTransparent
    }

    /**
     * Subscribe to data loaded at the view model
     */
    private fun subscribeObservables() {
        viewModel.accountData.observe(this, Observer<Resource<Account>> {
            when (it) {
                is Success -> onAccountChanged(it.data)
                is Error -> {
                    Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) { viewModel.refresh() }
                            .show()
                }
            }
        })
        viewModel.relationshipData.observe(this, Observer<Resource<Relationship>> {
            val relation = it?.data
            if (relation != null) {
                onRelationshipChanged(relation)
            }

            if (it is Error) {
                Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_retry) { viewModel.refresh() }
                        .show()
            }

        })
    }

    /**
     * Setup swipe to refresh layout
     */
    private fun setupRefreshLayout() {
        swipeToRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
            adapter.refreshContent()
        }
        viewModel.isRefreshing.observe(this, Observer { isRefreshing ->
            swipeToRefreshLayout.isRefreshing = isRefreshing == true
        })
        swipeToRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        swipeToRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(this,
                android.R.attr.colorBackground))
    }

    private fun onAccountChanged(account: Account?) {
        loadedAccount = account ?: return

        val usernameFormatted = getString(R.string.status_username_format, account.username)
        accountUsernameTextView.text = usernameFormatted
        accountDisplayNameTextView.text = CustomEmojiHelper.emojifyString(account.name, account.emojis, accountDisplayNameTextView)

        val emojifiedNote = CustomEmojiHelper.emojifyText(account.note, account.emojis, accountNoteTextView)
        LinkHelper.setClickableText(accountNoteTextView, emojifiedNote, null, this)

        accountFieldAdapter.fields = account.fields ?: emptyList()
        accountFieldAdapter.emojis = account.emojis ?: emptyList()
        accountFieldAdapter.notifyDataSetChanged()


        accountLockedImageView.visible(account.locked)
        accountBadgeTextView.visible(account.bot)

        updateAccountAvatar()
        updateToolbar()
        updateMovedAccount()
        updateRemoteAccount()
        updateAccountStats()
        invalidateOptionsMenu()

        accountMuteButton.setOnClickListener {
            viewModel.changeMuteState()
            updateMuteButton()
        }
    }

    /**
     * Load account's avatar and header image
     */
    private fun updateAccountAvatar() {
        loadedAccount?.let { account ->

            loadAvatar(
                    account.avatar,
                    accountAvatarImageView,
                    resources.getDimensionPixelSize(R.dimen.avatar_radius_94dp),
                    animateAvatar
            )

            Glide.with(this)
                    .asBitmap()
                    .load(account.header)
                    .centerCrop()
                    .into(accountHeaderImageView)


            accountAvatarImageView.setOnClickListener { avatarView ->
                val intent = ViewMediaActivity.newAvatarIntent(avatarView.context, account.avatar)

                avatarView.transitionName = account.avatar
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, avatarView, account.avatar)

                startActivity(intent, options.toBundle())
            }
        }
    }

    /**
     * Update toolbar views for loaded account
     */
    private fun updateToolbar() {
        loadedAccount?.let { account ->

            val emojifiedName = CustomEmojiHelper.emojifyString(account.name, account.emojis, accountToolbar)

            try {
                supportActionBar?.title = EmojiCompat.get().process(emojifiedName)
            } catch (e: IllegalStateException) {
                supportActionBar?.title = emojifiedName
            }
            supportActionBar?.subtitle = String.format(getString(R.string.status_username_format), account.username)
        }
    }

    /**
     * Update moved account info
     */
    private fun updateMovedAccount() {
        loadedAccount?.moved?.let { movedAccount ->

            accountMovedView?.show()

            // necessary because accountMovedView is now replaced in layout hierachy
            findViewById<View>(R.id.accountMovedViewLayout).setOnClickListener {
                onViewAccount(movedAccount.id)
            }

            accountMovedDisplayName.text = movedAccount.name
            accountMovedUsername.text = getString(R.string.status_username_format, movedAccount.username)

            val avatarRadius = resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)

            loadAvatar(movedAccount.avatar, accountMovedAvatar, avatarRadius, animateAvatar)

            accountMovedText.text = getString(R.string.account_moved_description, movedAccount.displayName)

            // this is necessary because API 19 can't handle vector compound drawables
            val movedIcon = ContextCompat.getDrawable(this, R.drawable.ic_briefcase)?.mutate()
            val textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
            movedIcon?.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)

            accountMovedText.setCompoundDrawablesRelativeWithIntrinsicBounds(movedIcon, null, null, null)
        }

    }

    /**
     * Check is account remote and update info if so
     */
    private fun updateRemoteAccount() {
        loadedAccount?.let { account ->
            if (account.isRemote()) {
                accountRemoveView.show()
                accountRemoveView.setOnClickListener {
                    LinkHelper.openLink(account.url, this)
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
            accountFollowersTextView.text = numberFormat.format(account.followersCount)
            accountFollowingTextView.text = numberFormat.format(account.followingCount)
            accountStatusesTextView.text = numberFormat.format(account.statusesCount)

            accountFloatingActionButton.setOnClickListener { mention() }

            accountFollowButton.setOnClickListener {
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
        showingReblogs = relation.showingReblogs

        accountFollowsYouTextView.visible(relation.followedBy)

        updateButtons()
    }

    private fun updateFollowButton() {
        if (viewModel.isSelf) {
            accountFollowButton.setText(R.string.action_edit_own_profile)
            return
        }
        if (blocking) {
            accountFollowButton.setText(R.string.action_unblock)
            return
        }
        when (followState) {
            FollowState.NOT_FOLLOWING -> {
                accountFollowButton.setText(R.string.action_follow)
            }
            FollowState.REQUESTED -> {
                accountFollowButton.setText(R.string.state_follow_requested)
            }
            FollowState.FOLLOWING -> {
                accountFollowButton.setText(R.string.action_unfollow)
            }
        }
    }

    private fun updateMuteButton() {
        if (muting) {
            accountMuteButton.setIconResource(R.drawable.ic_unmute_24dp)
        } else {
            accountMuteButton.hide()
        }
    }

    private fun updateButtons() {
        invalidateOptionsMenu()

        if (loadedAccount?.moved == null) {

            accountFollowButton.show()
            updateFollowButton()

            if (blocking || viewModel.isSelf) {
                accountFloatingActionButton.hide()
                accountMuteButton.hide()
            } else {
                accountFloatingActionButton.show()
                if (muting)
                    accountMuteButton.show()
                else
                    accountMuteButton.hide()
                updateMuteButton()
            }

        } else {
            accountFloatingActionButton.hide()
            accountFollowButton.hide()
            accountMuteButton.hide()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.account_toolbar, menu)

        if (!viewModel.isSelf) {
            val follow = menu.findItem(R.id.action_follow)
            follow.title = if (followState == FollowState.NOT_FOLLOWING) {
                getString(R.string.action_follow)
            } else {
                getString(R.string.action_unfollow)
            }

            follow.isVisible = followState != FollowState.REQUESTED

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

            if (loadedAccount != null) {
                val muteDomain = menu.findItem(R.id.action_mute_domain)
                domain = LinkHelper.getDomain(loadedAccount?.url)
                if (domain.isEmpty()) {
                    // If we can't get the domain, there's no way we can mute it anyway...
                    menu.removeItem(R.id.action_mute_domain)
                } else {
                    muteDomain.title = getString(R.string.action_mute_domain, domain)
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
            // It shouldn't be possible to block, follow, mute or report yourself.
            menu.removeItem(R.id.action_follow)
            menu.removeItem(R.id.action_block)
            menu.removeItem(R.id.action_mute)
            menu.removeItem(R.id.action_mute_domain)
            menu.removeItem(R.id.action_show_reblogs)
            menu.removeItem(R.id.action_report)
        }

        return super.onCreateOptionsMenu(menu)
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

    private fun showMuteDomainWarningDialog(instance: String) {
        AlertDialog.Builder(this)
                .setMessage(getString(R.string.mute_domain_warning, instance))
                .setPositiveButton(getString(R.string.mute_domain_warning_dialog_ok)) { _, _ -> viewModel.muteDomain(instance) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun mention() {
        loadedAccount?.let {
            val intent = ComposeActivity.IntentBuilder()
                    .mentionedUsernames(setOf(it.username))
                    .build(this)
            startActivity(intent)
        }
    }

    override fun onViewTag(tag: String) {
        val intent = Intent(this, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_mention -> {
                mention()
                return true
            }
            R.id.action_open_in_web -> {
                // If the account isn't loaded yet, eat the input.
                if (loadedAccount != null) {
                    LinkHelper.openLink(loadedAccount?.url, this)
                }
                return true
            }
            R.id.action_follow -> {
                viewModel.changeFollowState()
                return true
            }
            R.id.action_block -> {
                viewModel.changeBlockState()
                return true
            }
            R.id.action_mute -> {
                viewModel.changeMuteState()
                return true
            }
            R.id.action_mute_domain -> {
                showMuteDomainWarningDialog(domain)
                return true
            }
            R.id.action_show_reblogs -> {
                viewModel.changeShowReblogsState()
                return true
            }
            R.id.action_report -> {
                if(loadedAccount != null) {
                    startActivity(ReportActivity.getIntent(this, viewModel.accountId, loadedAccount!!.username))
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getActionButton(): FloatingActionButton? {
        return if (!viewModel.isSelf && !blocking) {
            accountFloatingActionButton
        } else null
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
