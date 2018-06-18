package com.keylesspalace.tusky

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton

import com.keylesspalace.tusky.di.Injectable
import kotlinx.android.synthetic.main.about_emoji.*
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

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

        versionTextView.text = getString(R.string.about_tusky_version, BuildConfig.VERSION_NAME)

        tuskyProfileButton.setOnClickListener { onAccountButtonClick() }
        setupAboutEmoji()
    }

    private fun onAccountButtonClick() {
        viewUrl("https://mastodon.social/@Tusky")
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

    private fun setupAboutEmoji() {
        // Inflate the TextView containing the Apache 2.0 license text.
        try {
            val apacheLicense = assets.open("LICENSE_APACHE")
            val builder = StringBuilder()
            val reader = BufferedReader(
                    InputStreamReader(apacheLicense, "UTF-8"))
            var line = reader.readLine()
            while (line != null) {
                builder.append(line)
                builder.append('\n')
                line = reader.readLine()
            }
            reader.close()
            apacheView.text = builder
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Set up the button action
        val expand = findViewById<ImageButton>(R.id.about_blobmoji_expand)
        expand.setOnClickListener { v ->
            if (apacheView.visibility == View.GONE) {
                apacheView.visibility = View.VISIBLE
                (v as ImageButton).setImageResource(R.drawable.ic_arrow_drop_up_black_24dp)
            } else {
                apacheView.visibility = View.GONE
                (v as ImageButton).setImageResource(R.drawable.ic_arrow_drop_down_black_24dp)
            }
        }
    }
}
