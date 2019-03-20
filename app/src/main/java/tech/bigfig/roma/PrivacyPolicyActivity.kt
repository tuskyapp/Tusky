package tech.bigfig.roma

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.uber.autodispose.AutoDispose.autoDisposable
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_privacy_policy.*
import kotlinx.android.synthetic.main.toolbar_basic.*


class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(R.string.title_privacy_policy)

        loadPrivacyPolicy()
    }

    private fun loadPrivacyPolicy() {
        Single.fromCallable<Spanned> {
            val string = getString(R.string.privacy_policy)
            return@fromCallable Html.fromHtml(string)
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(autoDisposable(from(this, Lifecycle.Event.ON_PAUSE)))
                .subscribe(
                        { text ->
                            content.text = text
                        },
                        { error ->
                            Log.d(TAG,"Failed to load content",error)
                            finish()
                        }
                )

    }

    companion object {
        private val TAG = PrivacyPolicyActivity::class.java.simpleName
        fun getIntent(context: Context) = Intent(context, PrivacyPolicyActivity::class.java)
    }
}
