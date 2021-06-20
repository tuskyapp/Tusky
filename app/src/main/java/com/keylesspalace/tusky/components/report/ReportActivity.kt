/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.report.adapter.ReportPagerAdapter
import com.keylesspalace.tusky.databinding.ActivityReportBinding
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.viewBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ReportActivity : BottomSheetActivity(), HasAndroidInjector {

    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ReportViewModel by viewModels { viewModelFactory }

    private val binding by viewBinding(ActivityReportBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = intent?.getStringExtra(ACCOUNT_ID)
        val accountUserName = intent?.getStringExtra(ACCOUNT_USERNAME)
        if (accountId.isNullOrBlank() || accountUserName.isNullOrBlank()) {
            throw IllegalStateException("accountId ($accountId) or accountUserName ($accountUserName) is null")
        }

        viewModel.init(accountId, accountUserName, intent?.getStringExtra(STATUS_ID))

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        supportActionBar?.apply {
            title = getString(R.string.report_username_format, viewModel.accountUserName)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_close_24dp)
        }

        initViewPager()
        if (savedInstanceState == null) {
            viewModel.navigateTo(Screen.Statuses)
        }
        subscribeObservables()
    }

    private fun initViewPager() {
        binding.wizard.isUserInputEnabled = false
        binding.wizard.adapter = ReportPagerAdapter(this)
    }

    private fun subscribeObservables() {
        viewModel.navigation.observe(this) { screen ->
            if (screen != null) {
                viewModel.navigated()
                when (screen) {
                    Screen.Statuses -> showStatusesPage()
                    Screen.Note -> showNotesPage()
                    Screen.Done -> showDonePage()
                    Screen.Back -> showPreviousScreen()
                    Screen.Finish -> closeScreen()
                }
            }
        }

        viewModel.checkUrl.observe(this) {
            if (!it.isNullOrBlank()) {
                viewModel.urlChecked()
                viewUrl(it)
            }
        }
    }

    private fun showPreviousScreen() {
        when (binding.wizard.currentItem) {
            0 -> closeScreen()
            1 -> showStatusesPage()
        }
    }

    private fun showDonePage() {
        binding.wizard.currentItem = 2
    }

    private fun showNotesPage() {
        binding.wizard.currentItem = 1
    }

    private fun closeScreen() {
        finish()
    }

    private fun showStatusesPage() {
        binding.wizard.currentItem = 0
    }

    companion object {
        private const val ACCOUNT_ID = "account_id"
        private const val ACCOUNT_USERNAME = "account_username"
        private const val STATUS_ID = "status_id"

        @JvmStatic
        fun getIntent(context: Context, accountId: String, userName: String, statusId: String? = null) =
            Intent(context, ReportActivity::class.java)
                .apply {
                    putExtra(ACCOUNT_ID, accountId)
                    putExtra(ACCOUNT_USERNAME, userName)
                    putExtra(STATUS_ID, statusId)
                }
    }

    override fun androidInjector() = androidInjector
}
