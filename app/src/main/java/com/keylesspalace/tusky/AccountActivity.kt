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
import android.app.Activity
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.Px
import android.support.design.widget.*
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.keylesspalace.tusky.adapter.AccountFieldAdapter
import com.keylesspalace.tusky.appstore.BlockEvent
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.MuteEvent
import com.keylesspalace.tusky.appstore.UnfollowEvent
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.pager.AccountPagerAdapter
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.view.RoundedTransformation
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
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>
    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    @Inject
    lateinit var eventHub: EventHub

    private lateinit var viewModel: AccountViewModel

    private val accountFieldAdapter = AccountFieldAdapter(this)

    private lateinit var accountId: String
    private var followState: FollowState? = null
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
    private var statusBarColor: Int = 0
    @Px
    private var avatarSize: Int = 0
    @Px
    private var titleVisibleHeight: Int = 0

    private val followAction: String
        get() {
            return when (followState) {
                AccountActivity.FollowState.NOT_FOLLOWING -> getString(R.string.action_follow)
                AccountActivity.FollowState.REQUESTED, AccountActivity.FollowState.FOLLOWING -> getString(R.string.action_unfollow)
                else -> getString(R.string.action_follow)
            }
        }

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
                is Error -> onObtainAccountFailure()
            }
        })
        viewModel.relationshipData.observe(this, Observer<Resource<Relationship>> {
            val relation = it?.data
            if (it != null) {
                onRelationshipChanged(relation)
            }

            if (it is Success) {
                when {
                //TODO this sends too many events
                    relation?.following == false -> eventHub.dispatch(UnfollowEvent(accountId))
                    relation?.blocking == true -> eventHub.dispatch(BlockEvent(accountId))
                    relation?.muting == true -> eventHub.dispatch(MuteEvent(accountId))
                }
            }

            if (it is Error) {
                Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG).show()
            }

        })

        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
        }

        setContentView(R.layout.activity_account)

        if (savedInstanceState != null) {
            accountId = savedInstanceState.getString("accountId")
            followState = savedInstanceState.getSerializable("followState") as FollowState
            blocking = savedInstanceState.getBoolean("blocking")
            muting = savedInstanceState.getBoolean("muting")
        } else {
            val intent = intent
            accountId = intent.getStringExtra("id")
            followState = FollowState.NOT_FOLLOWING
            blocking = false
            muting = false
        }
        loadedAccount = null

        // set toolbar top margin according to system window insets
        ViewCompat.setOnApplyWindowInsetsListener(accountCoordinatorLayout) { _, insets ->
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
        statusBarColor = ThemeUtils.getColor(this, R.attr.colorPrimaryDark)
        avatarSize = resources.getDimensionPixelSize(R.dimen.account_activity_avatar_size)
        titleVisibleHeight = resources.getDimensionPixelSize(R.dimen.account_activity_scroll_title_visible_height)

        ThemeUtils.setDrawableTint(this, accountToolbar.navigationIcon, R.attr.account_toolbar_icon_tint_uncollapsed)
        ThemeUtils.setDrawableTint(this, accountToolbar.overflowIcon, R.attr.account_toolbar_icon_tint_uncollapsed)

        // Add a listener to change the toolbar icon color when it enters/exits its collapsed state.
        accountAppBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            @AttrRes
            internal var priorAttribute = R.attr.account_toolbar_icon_tint_uncollapsed

            override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {

                @AttrRes val attribute = if (titleVisibleHeight + verticalOffset < 0) {

                    accountToolbar.setTitleTextColor(ThemeUtils.getColor(this@AccountActivity,
                            android.R.attr.textColorPrimary))
                    accountToolbar.setSubtitleTextColor(ThemeUtils.getColor(this@AccountActivity,
                            android.R.attr.textColorSecondary))

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

                val scaledAvatarSize = (avatarSize + verticalOffset) / avatarSize.toFloat()

                accountAvatarImageView.scaleX = scaledAvatarSize
                accountAvatarImageView.scaleY = scaledAvatarSize

                accountAvatarImageView.visibility = if (scaledAvatarSize <= 0) View.GONE else View.VISIBLE

                var transparencyPercent = Math.abs(verticalOffset) / titleVisibleHeight.toFloat()
                if (transparencyPercent > 1) transparencyPercent = 1f

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = ArgbEvaluator().evaluate(transparencyPercent, Color.TRANSPARENT, statusBarColor) as Int
                }

                val evaluatedToolbarColor = argbEvaluator.evaluate(transparencyPercent, Color.TRANSPARENT, toolbarColor) as Int
                val evaluatedTabBarColor = argbEvaluator.evaluate(transparencyPercent, backgroundColor, toolbarColor) as Int
                accountToolbar.setBackgroundColor(evaluatedToolbarColor)
                accountHeaderInfoContainer.setBackgroundColor(evaluatedTabBarColor)
                accountTabLayout.setBackgroundColor(evaluatedTabBarColor)
            }
        })

        // Initialise the default UI states.
        accountFloatingActionButton.hide()
        accountFollowButton.visibility = View.GONE
        accountFollowsYouTextView.visibility = View.GONE

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
        val pageTitles = arrayOf(getString(R.string.title_statuses), getString(R.string.title_statuses_with_replies), getString(R.string.title_media))
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
                R.id.accountFollowersTextView -> AccountListActivity.Type.FOLLOWERS
                R.id.accountFollowingTextView -> AccountListActivity.Type.FOLLOWING
                else -> throw AssertionError()
            }
            val intent = AccountListActivity.newIntent(this@AccountActivity, type,
                    accountId)
            startActivity(intent)
        }
        accountFollowersTextView.setOnClickListener(accountListClickListener)
        accountFollowingTextView.setOnClickListener(accountListClickListener)

        accountStatusesTextView.setOnClickListener {
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
                //EmojiCompat.get().process(
                supportActionBar?.title = account.name

                val subtitle = String.format(getString(R.string.status_username_format),
                        account.username)
                supportActionBar?.subtitle = subtitle
            }
            val emojifiedNote = CustomEmojiHelper.emojifyText(account.note, account.emojis, accountNoteTextView)
            LinkHelper.setClickableText(accountNoteTextView, emojifiedNote, null, this)

            accountLockedImageView.visibility = if (account.locked) {
                View.VISIBLE
            } else {
                View.GONE
            }
            accountBadgeTextView.visibility = if (account.bot) {
                View.VISIBLE
            } else {
                View.GONE
            }
            Picasso.with(this)
                    .load(account.avatar)
                    .transform(RoundedTransformation(25f))
                    .placeholder(R.drawable.avatar_default)
                    .into(accountAvatarImageView)
            Picasso.with(this)
                    .load(account.header)
                    .placeholder(R.drawable.account_header_default)
                    .into(accountHeaderImageView)

            accountFieldAdapter.fields = account.fields
            accountFieldAdapter.emojis = account.emojis
            accountFieldAdapter.notifyDataSetChanged()

            if (account.moved != null) {
                val movedAccount = account.moved

                accountMovedView.visibility = View.VISIBLE

                // necessary because accountMovedView is now replaced in layout hierachy
                findViewById<View>(R.id.accountMovedView).setOnClickListener {
                    onViewAccount(movedAccount.id)
                }

                accountMovedDisplayName.text = movedAccount.name
                accountMovedUsername.text = getString(R.string.status_username_format, movedAccount.username)

                Picasso.with(this)
                        .load(movedAccount.avatar)
                        .transform(RoundedTransformation(25f))
                        .placeholder(R.drawable.avatar_default)
                        .into(accountMovedAvatar)

                accountMovedText.text = getString(R.string.account_moved_description, movedAccount.displayName)

                // this is necessary because API 19 can't handle vactor compound drawables
                val movedIcon = ContextCompat.getDrawable(this, R.drawable.ic_briefcase)?.mutate()
                val textColor = ThemeUtils.getColor(this, android.R.attr.textColorTertiary)
                movedIcon?.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)

                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(accountMovedText, movedIcon, null, null, null)

                accountFollowersTextView.visibility = View.GONE
                accountFollowingTextView.visibility = View.GONE
                accountStatusesTextView.visibility = View.GONE
                accountFollowersDescription.visibility = View.GONE
                accountFollowingDescription.visibility = View.GONE
                accountStatusesDescription.visibility = View.GONE
                accountTabLayout.visibility = View.GONE
                accountFragmentViewPager.visibility = View.GONE
                accountTabBottomShadow.visibility = View.GONE
            }

            val numberFormat = NumberFormat.getNumberInstance()
            accountFollowersTextView.text = numberFormat.format(account.followersCount)
            accountFollowingTextView.text = numberFormat.format(account.followingCount)
            accountStatusesTextView.text = numberFormat.format(account.statusesCount)

            accountFloatingActionButton.setOnClickListener { _ -> mention() }

            accountFollowButton.setOnClickListener { _ ->
                if (isSelf) {
                    val intent = Intent(this@AccountActivity, EditProfileActivity::class.java)
                    startActivityForResult(intent, EDIT_ACCOUNT)
                    return@setOnClickListener
                }
                when (followState) {
                    AccountActivity.FollowState.NOT_FOLLOWING -> {
                        changeFollowState(accountId)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == EDIT_ACCOUNT && resultCode == Activity.RESULT_OK) {
            viewModel.obtainAccount(accountId, true)
        }
    }

    private fun onRelationshipChanged(relation: Relationship?) {
        if (relation != null) {
            followState = when {
                relation.following -> FollowState.FOLLOWING
                relation.requested -> FollowState.REQUESTED
                else -> FollowState.NOT_FOLLOWING
            }
            blocking = relation.blocking
            muting = relation.muting
            showingReblogs = relation.showingReblogs
            if (relation.followedBy) {
                accountFollowsYouTextView.visibility = View.VISIBLE
            } else {
                accountFollowsYouTextView.visibility = View.GONE
            }
            updateButtons()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("accountId", accountId)
        outState.putSerializable("followState", followState)
        outState.putBoolean("blocking", blocking)
        outState.putBoolean("muting", muting)
        super.onSaveInstanceState(outState)
    }

    private fun onObtainAccountFailure() {
        Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                //        .setAction(R.string.action_retry) { obtainAccount() }
                .show()
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
            accountFloatingActionButton.show()
            accountFollowButton.visibility = View.VISIBLE

            updateFollowButton()

            if(!isSelf) {
                accountFloatingActionButton.hide()
            }

        } else {
            accountFloatingActionButton.hide()
            accountFollowButton.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.account_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!isSelf) {
            val follow = menu.findItem(R.id.action_follow)
            follow.title = followAction
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

    private fun changeFollowState(id: String) {
        if (followState == FollowState.NOT_FOLLOWING) {
            viewModel.follow(id)
        } else {
            viewModel.unfollow(id)
        }
    }

    private fun changeBlockState(id: String) {
        if (blocking) {
            viewModel.unblock(id)
        } else {
            viewModel.block(id)
        }
    }

    private fun changeMuteState(id: String) {
        if (muting) {
            viewModel.mute(id)
        } else {
            viewModel.unmute(id)
        }
    }

    private fun changeShowReblogsState(id: String) {
        if (showingReblogs) {
            viewModel.hideReblogs(id)
        } else {
            viewModel.showReblogs(id)
        }
    }

    private fun showFollowRequestPendingDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_message_cancel_follow_request)
                .setPositiveButton(android.R.string.ok) { _, _ -> changeFollowState(accountId) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun showUnfollowWarningDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_unfollow_warning)
                .setPositiveButton(android.R.string.ok) { _, _ -> changeFollowState(accountId) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun mention(): Boolean {
        if (loadedAccount == null) {
            // If the account isn't loaded yet, eat the input.
            return false
        }
        val intent = ComposeActivity.IntentBuilder()
                .mentionedUsernames(setOf(loadedAccount!!.username))
                .build(this)
        startActivity(intent)
        return true
    }

    override fun onViewTag(tag: String) {
        val intent = Intent(this@AccountActivity, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
        startActivity(intent)
    }

    override fun onViewAccount(id: String) {
        val intent = Intent(this@AccountActivity, AccountActivity::class.java)
        intent.putExtra("id", id)
        startActivity(intent)
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
                return mention()
            }
            R.id.action_open_in_web -> {
                if (loadedAccount == null) {
                    // If the account isn't loaded yet, eat the input.
                    return false
                }
                LinkHelper.openLink(loadedAccount?.url, this)
                return true
            }
            R.id.action_follow -> {
                changeFollowState(accountId)
                return true
            }
            R.id.action_block -> {
                changeBlockState(accountId)
                return true
            }
            R.id.action_mute -> {
                changeMuteState(accountId)
                return true
            }

            R.id.action_show_reblogs -> {
                changeShowReblogsState(accountId)
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
        private const val TAG = "AccountActivity"
        private const val EDIT_ACCOUNT = 1457
        private val argbEvaluator = ArgbEvaluator()
    }

}
