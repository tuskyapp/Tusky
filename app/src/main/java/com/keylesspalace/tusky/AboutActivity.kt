package com.keylesspalace.tusky

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.databinding.ActivityAboutBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.util.NoUnderlineURLSpan
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import javax.inject.Inject
import kotlinx.coroutines.launch

class AboutActivity : BottomSheetActivity(), Injectable {
    @Inject
    lateinit var instanceInfoRepository: InstanceInfoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(R.string.about_title_activity)

        binding.versionTextView.text = getString(R.string.about_app_version, getString(R.string.app_name), BuildConfig.VERSION_NAME)

        binding.deviceInfo.text = getString(
            R.string.about_device_info,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT
        )

        lifecycleScope.launch {
            accountManager.activeAccount?.let { account ->
                val instanceInfo = instanceInfoRepository.getInstanceInfo()
                binding.accountInfo.text = getString(
                    R.string.about_account_info,
                    account.username,
                    account.domain,
                    instanceInfo.version
                )
                binding.accountInfoTitle.show()
                binding.accountInfo.show()
            }
        }

        if (BuildConfig.CUSTOM_INSTANCE.isBlank()) {
            binding.aboutPoweredByTusky.hide()
        }

        binding.aboutLicenseInfoTextView.setClickableTextWithoutUnderlines(
            R.string.about_tusky_license
        )
        binding.aboutWebsiteInfoTextView.setClickableTextWithoutUnderlines(
            R.string.about_project_site
        )
        binding.aboutBugsFeaturesInfoTextView.setClickableTextWithoutUnderlines(
            R.string.about_bug_feature_request_site
        )

        binding.tuskyProfileButton.setOnClickListener {
            viewUrl(BuildConfig.SUPPORT_ACCOUNT_URL)
        }

        binding.aboutLicensesButton.setOnClickListener {
            startActivityWithSlideInAnimation(Intent(this, LicenseActivity::class.java))
        }

        binding.copyDeviceInfo.setOnClickListener {
            val text = "${binding.versionTextView.text}\n\nDevice:\n\n${binding.deviceInfo.text}\n\nAccount:\n\n${binding.accountInfo.text}"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Tusky version information", text)
            clipboard.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                Toast.makeText(this, getString(R.string.about_copied), Toast.LENGTH_SHORT).show()
            }
        }
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

        val customSpan = NoUnderlineURLSpan(span.url)

        builder.removeSpan(span)
        builder.setSpan(customSpan, start, end, flags)
    }

    setText(builder)
    linksClickable = true
    movementMethod = LinkMovementMethod.getInstance()
}
