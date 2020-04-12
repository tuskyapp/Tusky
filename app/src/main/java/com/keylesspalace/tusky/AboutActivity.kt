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

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MenuItem
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.CustomURLSpan
import com.keylesspalace.tusky.util.LogReader
import com.keylesspalace.tusky.util.hide
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.android.synthetic.main.toolbar_basic.*

class AboutActivity : BottomSheetActivity(), Injectable {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(R.string.about_title_activity)

        versionTextView.text = getString(R.string.about_app_version, getString(R.string.app_name), BuildConfig.VERSION_NAME)

        if (BuildConfig.CUSTOM_INSTANCE.isBlank()) {
            aboutPoweredByTusky.hide()
        }

        aboutLicenseInfoTextView.setClickableTextWithoutUnderlines(R.string.about_tusky_license)
        aboutWebsiteInfoTextView.setClickableTextWithoutUnderlines(R.string.about_project_site)
        aboutBugsFeaturesInfoTextView.setClickableTextWithoutUnderlines(R.string.about_bug_feature_request_site)

        tuskyProfileButton.setOnClickListener {
            viewUrl(BuildConfig.SUPPORT_ACCOUNT_URL)
        }

        aboutLicensesButton.setOnClickListener {
            startActivityWithSlideInAnimation(Intent(this, LicenseActivity::class.java))
        }

        sendLogsButton.setOnClickListener {
            sendLogs()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun sendLogs() {
        val logFileUri = LogReader.getLogFile(this)
        val shareFileUri = FileProvider.getUriForFile(applicationContext,
                "${BuildConfig.APPLICATION_ID}.fileprovider", logFileUri.toFile())

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareFileUri)
            type = "text/plain"
        }

        startActivity(Intent.createChooser(sendIntent,
                getText(R.string.action_send_logs)))
    }
}

private fun TextView.setClickableTextWithoutUnderlines(@StringRes textId: Int) {

    val text = SpannableString(context.getText(textId))

    Linkify.addLinks(text, Linkify.WEB_URLS)

    val builder = SpannableStringBuilder(text)
    val urlSpans = text.getSpans(0, text.length, URLSpan::class.java)
    for (span in urlSpans) {
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val flags = builder.getSpanFlags(span)

        val customSpan = object : CustomURLSpan(span.url) {}

        builder.removeSpan(span)
        builder.setSpan(customSpan, start, end, flags)
    }

    setText(builder)
    linksClickable = true
    movementMethod = LinkMovementMethod.getInstance()

}
