/* Copyright 2017 Andrew Dawson
 *
 * This file is part of Tusky.
 *
 * Tusky is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky. If not, see
 * <http://www.gnu.org/licenses/>. */

package com.keylesspalace.tusky;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";
    private static String OAUTH_SCOPES = "read write follow";

    private SharedPreferences preferences;
    private String domain;
    private String clientId;
    private String clientSecret;

    /**
     * Chain together the key-value pairs into a query string, for either appending to a URL or
     * as the content of an HTTP request.
     */
    private String toQueryString(Map<String, String> parameters)
            throws UnsupportedEncodingException {
        StringBuilder s = new StringBuilder();
        String between = "";
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            s.append(between);
            s.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            s.append("=");
            s.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            between = "&";
        }
        return s.toString();
    }

    /** Make sure the user-entered text is just a fully-qualified domain name. */
    private String validateDomain(String s) {
        s = s.replaceFirst("http://", "");
        s = s.replaceFirst("https://", "");
        return s;
    }

    private String getOauthRedirectUri() {
        String scheme = getString(R.string.oauth_scheme);
        String host = getString(R.string.oauth_redirect_host);
        return scheme + "://" + host + "/";
    }

    private void redirectUserToAuthorizeAndLogin() {
        /* To authorize this app and log in it's necessary to redirect to the domain given,
         * activity_login there, and the server will redirect back to the app with its response. */
        String endpoint = getString(R.string.endpoint_authorize);
        String redirectUri = getOauthRedirectUri();
        Map<String, String> parameters = new HashMap<>();
        parameters.put("client_id", clientId);
        parameters.put("redirect_uri", redirectUri);
        parameters.put("response_type", "code");
        parameters.put("scope", OAUTH_SCOPES);
        String queryParameters;
        try {
            queryParameters = toQueryString(parameters);
        } catch (UnsupportedEncodingException e) {
            //TODO: No clue how to handle this error case??
            Log.e(TAG, "Was not able to build the authorization URL.");
            return;
        }
        String url = "https://" + domain + endpoint + "?" + queryParameters;
        Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse(url));
        startActivity(viewIntent);
    }

    /**
     * Obtain the oauth client credentials for this app. This is only necessary the first time the
     * app is run on a given server instance. So, after the first authentication, they are
     * saved in SharedPreferences and every subsequent run they are simply fetched from there.
     */
    private void onButtonClick(final EditText editText) {
        domain = validateDomain(editText.getText().toString());
        /* Attempt to get client credentials from SharedPreferences, and if not present
         * (such as in the case that the domain has never been accessed before)
         * authenticate with the server and store the received credentials to use next
         * time. */
        clientId = preferences.getString(domain + "/client_id", null);
        clientSecret = preferences.getString(domain + "/client_secret", null);
        if (clientId != null && clientSecret != null) {
            redirectUserToAuthorizeAndLogin();
        } else {
            String endpoint = getString(R.string.endpoint_apps);
            String url = "https://" + domain + endpoint;
            JSONObject parameters = new JSONObject();
            try {
                parameters.put("client_name", getString(R.string.app_name));
                parameters.put("redirect_uris", getOauthRedirectUri());
                parameters.put("scopes", OAUTH_SCOPES);
                parameters.put("website", getString(R.string.app_website));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to build the form data for the authentication request.");
                return;
            }
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, url, parameters,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                clientId = response.getString("client_id");
                                clientSecret = response.getString("client_secret");
                            } catch (JSONException e) {
                                Log.e(TAG, "Couldn't get data from the authentication response.");
                                return;
                            }
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString(domain + "/client_id", clientId);
                            editor.putString(domain + "/client_secret", clientSecret);
                            editor.apply();
                            redirectUserToAuthorizeAndLogin();
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            editText.setError(
                                    "This app could not obtain authentication from that server " +
                                    "instance.");
                            error.printStackTrace();
                        }
                    });
            VolleySingleton.getInstance(this).addToRequestQueue(request);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        Button button = (Button) findViewById(R.id.button_login);
        final EditText editText = (EditText) findViewById(R.id.edit_text_domain);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonClick(editText);
            }
        });
        TextView noAccount = (TextView) findViewById(R.id.no_account);
        final Context context = this;
        noAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setMessage(R.string.dialog_no_account)
                        .setPositiveButton(R.string.action_close,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                        .show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("domain", domain);
        editor.putString("clientId", clientId);
        editor.putString("clientSecret", clientSecret);
        editor.commit();
    }

    private void onLoginSuccess(String accessToken) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("accessToken", accessToken);
        editor.apply();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* Check if we are resuming during authorization by seeing if the intent contains the
         * redirect that was given to the server. If so, its response is here! */
        Uri uri = getIntent().getData();
        String redirectUri = getOauthRedirectUri();
        if (uri != null && uri.toString().startsWith(redirectUri)) {
            // This should either have returned an authorization code or an error.
            String code = uri.getQueryParameter("code");
            String error = uri.getQueryParameter("error");
            final TextView errorText = (TextView) findViewById(R.id.text_error);
            if (code != null) {
                /* During the redirect roundtrip this Activity usually dies, which wipes out the
                 * instance variables, so they have to be recovered from where they were saved in
                 * SharedPreferences. */
                domain = preferences.getString("domain", null);
                clientId = preferences.getString("clientId", null);
                clientSecret = preferences.getString("clientSecret", null);
                /* Since authorization has succeeded, the final step to log in is to exchange
                 * the authorization code for an access token. */
                JSONObject parameters = new JSONObject();
                try {
                    parameters.put("client_id", clientId);
                    parameters.put("client_secret", clientSecret);
                    parameters.put("redirect_uri", redirectUri);
                    parameters.put("code", code);
                    parameters.put("grant_type", "authorization_code");
                } catch (JSONException e) {
                    errorText.setText("Heck.");
                    //TODO: I don't even know how to handle this error state.
                }
                String endpoint = getString(R.string.endpoint_token);
                String url = "https://" + domain + endpoint;
                JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, url, parameters,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            String accessToken = "";
                            try {
                                accessToken = response.getString("access_token");
                            } catch(JSONException e) {
                                errorText.setText("Heck.");
                                //TODO: I don't even know how to handle this error state.
                            }
                            onLoginSuccess(accessToken);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            errorText.setText(error.getMessage());
                        }
                    });
                VolleySingleton.getInstance(this).addToRequestQueue(request);
            } else if (error != null) {
                /* Authorization failed. Put the error response where the user can read it and they
                 * can try again. */
                errorText.setText(error);
            } else {
                // This case means a junk response was received somehow.
                errorText.setText(getString(R.string.error_authorization_unknown));
            }
        }
    }
}
