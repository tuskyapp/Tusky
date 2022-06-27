/* Copyright 2022 Tusky Contributors
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

package com.keylesspalace.tusky.components.viewthread

import android.content.Context
import com.keylesspalace.tusky.BottomSheetActivity
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import dagger.android.DispatchingAndroidInjector
import android.os.Bundle
import com.keylesspalace.tusky.R
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import java.lang.IllegalArgumentException

class ViewThreadActivity : BottomSheetActivity(), HasAndroidInjector {

    private var revealButtonState = REVEAL_BUTTON_HIDDEN

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    private var fragment: ViewThreadFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_thread)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setTitle(R.string.title_view_thread)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
        }
        val id = intent.getStringExtra(ID_EXTRA)
        fragment =
            supportFragmentManager.findFragmentByTag(FRAGMENT_TAG + id) as ViewThreadFragment?
        if (fragment == null) {
            fragment = ViewThreadFragment.newInstance(id!!)
        }
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment!!, FRAGMENT_TAG + id)
        fragmentTransaction.commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.view_thread_toolbar, menu)
        val menuItem = menu.findItem(R.id.action_reveal)
        menuItem.isVisible = revealButtonState != REVEAL_BUTTON_HIDDEN
        menuItem.setIcon(if (revealButtonState == REVEAL_BUTTON_REVEAL) R.drawable.ic_eye_24dp else R.drawable.ic_hide_media_24dp)
        return super.onCreateOptionsMenu(menu)
    }

    fun setRevealButtonState(state: Int) {
        when (state) {
            REVEAL_BUTTON_HIDDEN, REVEAL_BUTTON_REVEAL, REVEAL_BUTTON_HIDE -> {
                revealButtonState = state
                invalidateOptionsMenu()
            }
            else -> throw IllegalArgumentException("Invalid reveal button state: $state")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_in_web -> {
                openLink(intent.getStringExtra(URL_EXTRA)!!)
                return true
            }
            R.id.action_reveal -> {
                fragment!!.onRevealPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        const val REVEAL_BUTTON_HIDDEN = 1
        const val REVEAL_BUTTON_REVEAL = 2
        const val REVEAL_BUTTON_HIDE = 3

        fun startIntent(context: Context, id: String, url: String): Intent {
            val intent = Intent(context, ViewThreadActivity::class.java)
            intent.putExtra(ID_EXTRA, id)
            intent.putExtra(URL_EXTRA, url)
            return intent
        }

        private const val ID_EXTRA = "id"
        private const val URL_EXTRA = "url"
        private const val FRAGMENT_TAG = "ViewThreadFragment_"
    }
}