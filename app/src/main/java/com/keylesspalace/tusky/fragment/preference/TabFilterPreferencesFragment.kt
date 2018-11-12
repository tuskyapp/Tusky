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

package com.keylesspalace.tusky.fragment.preference

import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.PreferenceFragmentCompat
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.keylesspalace.tusky.R
import java.util.regex.Pattern

class TabFilterPreferencesFragment : PreferenceFragmentCompat() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.timeline_filter_preferences)

        sharedPreferences = preferenceManager.sharedPreferences

        val regexPref = findPreference("tabFilterRegex")
        if (regexPref != null) {

            regexPref.summary = sharedPreferences.getString("tabFilterRegex", "")
            regexPref.setOnPreferenceClickListener {

                val editText = EditText(requireContext())
                editText.setText(sharedPreferences.getString("tabFilterRegex", ""))

                val dialog = AlertDialog.Builder(requireContext())
                        .setTitle(R.string.pref_title_filter_regex)
                        .setView(editText)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            sharedPreferences
                                    .edit()
                                    .putString("tabFilterRegex", editText.text.toString())
                                    .apply()
                            regexPref.summary = editText.text.toString()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()

                editText.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(newRegex: Editable) {
                        try {
                            Pattern.compile(newRegex.toString())
                            editText.error = null
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        } catch (e: IllegalArgumentException) {
                            editText.error = getString(R.string.error_invalid_regex)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        }
                    }

                    override fun beforeTextChanged(s1: CharSequence, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s1: CharSequence, start: Int, before: Int, count: Int) {}
                })
                dialog.show()
                true
            }
        }

    }

    companion object {

        fun newInstance(): TabFilterPreferencesFragment {
            return TabFilterPreferencesFragment()
        }
    }
}
