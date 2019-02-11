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
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.preference.PreferenceManager
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.adapter.AccountFieldAdapter
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.pager.AccountPagerAdapter
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.viewmodel.AccountViewModel
import com.squareup.picasso.Picasso
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.activity_account.*
import kotlinx.android.synthetic.main.view_account_moved.*
import java.text.NumberFormat
import javax.inject.Inject

class AccountActivity : BottomSheetActivity(), ActionButtonActivity, HasSupportFragmentInjector, LinkListener {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<androidx.fragment.app.Fragment>
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: AccountViewModel

    private val accountFieldAdapter = AccountFieldAdapter(this)

    private lateinit var accountId: String
    private var followState: FollowState = FollowState.NOT_FOLLOWING
    private var blocking: Boolean = false
    private var muting: Boolean = false
    private var showingReblogs: Boolean = false
    private var isSelf: Boolean = false
    private var loadedAccount: Account? = null

    // fields for scroll animation
    private var hideFab: Boolean = false
    private var oldOffset: Int = 0
    @ColorInt
    private var toolbarColor: Int = 0
    @ColorInt
    private var backgroundColor: Int = 0
    @ColorInt
    private var statusBarColorTransparent: Int = 0
    @ColorInt
    private var statusBarColorOpaque: Int = 0
    @ColorInt
    private var textColorPrimary: Int = 0
    @ColorInt
    private var textColorSecondary: Int = 0
    @Px
    private var avatarSize: Float = 0f
    @Px
    private var titleVisibleHeight: Int = 0

    private enum class FollowState {
        NOT_FOLLOWING,
        FOLLOWING,
        REQUESTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this, viewModelFactory)[AccountViewModel::class.java]

