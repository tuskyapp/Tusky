package com.keylesspalace.tusky.interfaces

import com.google.android.material.tabs.TabLayout

/**
 * Implement this interface if Activity has tabs and it allows to fragments to have access to this tabs.
 * For example - to implement scroll to top on click
 */
interface TabbedActivity {
    /**
     * Returns the tab layout or null if it is not inflated or absent
     */
    fun getTabLayout(): TabLayout?
}