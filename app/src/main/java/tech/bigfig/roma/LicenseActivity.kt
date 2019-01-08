/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma

import android.os.Bundle
import androidx.annotation.RawRes
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import tech.bigfig.roma.util.IOUtils
import kotlinx.android.extensions.CacheImplementation
import kotlinx.android.extensions.ContainerOptions
import kotlinx.android.synthetic.main.activity_license.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class LicenseActivity : BaseActivity() {

    @ContainerOptions(cache = CacheImplementation.NO_CACHE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setTitle(R.string.title_licenses)

        loadFileIntoTextView(R.raw.apache, licenseApacheTextView)

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

    private fun loadFileIntoTextView(@RawRes fileId: Int, textView: TextView) {

        val sb = StringBuilder()

        val br = BufferedReader(InputStreamReader(resources.openRawResource(fileId)))

        try {
            var line: String? = br.readLine()
            while (line != null) {
                sb.append(line)
                sb.append('\n')
                line = br.readLine()
            }
        } catch (e: IOException) {
            Log.w("LicenseActivity", e)
        }

        IOUtils.closeQuietly(br)

        textView.text = sb.toString()

    }

}
