/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.MenuItem

import tech.bigfig.roma.fragment.AccountListFragment

import javax.inject.Inject

import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.toolbar_basic.*

class AccountListActivity : BaseActivity(), HasSupportFragmentInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    enum class Type {
        FOLLOWS,
        FOLLOWERS,
        BLOCKS,
        MUTES,
        FOLLOW_REQUESTS,
        REBLOGGED,
        FAVOURITED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_list)

        val type = intent.getSerializableExtra(EXTRA_TYPE) as Type
        val id: String? = intent.getStringExtra(EXTRA_ID)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            when (type) {
                Type.BLOCKS -> setTitle(R.string.title_blocks)
                Type.MUTES -> setTitle(R.string.title_mutes)
                Type.FOLLOW_REQUESTS -> setTitle(R.string.title_follow_requests)
                Type.FOLLOWERS -> setTitle(R.string.title_followers)
                Type.FOLLOWS -> setTitle(R.string.title_follows)
                Type.REBLOGGED -> setTitle(R.string.title_reblogged_by)
                Type.FAVOURITED -> setTitle(R.string.title_favourited_by)
            }
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, AccountListFragment.newInstance(type, id))
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment>? {
        return dispatchingAndroidInjector
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ID = "id"

        @JvmStatic
        fun newIntent(context: Context, type: Type, id: String? = null): Intent {
            return Intent(context, AccountListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_ID, id)
            }
        }
    }
}
