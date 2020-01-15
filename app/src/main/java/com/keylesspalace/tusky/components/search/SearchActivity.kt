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

package com.keylesspalace.tusky.components.search

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.tabs.TabLayoutMediator
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.search.adapter.SearchPagerAdapter
import com.keylesspalace.tusky.di.ViewModelFactory
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.android.synthetic.main.activity_search.*
import javax.inject.Inject

class SearchActivity : BottomSheetActivity(), HasAndroidInjector {
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: SearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        viewModel = ViewModelProviders.of(this, viewModelFactory)[SearchViewModel::class.java]
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        setupPages()
        handleIntent(intent)
    }

    private fun setupPages() {
        pages.adapter = SearchPagerAdapter(this)

        TabLayoutMediator(tabs, pages) {
            tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.search_toolbar, menu)
        val searchView = menu.findItem(R.id.action_search)
                .actionView as SearchView
        setupSearchView(searchView)

        searchView.setQuery(viewModel.currentQuery, false)

        return true
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

    private fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> getString(R.string.title_statuses)
            1 -> getString(R.string.title_accounts)
            2 -> getString(R.string.title_hashtags_dialog)
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            viewModel.currentQuery = intent.getStringExtra(SearchManager.QUERY) ?: ""
            viewModel.search(viewModel.currentQuery)
        }
    }

    private fun setupSearchView(searchView: SearchView) {
        searchView.setIconifiedByDefault(false)

        searchView.setSearchableInfo((getSystemService(Context.SEARCH_SERVICE) as? SearchManager)?.getSearchableInfo(componentName))

        searchView.requestFocus()

        searchView.maxWidth = Integer.MAX_VALUE
    }

    override fun androidInjector() = androidInjector

    companion object {
        @JvmStatic
        fun getIntent(context: Context) = Intent(context, SearchActivity::class.java)
    }
}
