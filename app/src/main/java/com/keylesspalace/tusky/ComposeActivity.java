package com.keylesspalace.tusky;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ComposeActivity extends AppCompatActivity {
    private static int STATUS_CHARACTER_LIMIT = 500;

    private String domain;
    private String accessToken;
    private EditText textEditor;

    private void onSendSuccess() {
        Toast.makeText(this, "Toot!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onSendFailure(Exception exception) {
        textEditor.setError(getString(R.string.error_sending_status));
    }

    private void sendStatus(String content, String visibility) {
        String endpoint = getString(R.string.endpoint_status);
        String url = "https://" + domain + endpoint;
        JSONObject parameters = new JSONObject();
        try {
            parameters.put("status", content);
            parameters.put("visibility", visibility);
        } catch (JSONException e) {
            onSendFailure(e);
            return;
        }
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, parameters,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        onSendSuccess();
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onSendFailure(error);
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };
        VolleySingleton.getInstance(this).addToRequestQueue(request);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        SharedPreferences preferences = getSharedPreferences(
                getString(R.string.preferences_file_key), Context.MODE_PRIVATE);
        domain = preferences.getString("domain", null);
        accessToken = preferences.getString("accessToken", null);
        assert(domain != null);
        assert(accessToken != null);

        textEditor = (EditText) findViewById(R.id.field_status);
        final TextView charactersLeft = (TextView) findViewById(R.id.characters_left);
        TextWatcher textEditorWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int left = STATUS_CHARACTER_LIMIT - s.length();
                charactersLeft.setText(Integer.toString(left));
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        };
        textEditor.addTextChangedListener(textEditorWatcher);

        final RadioGroup radio = (RadioGroup) findViewById(R.id.radio_visibility);
        final Button sendButton = (Button) findViewById(R.id.button_send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Editable editable = textEditor.getText();
                if (editable.length() <= STATUS_CHARACTER_LIMIT) {
                    int id = radio.getCheckedRadioButtonId();
                    String visibility;
                    switch (id) {
                        default:
                        case R.id.radio_public: {
                            visibility = "public";
                            break;
                        }
                        case R.id.radio_unlisted: {
                            visibility = "unlisted";
                            break;
                        }
                        case R.id.radio_private: {
                            visibility = "private";
                            break;
                        }
                    }
                    sendStatus(editable.toString(), visibility);
                } else {
                    textEditor.setError(getString(R.string.error_compose_character_limit));
                }
            }
        });
    }
}
