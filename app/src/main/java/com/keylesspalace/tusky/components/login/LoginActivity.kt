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

package com.keylesspalace.tusky.components.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.ActivityLoginBinding
import com.keylesspalace.tusky.entity.AccessToken
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getNonNullString
import com.keylesspalace.tusky.util.openLinkInCustomTab
import com.keylesspalace.tusky.util.rickRoll
import com.keylesspalace.tusky.util.setOnWindowInsetsChangeListener
import com.keylesspalace.tusky.util.shouldRickRoll
import com.keylesspalace.tusky.util.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Main login page, the first thing that users see. Has prompt for instance and login button. */
@AndroidEntryPoint
class LoginActivity : BaseActivity() {

    @Inject
    lateinit var mastodonApi: MastodonApi

    private val binding by viewBinding(ActivityLoginBinding::inflate)

    private val oauthRedirectUri: String
        get() {
            val scheme = getString(R.string.oauth_scheme)
            val host = BuildConfig.APPLICATION_ID
            return "$scheme://$host/"
        }

    private val doWebViewAuth = registerForActivityResult(OauthLogin()) { result ->
        when (result) {
            is LoginResult.Ok -> fetchOauthToken(result.code)
            is LoginResult.Err -> displayError(result.errorMessage)
            is LoginResult.Cancel -> setLoading(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.loginScrollView.setOnWindowInsetsChangeListener { windowInsets ->
            val insets = windowInsets.getInsets(systemBars() or ime())
            binding.loginScrollView.updatePadding(bottom = insets.bottom)
        }

        if (savedInstanceState == null &&
            BuildConfig.CUSTOM_INSTANCE.isNotBlank() &&
            !isAdditionalLogin()
        ) {
            binding.domainEditText.setText(BuildConfig.CUSTOM_INSTANCE)
            binding.domainEditText.setSelection(BuildConfig.CUSTOM_INSTANCE.length)
        }

        if (BuildConfig.CUSTOM_LOGO_URL.isNotBlank()) {
            Glide.with(binding.loginLogo)
                .load(BuildConfig.CUSTOM_LOGO_URL)
                .placeholder(null)
                .into(binding.loginLogo)
        }

        binding.loginButton.setOnClickListener { onLoginClick(true) }

        binding.whatsAnInstanceTextView.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(this)
                .setMessage(R.string.dialog_whats_an_instance)
                .setPositiveButton(R.string.action_close, null)
                .show()
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            textView?.movementMethod = LinkMovementMethod.getInstance()
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(isAdditionalLogin())
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    override fun requiresLogin(): Boolean {
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(R.string.action_browser_login)?.apply {
            setOnMenuItemClickListener {
                onLoginClick(false)
                true
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun onLoginClick(openInWebView: Boolean) {
        binding.loginButton.isEnabled = false
        binding.domainTextInputLayout.error = null

        val domain = canonicalizeDomain(binding.domainEditText.text.toString())

        try {
            HttpUrl.Builder().host(domain).scheme("https").build()
        } catch (e: IllegalArgumentException) {
            setLoading(false)
            binding.domainTextInputLayout.error = getString(R.string.error_invalid_domain)
            return
        }

        if (shouldRickRoll(this, domain)) {
            rickRoll(this)
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            mastodonApi.authenticateApp(
                domain,
                getString(R.string.app_name),
                oauthRedirectUri,
                OAUTH_SCOPES,
                getString(R.string.tusky_website)
            ).fold(
                { credentials ->
                    // Save credentials so we can access them after we opened another activity for auth.
                    preferences.edit()
                        .putString(DOMAIN, domain)
                        .putString(CLIENT_ID, credentials.clientId)
                        .putString(CLIENT_SECRET, credentials.clientSecret)
                        .apply()

                    redirectUserToAuthorizeAndLogin(domain, credentials.clientId, openInWebView)
                },
                { e ->
                    binding.loginButton.isEnabled = true
                    binding.domainTextInputLayout.error =
                        getString(R.string.error_failed_app_registration)
                    setLoading(false)
                    Log.e(TAG, Log.getStackTraceString(e))
                    return@launch
                }
            )
        }
    }

    private fun redirectUserToAuthorizeAndLogin(
        domain: String,
        clientId: String,
        openInWebView: Boolean
    ) {
        // To authorize this app and log in it's necessary to redirect to the domain given,
        // login there, and the server will redirect back to the app with its response.
        val uri = Uri.Builder()
            .scheme("https")
            .authority(domain)
            .path(MastodonApi.ENDPOINT_AUTHORIZE)
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", oauthRedirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", OAUTH_SCOPES)
            .build()

        if (openInWebView) {
            doWebViewAuth.launch(LoginData(domain, uri, oauthRedirectUri.toUri()))
        } else {
            openLinkInCustomTab(uri, this)
        }
    }

    override fun onStart() {
        super.onStart()

        /* Check if we are resuming during authorization by seeing if the intent contains the
         * redirect that was given to the server. If so, its response is here! */
        val uri = intent.data

        if (uri?.toString()?.startsWith(oauthRedirectUri) == true) {
            // This should either have returned an authorization code or an error.
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            if (code != null) {
                fetchOauthToken(code)
            } else {
                displayError(error)
            }
        } else {
            // first show or user cancelled login
            setLoading(false)
        }
    }

    private fun displayError(error: String?) {
        // Authorization failed. Put the error response where the user can read it and they
        // can try again.
        setLoading(false)

        binding.domainTextInputLayout.error = if (error == null) {
            // This case means a junk response was received somehow.
            getString(R.string.error_authorization_unknown)
        } else {
            // Use error returned by the server or fall back to the generic message
            Log.e(TAG, getString(R.string.error_authorization_denied) + " " + error)
            error.ifBlank { getString(R.string.error_authorization_denied) }
        }
    }

    private fun fetchOauthToken(code: String) {
        setLoading(true)

        /* restore variables from SharedPreferences */
        val domain = preferences.getNonNullString(DOMAIN, "")
        val clientId = preferences.getNonNullString(CLIENT_ID, "")
        val clientSecret = preferences.getNonNullString(CLIENT_SECRET, "")

        lifecycleScope.launch {
            mastodonApi.fetchOAuthToken(
                domain,
                clientId,
                clientSecret,
                oauthRedirectUri,
                code,
                "authorization_code"
            ).fold(
                { accessToken ->
                    fetchAccountDetails(accessToken, domain, clientId, clientSecret)
                },
                { e ->
                    setLoading(false)
                    binding.domainTextInputLayout.error =
                        getString(R.string.error_retrieving_oauth_token)
                    Log.e(TAG, getString(R.string.error_retrieving_oauth_token), e)
                }
            )
        }
    }

    private suspend fun fetchAccountDetails(
        accessToken: AccessToken,
        domain: String,
        clientId: String,
        clientSecret: String
    ) {
        mastodonApi.accountVerifyCredentials(
            domain = domain,
            auth = "Bearer ${accessToken.accessToken}"
        ).fold({ newAccount ->
            accountManager.addAccount(
                accessToken = accessToken.accessToken,
                domain = domain,
                clientId = clientId,
                clientSecret = clientSecret,
                oauthScopes = OAUTH_SCOPES,
                newAccount = newAccount
            )
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }, { e ->
            setLoading(false)
            binding.domainTextInputLayout.error =
                getString(R.string.error_loading_account_details)
            Log.e(TAG, getString(R.string.error_loading_account_details), e)
        })
    }

    private fun setLoading(loadingState: Boolean) {
        if (loadingState) {
            binding.loginLoadingLayout.visibility = View.VISIBLE
            binding.loginInputLayout.visibility = View.GONE
        } else {
            binding.loginLoadingLayout.visibility = View.GONE
            binding.loginInputLayout.visibility = View.VISIBLE
            binding.loginButton.isEnabled = true
        }
    }

    private fun isAdditionalLogin(): Boolean {
        return intent.getIntExtra(LOGIN_MODE, MODE_DEFAULT) == MODE_ADDITIONAL_LOGIN
    }

    companion object {
        private const val TAG = "LoginActivity" // logging tag
        private const val OAUTH_SCOPES = "read write follow push"
        private const val LOGIN_MODE = "LOGIN_MODE"
        private const val DOMAIN = "domain"
        private const val CLIENT_ID = "clientId"
        private const val CLIENT_SECRET = "clientSecret"

        const val MODE_DEFAULT = 0
        const val MODE_ADDITIONAL_LOGIN = 1

        @JvmStatic
        fun getIntent(context: Context, mode: Int): Intent {
            val loginIntent = Intent(context, LoginActivity::class.java)
            loginIntent.putExtra(LOGIN_MODE, mode)
            return loginIntent
        }

        /** Make sure the user-entered text is just a fully-qualified domain name.  */
        private fun canonicalizeDomain(domain: String): String {
            // Strip any schemes out.
            var s = domain.replaceFirst("http://", "")
            s = s.replaceFirst("https://", "")
            // If a username was included (e.g. username@example.com), just take what's after the '@'.
            val at = s.lastIndexOf('@')
            if (at != -1) {
                s = s.substring(at + 1)
            }
            return s.trim { it <= ' ' }
        }
    }
}
