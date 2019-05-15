package tech.bigfig.roma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.fragment.app.Fragment
import android.view.MenuItem
import tech.bigfig.roma.fragment.TimelineFragment
import tech.bigfig.roma.interfaces.ActionButtonActivity
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

class ModalTimelineActivity : BottomSheetActivity(), ActionButtonActivity, HasSupportFragmentInjector {

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
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

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

    override fun supportFragmentInjector(): AndroidInjector<Fragment> {
        return dispatchingAndroidInjector
    }

}
