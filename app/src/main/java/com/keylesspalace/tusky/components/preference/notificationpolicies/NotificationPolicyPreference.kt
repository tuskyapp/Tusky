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
