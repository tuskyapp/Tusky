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

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

class MultipartRequest extends Request<JSONObject> {
    private static final String CHARSET = "utf-8";
    private final String boundary = "something-" + System.currentTimeMillis();

    private JSONObject parameters;
    private Response.Listener<JSONObject> listener;

    MultipartRequest(int method, String url, JSONObject parameters,
            Response.Listener<JSONObject> listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.parameters = parameters;
        this.listener = listener;
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data;boundary=" + boundary;
    }

    @Override
    public byte[] getBody() {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(byteStream);
        try {
            // Write the JSON parameters first.
            if (parameters != null) {
                stream.writeBytes(String.format("--%s\r\n", boundary));
                stream.writeBytes("Content-Disposition: form-data; name=\"parameters\"\r\n");
                stream.writeBytes(String.format(
                        "Content-Type: application/json; charset=%s\r\n", CHARSET));
                stream.writeBytes("\r\n");
                stream.writeBytes(parameters.toString());
            }

            // Write the binary data.
            DataItem data = getData();
            if (data != null) {
                stream.writeBytes(String.format("--%s\r\n", boundary));
                stream.writeBytes(String.format(
                        "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n",
                        data.name, data.filename));
                stream.writeBytes(String.format("Content-Type: %s\r\n", data.mimeType));
                stream.writeBytes(String.format("Content-Length: %s\r\n",
                        String.valueOf(data.content.length)));
                stream.writeBytes("\r\n");
                stream.write(data.content);
            }

            // Close the multipart form data.
            stream.writeBytes(String.format("--%s--\r\n", boundary));

            return byteStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers));
            return Response.success(new JSONObject(jsonString),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (JSONException|UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }
    }

    @Override
    protected void deliverResponse(JSONObject response) {
        listener.onResponse(response);
    }

    public DataItem getData() {
        return null;
    }

    static class DataItem {
        String name;
        String filename;
        String mimeType;
        byte[] content;
    }
}
