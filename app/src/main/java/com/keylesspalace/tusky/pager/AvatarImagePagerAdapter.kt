package com.keylesspalace.tusky.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.keylesspalace.tusky.SharedElementTransitionListener
import com.keylesspalace.tusky.fragment.ViewMediaFragment

class AvatarImagePagerAdapter(fragmentManager: FragmentManager, private val avatarUrl: String) : FragmentPagerAdapter(fragmentManager), SharedElementTransitionListener {
    override fun getItem(position: Int): Fragment {
        return if (position == 0) {
            ViewMediaFragment.newAvatarInstance(avatarUrl)
        } else {
            throw IllegalStateException()
        }
    }

    override fun getCount() = 1

    override fun onTransitionEnd() {
    }
}
