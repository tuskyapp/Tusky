package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.keylesspalace.tusky.fragment.TimelineFragment
import com.keylesspalace.tusky.interfaces.ActionButtonActivity
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

class ModalTimelineActivity : BottomSheetActivity(), ActionButtonActivity, HasAndroidInjector {

    companion object {
        private const val ARG_KIND = "kind"
        private const val ARG_ARG = "arg"

        @JvmStatic
        fun newIntent(context: Context, kind: TimelineFragment.Kind,
                      argument: String?): Intent {
            val intent = Intent(context, ModalTimelineActivity::class.java)
            intent.putExtra(ARG_KIND, kind)
            intent.putExtra(ARG_ARG, argument)
            return intent
        }

    }

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modal_timeline)

        setSupportActionBar(toolbar)
        val bar = supportActionBar
        if (bar != null) {
            bar.title = getString(R.string.title_list_timeline)
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }

        if (supportFragmentManager.findFragmentById(R.id.contentFrame) == null) {
            val kind = intent?.getSerializableExtra(ARG_KIND) as? TimelineFragment.Kind
                    ?: TimelineFragment.Kind.HOME
            val argument = intent?.getStringExtra(ARG_ARG)
            supportFragmentManager.beginTransaction()
                    .replace(R.id.contentFrame, TimelineFragment.newInstance(kind, argument))
                    .commit()
        }
    }

    override fun getActionButton(): FloatingActionButton? = null

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return false
    }

    override fun androidInjector() = dispatchingAndroidInjector

}
