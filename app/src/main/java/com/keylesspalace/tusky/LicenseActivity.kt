/* Copyright 2018 Conny Duck
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

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.databinding.ActivityLicenseBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

@AndroidEntryPoint
class LicenseActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(R.string.title_licenses)

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { scrollView, insets ->
            val systemBarInsets = insets.getInsets(systemBars())
            scrollView.updatePadding(bottom = systemBarInsets.bottom)

            insets.inset(0, 0, 0, systemBarInsets.bottom)
        }

        loadFileIntoTextView(R.raw.apache, binding.licenseApacheTextView)
    }

    private fun loadFileIntoTextView(@RawRes fileId: Int, textView: TextView) {
        lifecycleScope.launch {
            textView.text = withContext(Dispatchers.IO) {
                try {
                    resources.openRawResource(fileId).source().buffer().use { it.readUtf8() }
                } catch (e: IOException) {
                    Log.w("LicenseActivity", e)
                    ""
                }
            }
        }
    }
}
