package com.keylesspalace.tusky.components.report.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.keylesspalace.tusky.components.report.fragments.ReportDoneFragment
import com.keylesspalace.tusky.components.report.fragments.ReportNoteFragment
import com.keylesspalace.tusky.components.report.fragments.ReportStatusesFragment

class ReportPagerAdapter(manager: FragmentManager) : FragmentPagerAdapter(manager) {
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> ReportStatusesFragment.newInstance()
            1 -> ReportNoteFragment.newInstance()
            2 -> ReportDoneFragment.newInstance()
            else -> throw IllegalArgumentException("Unknown page index: $position")
        }
    }

    override fun getCount(): Int = 3
}