package com.keylesspalace.tusky.util

import android.util.Log
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

class GoogleTranslator @Inject constructor(private val httpClient: OkHttpClient) {
    private val url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t"

    var gson = Gson()

    fun translate(query: String, from: String = "auto", to: String = "en"): String {
        var result = query
        val httpUrl = url.toHttpUrl().newBuilder()
                .addQueryParameter("sl", from)
                .addQueryParameter("tl", to)
                .addQueryParameter("dj", "1")
                .addQueryParameter("q", query)
                .build()

        val request = Request.Builder().url(httpUrl).build()
        val call = httpClient.newCall(request)
        val response = call.execute()
        return response.body?.string()?.let { responseText ->
            val fromJson = gson.fromJson(responseText, Sentences::class.java)
            fromJson.sentences.forEach {
                result = result.replace(it.orig, it.trans)
            }
            Log.d("GOOGLE_TRANSLATE", result)
            return result
        }.toString()
    }
}

data class Sentences(val sentences: List<Translates>)

data class Translates(val trans: String, val orig: String)