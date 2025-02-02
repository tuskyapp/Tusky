package com.keylesspalace.tusky.components.preference

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat

abstract class BasePreferencesFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(listView) { listView, insets ->
            val systemBarsInsets = insets.getInsets(systemBars())
            listView.updatePadding(bottom = systemBarsInsets.bottom)
            insets.inset(0, 0, 0, systemBarsInsets.bottom)
        }
    }
}
