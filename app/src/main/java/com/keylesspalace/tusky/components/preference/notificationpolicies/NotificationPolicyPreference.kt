/* Copyright 2024 Tusky Contributors
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

package com.keylesspalace.tusky.components.preference.notificationpolicies

import android.content.Context
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.settings.PreferenceParent

class NotificationPolicyPreference(
    context: Context
) : ListPreference(context) {

    init {
        widgetLayoutResource = R.layout.preference_notification_policy
        setEntries(R.array.notification_policy_options)
        setEntryValues(R.array.notification_policy_value)
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val switchView: TextView = holder.findViewById(R.id.notification_policy_value) as TextView
        switchView.text = entries.getOrNull(findIndexOfValue(value))
    }
}

inline fun PreferenceParent.notificationPolicyPreference(builder: NotificationPolicyPreference.() -> Unit): NotificationPolicyPreference {
    val pref = NotificationPolicyPreference(context)
    builder(pref)
    addPref(pref)
    return pref
}
