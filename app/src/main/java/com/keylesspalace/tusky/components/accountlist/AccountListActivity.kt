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
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityAccountListBinding
import com.keylesspalace.tusky.util.getSerializableExtraCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountListActivity : BottomSheetActivity() {

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

        val type = intent.getSerializableExtraCompat<Type>(EXTRA_TYPE)!!
        val id: String? = intent.getStringExtra(EXTRA_ID)

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

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.commit {
                val fragment = AccountListFragment.newInstance(type, id)
                replace(R.id.fragment_container, fragment)
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ID = "id"

        fun newIntent(context: Context, type: Type, id: String? = null): Intent {
            return Intent(context, AccountListActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_ID, id)
            }
        }
    }
}
