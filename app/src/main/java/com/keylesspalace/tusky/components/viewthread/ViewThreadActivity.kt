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
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityViewThreadBinding
import com.keylesspalace.tusky.util.viewBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ViewThreadActivity : BottomSheetActivity(), HasAndroidInjector {

    private val binding by viewBinding(ActivityViewThreadBinding::inflate)

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(true)
        }
        val id = intent.getStringExtra(ID_EXTRA)!!
        val url = intent.getStringExtra(URL_EXTRA)!!
        val fragment =
            supportFragmentManager.findFragmentByTag(FRAGMENT_TAG + id) as ViewThreadFragment?
                ?: ViewThreadFragment.newInstance(id, url)

        supportFragmentManager.commit {
            replace(R.id.fragment_container, fragment, FRAGMENT_TAG + id)
        }
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {

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
