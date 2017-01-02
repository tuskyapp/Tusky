package com.keylesspalace.tusky;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class FetchTimelineTask extends AsyncTask<String, Void, Boolean> {
    private Context context;
    private FetchTimelineListener fetchTimelineListener;
    private String domain;
    private String accessToken;
    private String fromId;
    private List<com.keylesspalace.tusky.Status> statuses;
    private IOException ioException;

    public FetchTimelineTask(
            Context context, FetchTimelineListener listener, String domain, String accessToken,
            String fromId) {
        super();
        this.context = context;
        fetchTimelineListener = listener;
        this.domain = domain;
        this.accessToken = accessToken;
        this.fromId = fromId;
    }

    private Date parseDate(String dateTime) {
        Date date;
        String s = dateTime.replace("Z", "+00:00");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            date = format.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return date;
    }

    private CharSequence trimTrailingWhitespace(CharSequence s) {
        int i = s.length();
        do {
            i--;
        } while (i >= 0 && Character.isWhitespace(s.charAt(i)));
        return s.subSequence(0, i + 1);
    }

    private Spanned compatFromHtml(String html) {
        Spanned result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
            /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
             * all status contents do, so it should be trimmed. */
        return (Spanned) trimTrailingWhitespace(result);
    }

    private com.keylesspalace.tusky.Status readStatus(JsonReader reader, boolean isReblog)
            throws IOException {
        JsonToken check = reader.peek();
        if (check == JsonToken.NULL) {
            reader.skipValue();
            return null;
        }
        String id = null;
        String displayName = null;
        String username = null;
        com.keylesspalace.tusky.Status reblog = null;
        String content = null;
        String avatar = null;
        Date createdAt = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "id": {
                    id = reader.nextString();
                    break;
                }
                case "account": {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        name = reader.nextName();
                        switch (name) {
                            case "acct": {
                                username = reader.nextString();
                                break;
                            }
                            case "display_name": {
                                displayName = reader.nextString();
                                break;
                            }
                            case "avatar": {
                                avatar = reader.nextString();
                                break;
                            }
                            default: {
                                reader.skipValue();
                                break;
                            }
                        }
                    }
                    reader.endObject();
                    break;
                }
                case "reblog": {
                        /* This case shouldn't be hit after the first recursion at all. But if this
                         * method is passed unusual data this check will prevent extra recursion */
                    if (!isReblog) {
                        assert(false);
                        reblog = readStatus(reader, true);
                    }
                    break;
                }
                case "content": {
                    content = reader.nextString();
                    break;
                }
                case "created_at": {
                    createdAt = parseDate(reader.nextString());
                    break;
                }
                default: {
                    reader.skipValue();
                    break;
                }
            }
        }
        reader.endObject();
        assert(username != null);
        com.keylesspalace.tusky.Status status;
        if (reblog != null) {
            status = reblog;
            status.setRebloggedByUsername(username);
        } else {
            assert(content != null);
            Spanned contentPlus = compatFromHtml(content);
            status = new com.keylesspalace.tusky.Status(
                    id, displayName, username, contentPlus, avatar, createdAt);
        }
        return status;
    }

    private String parametersToQuery(Map<String, String> parameters)
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
        String urlParameters = s.toString();
        return "?" + urlParameters;
    }

    @Override
    protected Boolean doInBackground(String... data) {
        Boolean successful = true;
        HttpsURLConnection connection = null;
        try {
            String endpoint = context.getString(R.string.endpoint_timelines_home);
            String query = "";
            if (fromId != null) {
                Map<String, String> parameters = new HashMap<>();
                if (fromId != null) {
                    parameters.put("max_id", fromId);
                }
                query = parametersToQuery(parameters);
            }
            URL url = new URL("https://" + domain + endpoint + query);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.connect();

            statuses = new ArrayList<>(20);
            JsonReader reader = new JsonReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
            reader.beginArray();
            while (reader.hasNext()) {
                statuses.add(readStatus(reader, false));
            }
            reader.endArray();
            reader.close();
        } catch (IOException e) {
            ioException = e;
            successful = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return successful;
    }

    @Override
    protected void onPostExecute(Boolean wasSuccessful) {
        super.onPostExecute(wasSuccessful);
        if (fetchTimelineListener != null) {
            if (wasSuccessful) {
                fetchTimelineListener.onFetchTimelineSuccess(statuses, fromId != null);
            } else {
                assert(ioException != null);
                fetchTimelineListener.onFetchTimelineFailure(ioException);
            }
        }
    }
}
