/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.components.accountlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityAccountListBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class AccountListActivity : BaseActivity(), HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

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
        val binding = ActivityAccountListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val type = intent.getSerializableExtra(EXTRA_TYPE) as Type
        val id: String? = intent.getStringExtra(EXTRA_ID)
        val accountLocked: Boolean = intent.getBooleanExtra(EXTRA_ACCOUNT_LOCKED, false)

        setSupportActionBar(binding.includedToolbar.toolbar)
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

        supportFragmentManager.commit {
            replace(R.id.fragment_container, AccountListFragment.newInstance(type, id, accountLocked))
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ID = "id"
        private const val EXTRA_ACCOUNT_LOCKED = "acc_locked"

        fun newIntent(context: Context, type: Type, id: String? = null, accountLocked: Boolean = false): Intent {
            return Intent(context, AccountListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_ACCOUNT_LOCKED, accountLocked)
            }
        }
    }
}
