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

package com.keylesspalace.tusky

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.customtabs.CustomTabsIntent
import android.support.v7.app.AppCompatActivity
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.keylesspalace.tusky.entity.AccessToken
import com.keylesspalace.tusky.entity.AppCredentials
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.CustomTabsHelper
import com.keylesspalace.tusky.util.OkHttpUtils
import com.keylesspalace.tusky.util.ThemeUtils
import kotlinx.android.synthetic.main.activity_login.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class LoginActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private var domain: String = ""
    private var clientId: String? = null
    private var clientSecret: String? = null

    private val oauthRedirectUri: String
        get() {
            val scheme = getString(R.string.oauth_scheme)
            val host = BuildConfig.APPLICATION_ID
            return "$scheme://$host/"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = preferences.getString("appTheme", TuskyApplication.APP_THEME_DEFAULT)
        ThemeUtils.setAppNightMode(theme)

        setContentView(R.layout.activity_login)

        if (savedInstanceState != null) {
            domain = savedInstanceState.getString(DOMAIN)
            clientId = savedInstanceState.getString(CLIENT_ID)
            clientSecret = savedInstanceState.getString(CLIENT_SECRET)
        }

        preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE)

        loginButton.setOnClickListener { onButtonClick() }

        whatsAnInstanceTextView.setOnClickListener {
            val dialog = AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_whats_an_instance)
                    .setPositiveButton(R.string.action_close, null)
                    .show()
            val textView = dialog.findViewById<TextView>(android.R.id.message)
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        if(isAdditionalLogin()) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } else {
            toolbar.visibility = View.GONE
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(DOMAIN, domain)
        outState.putString(CLIENT_ID, clientId)
        outState.putString(CLIENT_SECRET, clientSecret)
        super.onSaveInstanceState(outState)
    }

    private fun getApiFor(domain: String): MastodonApi {
        val retrofit = Retrofit.Builder()
                .baseUrl("https://" + domain)
                .client(OkHttpUtils.getCompatibleClient(preferences))
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        return retrofit.create(MastodonApi::class.java)
    }

    /**
     * Obtain the oauth client credentials for this app. This is only necessary the first time the
     * app is run on a given server instance. So, after the first authentication, they are
     * saved in SharedPreferences and every subsequent run they are simply fetched from there.
     */
    private fun onButtonClick() {

        loginButton.isEnabled = false

        domain = validateDomain(domainEditText.text.toString())

        val callback = object : Callback<AppCredentials> {
            override fun onResponse(call: Call<AppCredentials>,
                                    response: Response<AppCredentials>) {
                if (!response.isSuccessful) {
                    loginButton.isEnabled = true
                    domainEditText.error = getString(R.string.error_failed_app_registration)
                    Log.e(TAG, "App authentication failed. " + response.message())
                    return
                }
                val credentials = response.body()
                clientId = credentials!!.clientId
                clientSecret = credentials.clientSecret

                redirectUserToAuthorizeAndLogin(domainEditText)
            }

            override fun onFailure(call: Call<AppCredentials>, t: Throwable) {
                loginButton.isEnabled = true
                domainEditText.error = getString(R.string.error_failed_app_registration)
                setLoading(false)
                Log.e(TAG, Log.getStackTraceString(t))
            }
        }

        try {
            getApiFor(domain)
                    .authenticateApp(getString(R.string.app_name), oauthRedirectUri,
                            OAUTH_SCOPES, getString(R.string.app_website))
                    .enqueue(callback)
            setLoading(true)
        } catch (e: IllegalArgumentException) {
            setLoading(false)
            domainEditText.error = getString(R.string.error_invalid_domain)
        }

    }

    private fun redirectUserToAuthorizeAndLogin(editText: EditText) {
        /* To authorize this app and log in it's necessary to redirect to the domain given,
         * activity_login there, and the server will redirect back to the app with its response. */
        val endpoint = MastodonApi.ENDPOINT_AUTHORIZE
        val redirectUri = oauthRedirectUri
        val parameters = HashMap<String, String>()
        parameters["client_id"] = clientId!!
        parameters["redirect_uri"] = redirectUri
        parameters["response_type"] = "code"
        parameters["scope"] = OAUTH_SCOPES
        val url = "https://" + domain + endpoint + "?" + toQueryString(parameters)
        val uri = Uri.parse(url)
        if (!openInCustomTab(uri, this)) {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri)
            if (viewIntent.resolveActivity(packageManager) != null) {
                startActivity(viewIntent)
            } else {
                editText.error = getString(R.string.error_no_web_browser_found)
                setLoading(false)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        preferences.edit()
                .putString("domain", domain)
                .putString("clientId", clientId)
                .putString("clientSecret", clientSecret)
                .apply()
    }

    override fun onStart() {
        super.onStart()
        /* Check if we are resuming during authorization by seeing if the intent contains the
         * redirect that was given to the server. If so, its response is here! */
        val uri = intent.data
        val redirectUri = oauthRedirectUri

        if (uri != null && uri.toString().startsWith(redirectUri)) {
            // This should either have returned an authorization code or an error.
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            if (code != null) {
                /* During the redirect roundtrip this Activity usually dies, which wipes out the
                 * instance variables, so they have to be recovered from where they were saved in
                 * SharedPreferences. */
                domain = preferences.getString(DOMAIN, null)
                clientId = preferences.getString(CLIENT_ID, null)
                clientSecret = preferences.getString(CLIENT_SECRET, null)

                setLoading(true)
                /* Since authorization has succeeded, the final step to log in is to exchange
                 * the authorization code for an access token. */
                val callback = object : Callback<AccessToken> {
                    override fun onResponse(call: Call<AccessToken>, response: Response<AccessToken>) {
                        if (response.isSuccessful) {
                            onLoginSuccess(response.body()!!.accessToken)
                        } else {
                            setLoading(false)
                            domainEditText.error = getString(R.string.error_retrieving_oauth_token)
                            Log.e(TAG, String.format("%s %s",
                                    getString(R.string.error_retrieving_oauth_token),
                                    response.message()))
                        }
                    }

                    override fun onFailure(call: Call<AccessToken>, t: Throwable) {
                        setLoading(false)
                        domainEditText.error = getString(R.string.error_retrieving_oauth_token)
                        Log.e(TAG, String.format("%s %s",
                                getString(R.string.error_retrieving_oauth_token),
                                t.message))
                    }
                }

                getApiFor(domain).fetchOAuthToken(clientId, clientSecret, redirectUri, code,
                        "authorization_code").enqueue(callback)
            } else if (error != null) {
                /* Authorization failed. Put the error response where the user can read it and they
                 * can try again. */
                setLoading(false)
                domainEditText.error = getString(R.string.error_authorization_denied)
                Log.e(TAG, String.format("%s %s",
                        getString(R.string.error_authorization_denied),
                        error))
            } else {
                // This case means a junk response was received somehow.
                setLoading(false)
                domainEditText.error = getString(R.string.error_authorization_unknown)
            }
        } else {
            // first show or user cancelled login
            setLoading(false)
        }
    }

    private fun setLoading(loadingState: Boolean) {
        if (loadingState) {
            loginLoadingLayout.visibility = View.VISIBLE
            loginInputLayout.visibility = View.GONE
        } else {
            loginLoadingLayout.visibility = View.GONE
            loginInputLayout.visibility = View.VISIBLE
            loginButton.isEnabled = true
        }
    }

    private fun isAdditionalLogin() : Boolean {
        return intent.getBooleanExtra(LOGIN_MODE, false)
    }

    private fun onLoginSuccess(accessToken: String) {

        setLoading(true)

        TuskyApplication.getAccountManager().addAccount(accessToken, domain)

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "LoginActivity" // logging tag
        private const val OAUTH_SCOPES = "read write follow"
        private const val LOGIN_MODE = "LOGIN_MODE"
        private const val DOMAIN = "domain"
        private const val CLIENT_ID = "clientId"
        private const val CLIENT_SECRET = "clientSecret"

        @JvmStatic
        fun getIntent(context: Context, mode: Boolean): Intent {
            val loginIntent = Intent(context, LoginActivity::class.java)
            loginIntent.putExtra(LOGIN_MODE, mode)
            return loginIntent
        }

        /** Make sure the user-entered text is just a fully-qualified domain name.  */
        private fun validateDomain(domain: String): String {
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

        /**
         * Chain together the key-value pairs into a query string, for either appending to a URL or
         * as the content of an HTTP request.
         */
        private fun toQueryString(parameters: Map<String, String>): String {
            val s = StringBuilder()
            var between = ""
            for ((key, value) in parameters) {
                s.append(between)
                s.append(Uri.encode(key))
                s.append("=")
                s.append(Uri.encode(value))
                between = "&"
            }
            return s.toString()
        }

        private fun openInCustomTab(uri: Uri, context: Context): Boolean {

            val toolbarColor = ThemeUtils.getColorById(context, "custom_tab_toolbar")
            val builder = CustomTabsIntent.Builder()
            builder.setToolbarColor(toolbarColor)
            val customTabsIntent = builder.build()
            try {
                val packageName = CustomTabsHelper.getPackageNameToUse(context)
                /* If we cant find a package name, it means theres no browser that supports
                 * Chrome Custom Tabs installed. So, we fallback to the webview */
                if (packageName == null) {
                    return false
                } else {
                    customTabsIntent.intent.`package` = packageName
                    customTabsIntent.launchUrl(context, uri)
                }
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Activity was not found for intent, " + customTabsIntent.toString())
                return false
            }

            return true
        }
    }
}
