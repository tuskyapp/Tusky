package com.keylesspalace.tusky.components.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityLoginWebviewBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.parcelize.Parcelize

/** Contract for starting [LoginWebViewActivity]. */
class OauthLogin : ActivityResultContract<LoginData, LoginResult>() {
    override fun createIntent(context: Context, input: LoginData): Intent {
        val intent = Intent(context, LoginWebViewActivity::class.java)
        intent.putExtra(DATA_EXTRA, input)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): LoginResult {
        // Can happen automatically on up or back press
        return if (resultCode == Activity.RESULT_CANCELED) {
            LoginResult.Cancel
        } else {
            intent!!.getParcelableExtra(RESULT_EXTRA)!!
        }
    }

    companion object {
        private const val RESULT_EXTRA = "result"
        private const val DATA_EXTRA = "data"

        fun parseData(intent: Intent): LoginData {
            return intent.getParcelableExtra(DATA_EXTRA)!!
        }

        fun makeResultIntent(result: LoginResult): Intent {
            val intent = Intent()
            intent.putExtra(RESULT_EXTRA, result)
            return intent
        }
    }
}

@Parcelize
data class LoginData(
    val url: Uri,
    val oauthRedirectUrl: Uri,
) : Parcelable

sealed class LoginResult : Parcelable {
    @Parcelize
    data class Ok(val code: String) : LoginResult()

    @Parcelize
    data class Err(val errorMessage: String) : LoginResult()

    @Parcelize
    object Cancel : LoginResult()
}

/** Activity to do Oauth process using WebView. */
class LoginWebViewActivity : BaseActivity(), Injectable {
    private val binding by viewBinding(ActivityLoginWebviewBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = OauthLogin.parseData(intent)

        setContentView(binding.root)

        setSupportActionBar(binding.loginToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        setTitle(R.string.title_login)

        val webView = binding.loginWebView
        webView.settings.allowContentAccess = false
        webView.settings.allowFileAccess = false
        webView.settings.databaseEnabled = false
        webView.settings.displayZoomControls = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        // Javascript needs to be enabled because otherwise 2FA does not work in some instances
        @SuppressLint("SetJavaScriptEnabled")
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString += " Tusky/${BuildConfig.VERSION_NAME}"

        val oauthUrl = data.oauthRedirectUrl

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.loginProgress.hide()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                Log.d("LoginWeb", "Failed to load ${data.url}: $error")
                sendResult(LoginResult.Err(getString(R.string.error_could_not_load_login_page)))
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return shouldOverrideUrlLoading(request.url)
            }

            /* overriding this deprecated method is necessary for it to work on api levels < 24 */
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, urlString: String?): Boolean {
                val url = urlString?.toUri() ?: return false
                return shouldOverrideUrlLoading(url)
            }

            fun shouldOverrideUrlLoading(url: Uri): Boolean {
                return if (url.scheme == oauthUrl.scheme && url.host == oauthUrl.host) {
                    val error = url.getQueryParameter("error")
                    if (error != null) {
                        sendResult(LoginResult.Err(error))
                    } else {
                        val code = url.getQueryParameter("code").orEmpty()
                        sendResult(LoginResult.Ok(code))
                    }
                    true
                } else {
                    false
                }
            }
        }

        webView.setBackgroundColor(Color.TRANSPARENT)

        if (savedInstanceState == null) {
            webView.loadUrl(data.url.toString())
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.loginWebView.saveState(outState)
    }

    override fun onDestroy() {
        if (isFinishing) {
            // We don't want to keep user session in WebView, we just want our own accessToken
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
        }
        super.onDestroy()
    }

    override fun finish() {
        super.finishWithoutSlideOutAnimation()
    }

    override fun requiresLogin() = false

    private fun sendResult(result: LoginResult) {
        setResult(Activity.RESULT_OK, OauthLogin.makeResultIntent(result))
        finishWithoutSlideOutAnimation()
    }
}
