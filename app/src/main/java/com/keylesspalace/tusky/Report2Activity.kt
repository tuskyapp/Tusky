package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Spanned
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.fragment.report.ReportStatusesFragment
import com.keylesspalace.tusky.fragment.report.Screen
import com.keylesspalace.tusky.util.HtmlUtils
import com.keylesspalace.tusky.viewmodel.ReportViewModel
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

class Report2Activity : BaseActivity(), HasSupportFragmentInjector {

    @Inject
    lateinit var dispatchingFragmentInjector: DispatchingAndroidInjector<Fragment>

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private lateinit var viewModel: ReportViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, viewModelFactory)[ReportViewModel::class.java]
        val accountId = intent?.getStringExtra(ACCOUNT_ID)
        val accountUserName = intent?.getStringExtra(ACCOUNT_USERNAME)
        if (accountId.isNullOrBlank() || accountUserName.isNullOrBlank()) {
            finish()
            return
        }

        viewModel.init(accountId, accountUserName,
                intent?.getStringExtra(STATUS_ID), intent?.getStringExtra(STATUS_CONTENT))


        setContentView(R.layout.activity_report2)

        setSupportActionBar(toolbar)

        val bar = supportActionBar
        if (bar != null) {
            bar.title = getString(R.string.report_username_format, viewModel.accountUserName)
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }

        if (savedInstanceState == null) {
            viewModel.navigateTo(Screen.Statuses)
        }
        subscribeObservables()
    }

    private fun subscribeObservables() {
        viewModel.navigation.observe(this, Observer { screen->
            if (screen!=null){
                viewModel.navigated()
                when(screen){
                    Screen.Statuses -> showFirstPage()
                    Screen.Note -> TODO()
                    Screen.Done -> TODO()
                    Screen.Back -> onBackPressed()
                }
            }
        })
    }

    private fun showFragment(fragment: Fragment, isMainScreen: Boolean = false) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.fragment_container, fragment)
        if (!isMainScreen)
            fragmentTransaction.addToBackStack(fragment.javaClass.simpleName)
        fragmentTransaction.commit()
    }

    private fun showFirstPage() {
        showFragment(ReportStatusesFragment.newInstance(), true)
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

    companion object {
        private const val ACCOUNT_ID = "account_id"
        private const val ACCOUNT_USERNAME = "account_username"
        private const val STATUS_ID = "status_id"
        private const val STATUS_CONTENT = "status_content"

        @JvmStatic
        fun getIntent(context: Context, accountId: String, userName: String, statusId: String, statusContent: Spanned) =
                Intent(context, Report2Activity::class.java)
                        .apply {
                            putExtra(ACCOUNT_ID, accountId)
                            putExtra(ACCOUNT_USERNAME, userName)
                            putExtra(STATUS_ID, statusId)
                            putExtra(STATUS_CONTENT, HtmlUtils.toHtml(statusContent))
                        }
    }

    override fun supportFragmentInjector(): AndroidInjector<Fragment> = dispatchingFragmentInjector
}
