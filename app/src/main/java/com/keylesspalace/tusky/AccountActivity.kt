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
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.Px
import android.support.design.widget.AppBarLayout
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.pager.AccountPagerAdapter
import com.keylesspalace.tusky.receiver.TimelineReceiver
import com.keylesspalace.tusky.util.Assert
import com.keylesspalace.tusky.util.LinkHelper
import com.keylesspalace.tusky.util.ThemeUtils
import com.keylesspalace.tusky.view.RoundedTransformation
import com.squareup.picasso.Picasso
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.activity_account.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import javax.inject.Inject

class AccountActivity : BottomSheetActivity(), ActionButtonActivity, HasSupportFragmentInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    private var accountId: String? = null
    private var followState: FollowState? = null
    private var blocking: Boolean = false
    private var muting: Boolean = false
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
        obtainAccount()

        val activeAccount = accountManager.activeAccount

        if (accountId == activeAccount!!.accountId) {
            isSelf = true
        } else {
            isSelf = false
            obtainRelationships()
        }

        // Setup the tabs and timeline pager.
        val adapter = AccountPagerAdapter(supportFragmentManager, accountId)
        val pageTitles = arrayOf(getString(R.string.title_statuses), getString(R.string.title_media))
        adapter.setPageTitles(pageTitles)
        accountFragmentViewPager.pageMargin = resources.getDimensionPixelSize(R.dimen.tab_page_margin)
        val pageMarginDrawable = ThemeUtils.getDrawable(this, R.attr.tab_page_margin_drawable,
                R.drawable.tab_page_margin_dark)
        accountFragmentViewPager.setPageMarginDrawable(pageMarginDrawable)
        accountFragmentViewPager.adapter = adapter
        accountFragmentViewPager.offscreenPageLimit = 0
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("accountId", accountId)
        outState.putSerializable("followState", followState)
        outState.putBoolean("blocking", blocking)
        outState.putBoolean("muting", muting)
        super.onSaveInstanceState(outState)
    }

    private fun obtainAccount() {
        mastodonApi.account(accountId).enqueue(object : Callback<Account> {
            override fun onResponse(call: Call<Account>,
                                    response: Response<Account>) {
                if (response.isSuccessful) {
                    onObtainAccountSuccess(response.body()!!)
                } else {
                    onObtainAccountFailure()
                }
            }

            override fun onFailure(call: Call<Account>, t: Throwable) {
                onObtainAccountFailure()
            }
        })
    }

    private fun onObtainAccountSuccess(account: Account) {
        loadedAccount = account

        val usernameFormatted = getString(R.string.status_username_format, account.username)
        accountUsernameTextView.text = usernameFormatted

        accountDisplayNameTextView.text = account.name

        if (supportActionBar != null) {
            //EmojiCompat.get().process(
            supportActionBar!!.title = account.name

            val subtitle = String.format(getString(R.string.status_username_format),
                    account.username)
            supportActionBar!!.subtitle = subtitle
        }

        LinkHelper.setClickableText(accountNoteTextView, account.note, null, object : LinkListener {
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
        })

        accountLockedImageView.visibility = if (account.locked) {
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

        val numberFormat = NumberFormat.getNumberInstance()

        accountFollowersTextView.text = numberFormat.format(account.followersCount)
        accountFollowingTextView.text = numberFormat.format(account.followingCount)
        accountStatusesTextView.text = numberFormat.format(account.statusesCount)

    }

    private fun onObtainAccountFailure() {
        Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry) { obtainAccount() }
                .show()
    }

    private fun obtainRelationships() {
        val ids = listOf(accountId)
        mastodonApi.relationships(ids).enqueue(object : Callback<List<Relationship>> {
            override fun onResponse(call: Call<List<Relationship>>,
                                    response: Response<List<Relationship>>) {
                val relationships = response.body()
                if (response.isSuccessful && relationships != null) {
                    val relationship = relationships[0]
                    onObtainRelationshipsSuccess(relationship)
                } else {
                    onObtainRelationshipsFailure(Exception(response.message()))
                }
            }

            override fun onFailure(call: Call<List<Relationship>>, t: Throwable) {
                onObtainRelationshipsFailure(t as Exception)
            }
        })
    }

    private fun onObtainRelationshipsSuccess(relation: Relationship) {
        followState = when {
            relation.following -> FollowState.FOLLOWING
            relation.requested -> FollowState.REQUESTED
            else -> FollowState.NOT_FOLLOWING
        }
        this.blocking = relation.blocking
        this.muting = relation.muting

        if (relation.followedBy) {
            accountFollowsYouTextView.visibility = View.VISIBLE
        } else {
            accountFollowsYouTextView.visibility = View.GONE
        }

        updateButtons()
    }

    private fun updateFollowButton(button: Button) {
        when (followState) {
            AccountActivity.FollowState.NOT_FOLLOWING -> {
                button.setText(R.string.action_follow)
            }
            AccountActivity.FollowState.REQUESTED -> {
                button.setText(R.string.state_follow_requested)
            }
            AccountActivity.FollowState.FOLLOWING -> {
                button.setText(R.string.action_unfollow)
            }
        }
    }

    private fun updateButtons() {
        invalidateOptionsMenu()

        if (!isSelf && !blocking) {
            accountFloatingActionButton.show()
            accountFollowButton.visibility = View.VISIBLE

            updateFollowButton(accountFollowButton)

            accountFloatingActionButton.setOnClickListener { _ -> mention() }

            accountFollowButton.setOnClickListener { _ ->
                when (followState) {
                    AccountActivity.FollowState.NOT_FOLLOWING -> {
                        follow(accountId)
                    }
                    AccountActivity.FollowState.REQUESTED -> {
                        showFollowRequestPendingDialog()
                    }
                    AccountActivity.FollowState.FOLLOWING -> {
                        showUnfollowWarningDialog()
                    }
                }
                updateFollowButton(accountFollowButton)
            }
        } else {
            accountFloatingActionButton.hide()
            accountFollowButton.visibility = View.GONE
        }
    }

    private fun onObtainRelationshipsFailure(exception: Exception) {
        Log.e(TAG, "Could not obtain relationships. " + exception.message)
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
            var title = if (blocking) {
                getString(R.string.action_unblock)
            } else {
                getString(R.string.action_block)
            }
            block.title = title
            val mute = menu.findItem(R.id.action_mute)
            title = if (muting) {
                getString(R.string.action_unmute)
            } else {
                getString(R.string.action_mute)
            }
            mute.title = title
        } else {
            // It shouldn't be possible to block or follow yourself.
            menu.removeItem(R.id.action_follow)
            menu.removeItem(R.id.action_block)
            menu.removeItem(R.id.action_mute)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun follow(id: String?) {
        val cb = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>,
                                    response: Response<Relationship>) {
                val relationship = response.body()
                if (response.isSuccessful && relationship != null) {
                    when {
                        relationship.following -> {
                            followState = FollowState.FOLLOWING
                        }
                        relationship.requested -> {
                            followState = FollowState.REQUESTED
                            Snackbar.make(accountCoordinatorLayout, R.string.state_follow_requested,
                                    Snackbar.LENGTH_LONG).show()
                        }
                        else -> {
                            followState = FollowState.NOT_FOLLOWING
                            broadcast(TimelineReceiver.Types.UNFOLLOW_ACCOUNT, id)
                        }
                    }
                    updateButtons()
                } else {
                    onFollowFailure(id)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onFollowFailure(id)
            }
        }

        Assert.expect(followState != FollowState.REQUESTED)
        when (followState) {
            AccountActivity.FollowState.NOT_FOLLOWING -> {
                mastodonApi.followAccount(id).enqueue(cb)
            }
            AccountActivity.FollowState.FOLLOWING -> {
                mastodonApi.unfollowAccount(id).enqueue(cb)
            }
        }
    }

    private fun onFollowFailure(id: String?) {
        val listener = { _: View -> follow(id) }
        Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show()
    }

    private fun showFollowRequestPendingDialog() {
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_message_follow_request)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    private fun showUnfollowWarningDialog() {
        val unfollowListener = { _: DialogInterface, _: Int -> follow(accountId) }
        AlertDialog.Builder(this)
                .setMessage(R.string.dialog_unfollow_warning)
                .setPositiveButton(android.R.string.ok, unfollowListener)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun block(id: String?) {
        val cb = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>,
                                    response: Response<Relationship>) {
                val relationship = response.body()
                if (response.isSuccessful && relationship != null) {
                    broadcast(TimelineReceiver.Types.BLOCK_ACCOUNT, id)
                    blocking = relationship.blocking
                    updateButtons()
                } else {
                    onBlockFailure(id)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onBlockFailure(id)
            }
        }
        if (blocking) {
            mastodonApi.unblockAccount(id).enqueue(cb)
        } else {
            mastodonApi.blockAccount(id).enqueue(cb)
        }
    }

    private fun onBlockFailure(id: String?) {
        val listener = { _: View -> block(id) }
        Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
                .show()
    }

    private fun mute(id: String?) {
        val cb = object : Callback<Relationship> {
            override fun onResponse(call: Call<Relationship>,
                                    response: Response<Relationship>) {
                val relationship = response.body()
                if (response.isSuccessful && relationship != null) {
                    broadcast(TimelineReceiver.Types.MUTE_ACCOUNT, id)
                    muting = relationship.muting
                    updateButtons()
                } else {
                    onMuteFailure(id)
                }
            }

            override fun onFailure(call: Call<Relationship>, t: Throwable) {
                onMuteFailure(id)
            }
        }

        if (muting) {
            mastodonApi.unmuteAccount(id).enqueue(cb)
        } else {
            mastodonApi.muteAccount(id).enqueue(cb)
        }
    }

    private fun onMuteFailure(id: String?) {
        val listener = { _: View -> mute(id) }
        Snackbar.make(accountCoordinatorLayout, R.string.error_generic, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_retry, listener)
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

    private fun broadcast(action: String, id: String?) {
        val intent = Intent(action)
        intent.putExtra("id", id)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
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
                LinkHelper.openLink(loadedAccount!!.url, this)
                return true
            }
            R.id.action_follow -> {
                follow(accountId)
                return true
            }
            R.id.action_block -> {
                block(accountId)
                return true
            }
            R.id.action_mute -> {
                mute(accountId)
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
        private val argbEvaluator = ArgbEvaluator()
    }

}