        viewModel.accountData.observe(this, Observer<Resource<Account>> {
            when (it) {
                is Success -> onAccountChanged(it.data)
                is Error -> {
                    Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_retry) { reload() }
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
                        .setAction(R.string.action_retry) { reload() }
                        .show()
            }

        })

        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_account)

        val intent = intent
        accountId = intent.getStringExtra(KEY_ACCOUNT_ID)

        // set toolbar top margin according to system window insets
        accountCoordinatorLayout.setOnApplyWindowInsetsListener { _, insets ->
            val top = insets.systemWindowInsetTop

            val toolbarParams = accountToolbar.layoutParams as CollapsingToolbarLayout.LayoutParams
            toolbarParams.topMargin = top

            insets.consumeSystemWindowInsets()
        }

        // Setup the toolbar.
        setSupportActionBar(accountToolbar)
        supportActionBar?.title = null
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        hideFab = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("fabHide", false)

        toolbarColor = ThemeUtils.getColor(this, R.attr.toolbar_background_color)
        backgroundColor = ThemeUtils.getColor(this, android.R.attr.colorBackground)
        statusBarColorTransparent = ContextCompat.getColor(this, R.color.header_background_filter)
        statusBarColorOpaque = ThemeUtils.getColor(this, R.attr.colorPrimaryDark)
        textColorPrimary = ThemeUtils.getColor(this, android.R.attr.textColorPrimary)
        textColorSecondary = ThemeUtils.getColor(this, android.R.attr.textColorSecondary)
        avatarSize = resources.getDimensionPixelSize(R.dimen.account_activity_avatar_size).toFloat()
        titleVisibleHeight = resources.getDimensionPixelSize(R.dimen.account_activity_scroll_title_visible_height)

        ThemeUtils.setDrawableTint(this, accountToolbar.navigationIcon, R.attr.account_toolbar_icon_tint_uncollapsed)
        ThemeUtils.setDrawableTint(this, accountToolbar.overflowIcon, R.attr.account_toolbar_icon_tint_uncollapsed)

        // Add a listener to change the toolbar icon color when it enters/exits its collapsed state.
        accountAppBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            @AttrRes var priorAttribute = R.attr.account_toolbar_icon_tint_uncollapsed

            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {

                @AttrRes val attribute = if (titleVisibleHeight + verticalOffset < 0) {
                    accountToolbar.setTitleTextColor(textColorPrimary)
                    accountToolbar.setSubtitleTextColor(textColorSecondary)

                    R.attr.account_toolbar_icon_tint_collapsed
                } else {
                    accountToolbar.setTitleTextColor(Color.TRANSPARENT)
                    accountToolbar.setSubtitleTextColor(Color.TRANSPARENT)

                    R.attr.account_toolbar_icon_tint_uncollapsed
                }
                if (attribute != priorAttribute) {
                    priorAttribute = attribute
                    val context = accountToolbar.context
                    ThemeUtils.setDrawableTint(context, accountToolbar.navigationIcon, attribute)
                    ThemeUtils.setDrawableTint(context, accountToolbar.overflowIcon, attribute)
                }

                if (hideFab && !isSelf && !blocking) {
                    if (verticalOffset > oldOffset) {
                        accountFloatingActionButton.show()
                    }
                    if (verticalOffset < oldOffset) {
                        accountFloatingActionButton.hide()
                    }
                }
                oldOffset = verticalOffset

                val scaledAvatarSize = (avatarSize + verticalOffset) / avatarSize

                accountAvatarImageView.scaleX = scaledAvatarSize
                accountAvatarImageView.scaleY = scaledAvatarSize

                accountAvatarImageView.visible(scaledAvatarSize > 0)

                var transparencyPercent = Math.abs(verticalOffset) / titleVisibleHeight.toFloat()
                if (transparencyPercent > 1) transparencyPercent = 1f

                window.statusBarColor = argbEvaluator.evaluate(transparencyPercent, statusBarColorTransparent, statusBarColorOpaque) as Int

                val evaluatedToolbarColor = argbEvaluator.evaluate(transparencyPercent, Color.TRANSPARENT, toolbarColor) as Int
                val evaluatedTabBarColor = argbEvaluator.evaluate(transparencyPercent, backgroundColor, toolbarColor) as Int
                accountToolbar.setBackgroundColor(evaluatedToolbarColor)
                accountHeaderInfoContainer.setBackgroundColor(evaluatedTabBarColor)
                accountTabLayout.setBackgroundColor(evaluatedTabBarColor)
            }
        })

        // Initialise the default UI states.
        accountFloatingActionButton.hide()
        accountFollowButton.hide()
        accountFollowsYouTextView.hide()

        // Obtain information to fill out the profile.
        viewModel.obtainAccount(accountId)

        val activeAccount = accountManager.activeAccount

        if (accountId == activeAccount?.accountId) {
            isSelf = true
            updateButtons()
        } else {
            isSelf = false
            viewModel.obtainRelationship(accountId)
        }

        // setup the RecyclerView for the account fields
        accountFieldList.isNestedScrollingEnabled = false
        accountFieldList.layoutManager = LinearLayoutManager(this)
        accountFieldList.adapter = accountFieldAdapter

        // Setup the tabs and timeline pager.
        val adapter = AccountPagerAdapter(supportFragmentManager, accountId)
        val pageTitles = arrayOf(getString(R.string.title_statuses), getString(R.string.title_statuses_with_replies), getString(R.string.title_statuses_pinned), getString(R.string.title_media))
        adapter.setPageTitles(pageTitles)
        accountFragmentViewPager.pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        val pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark)
        accountFragmentViewPager.setPageMarginDrawable(pageMarginDrawable)
        accountFragmentViewPager.adapter = adapter
        accountFragmentViewPager.offscreenPageLimit = 2
        accountTabLayout.setupWithViewPager(accountFragmentViewPager)

        val accountListClickListener = { v: View ->
            val type = when (v.id) {
                R.id.accountFollowers-> AccountListActivity.Type.FOLLOWERS
                R.id.accountFollowing -> AccountListActivity.Type.FOLLOWS
                else -> throw AssertionError()
            }
            val accountListIntent = AccountListActivity.newIntent(this, type, accountId)
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

    private fun onAccountChanged(account: Account?) {
        if (account != null) {
            loadedAccount = account
            val usernameFormatted = getString(R.string.status_username_format, account.username)
            accountUsernameTextView.text = usernameFormatted
            accountDisplayNameTextView.text = CustomEmojiHelper.emojifyString(account.name, account.emojis, accountDisplayNameTextView)
            if (supportActionBar != null) {
                try {
                    supportActionBar?.title = EmojiCompat.get().process(account.name)
                } catch (e: IllegalStateException) {
                    supportActionBar?.title = account.name
                }

                val subtitle = String.format(getString(R.string.status_username_format),
                        account.username)
                supportActionBar?.subtitle = subtitle
            }
            val emojifiedNote = CustomEmojiHelper.emojifyText(account.note, account.emojis, accountNoteTextView)
            LinkHelper.setClickableText(accountNoteTextView, emojifiedNote, null, this)

            accountLockedImageView.visible(account.locked)
            accountBadgeTextView.visible(account.bot)

            Picasso.with(this)
                    .load(account.avatar)
                    .placeholder(R.drawable.avatar_default)
                    .into(accountAvatarImageView)
            Picasso.with(this)
                    .load(account.header)
                    .fit() // prevents crash with large header images
                    .centerCrop()
                    .into(accountHeaderImageView)

            accountAvatarImageView.setOnClickListener { avatarView ->
                val intent = ViewMediaActivity.newAvatarIntent(avatarView.context, account.avatar)

                avatarView.transitionName = account.avatar
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, avatarView, account.avatar)

                startActivity(intent, options.toBundle())
            }

            accountFieldAdapter.fields = account.fields ?: emptyList()
            accountFieldAdapter.emojis = account.emojis ?: emptyList()
            accountFieldAdapter.notifyDataSetChanged()

            if (account.moved != null) {
                val movedAccount = account.moved

                accountMovedView.show()

                // necessary because accountMovedView is now replaced in layout hierachy
                findViewById<View>(R.id.accountMovedView).setOnClickListener {
                    onViewAccount(movedAccount.id)
                }

                accountMovedDisplayName.text = movedAccount.name
                accountMovedUsername.text = getString(R.string.status_username_format, movedAccount.username)

                Picasso.with(this)
                        .load(movedAccount.avatar)
                        .placeholder(R.drawable.avatar_default)
                        .into(accountMovedAvatar)

                accountMovedText.text = getString(R.string.account_moved_description, movedAccount.displayName)

                // this is necessary because API 19 can't handle vector compound drawables
                val movedIcon = ContextCompat.getDrawable(this, R.drawable.ic_briefcase)?.mutate()
                val textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
                movedIcon?.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)

                accountMovedText.setCompoundDrawablesRelativeWithIntrinsicBounds(movedIcon, null, null, null)

                accountFollowers.hide()
                accountFollowing.hide()
                accountStatuses.hide()
                accountTabLayout.hide()
                accountFragmentViewPager.hide()
            }

            if (account.isRemote()) {
                accountRemoveView.show()
                accountRemoveView.setOnClickListener {
                    LinkHelper.openLink(account.url, this)
                }
            }

            val numberFormat = NumberFormat.getNumberInstance()
            accountFollowersTextView.text = numberFormat.format(account.followersCount)
            accountFollowingTextView.text = numberFormat.format(account.followingCount)
            accountStatusesTextView.text = numberFormat.format(account.statusesCount)

            accountFloatingActionButton.setOnClickListener { mention() }

            accountFollowButton.setOnClickListener {
                if (isSelf) {
                    val intent = Intent(this@AccountActivity, EditProfileActivity::class.java)
                    startActivity(intent)
                    return@setOnClickListener
                }
                when (followState) {
                    AccountActivity.FollowState.NOT_FOLLOWING -> {
                        viewModel.changeFollowState(accountId)
                    }
                    AccountActivity.FollowState.REQUESTED -> {
                        showFollowRequestPendingDialog()
                    }
                    AccountActivity.FollowState.FOLLOWING -> {
                        showUnfollowWarningDialog()
                    }
                }
                updateFollowButton()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_ACCOUNT_ID, accountId)
        super.onSaveInstanceState(outState)
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

    private fun reload() {
        viewModel.obtainAccount(accountId, true)
        viewModel.obtainRelationship(accountId)
    }

    private fun updateFollowButton() {
        if(isSelf) {
            accountFollowButton.setText(R.string.action_edit_own_profile)
            return
        }
        when (followState) {
            AccountActivity.FollowState.NOT_FOLLOWING -> {
                accountFollowButton.setText(R.string.action_follow)
            }
            AccountActivity.FollowState.REQUESTED -> {
                accountFollowButton.setText(R.string.state_follow_requested)
            }
            AccountActivity.FollowState.FOLLOWING -> {
                accountFollowButton.setText(R.string.action_unfollow)
            }
        }
    }

    private fun updateButtons() {
        invalidateOptionsMenu()

        if (!blocking && loadedAccount?.moved == null) {

            accountFollowButton.show()
            updateFollowButton()

            if(isSelf) {
                accountFloatingActionButton.hide()
            } else {
                accountFloatingActionButton.show()
            }

        } else {
            accountFloatingActionButton.hide()
            accountFollowButton.hide()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.account_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!isSelf) {
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
            // It shouldn't be possible to block or follow yourself.
            menu.removeItem(R.id.action_follow)
            menu.removeItem(R.id.action_block)
            menu.removeItem(R.id.action_mute)
            menu.removeItem(R.id.action_show_reblogs)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun showFollowRequestPendingDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_message_cancel_follow_request)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState(accountId) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun showUnfollowWarningDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_unfollow_warning)
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.changeFollowState(accountId) }
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
                viewModel.changeFollowState(accountId)
                return true
            }
            R.id.action_block -> {
                viewModel.changeBlockState(accountId)
                return true
            }
            R.id.action_mute -> {
                viewModel.changeMuteState(accountId)
                return true
            }

            R.id.action_show_reblogs -> {
                viewModel.changeShowReblogsState(accountId)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getActionButton(): FloatingActionButton? {
        return if (!isSelf && !blocking) {
            accountFloatingActionButton
        } else null
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return dispatchingAndroidInjector
    }

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
