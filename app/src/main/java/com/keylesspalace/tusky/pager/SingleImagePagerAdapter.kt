package com.keylesspalace.tusky.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.keylesspalace.tusky.ViewMediaAdapter
import com.keylesspalace.tusky.fragment.ViewMediaFragment

class SingleImagePagerAdapter(
    activity: FragmentActivity,
    private val imageUrl: String
) : ViewMediaAdapter(activity) {

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) {
            ViewMediaFragment.newSingleImageInstance(imageUrl)
        } else {
            throw IllegalStateException()
        }
    }

    override fun getItemCount() = 1

    override fun onTransitionEnd(position: Int) {
    }
}
