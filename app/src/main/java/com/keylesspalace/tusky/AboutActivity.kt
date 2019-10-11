package com.keylesspalace.tusky

import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.MenuItem
import android.widget.TextView
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.CustomURLSpan
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

        if(BuildConfig.CUSTOM_INSTANCE.isBlank()) {
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
