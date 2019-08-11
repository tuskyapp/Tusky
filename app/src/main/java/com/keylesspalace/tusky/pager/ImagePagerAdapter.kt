package com.keylesspalace.tusky.pager

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.keylesspalace.tusky.SharedElementTransitionListener
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.fragment.ViewMediaFragment
import java.util.*

class ImagePagerAdapter(
        fragmentManager: FragmentManager,
        private val attachments: List<Attachment>,
        private val initialPosition: Int
) : FragmentStatePagerAdapter(fragmentManager), SharedElementTransitionListener {

    private var primaryItem: ViewMediaFragment? = null
    private var didTransition = false

    override fun setPrimaryItem(container: ViewGroup, position: Int, item: Any) {
        super.setPrimaryItem(container, position, item)
        this.primaryItem = item as ViewMediaFragment
    }

    override fun getItem(position: Int): Fragment {
        return if (position >= 0 && position < attachments.size) {
            // Fragment should not wait for or start transition if it already happened but we
            // instantiate the same fragment again, e.g. open the first photo, scroll to the
            // forth photo and then back to the first. The first fragment will trz to start the
            // transition and wait until it's over and it will never take place.
            ViewMediaFragment.newInstance(
                    attachment = attachments[position],
                    shouldStartPostponedTransition = !didTransition && position == initialPosition
            )
        } else {
            throw IllegalStateException()
        }
    }

    override fun getCount(): Int {
        return attachments.size
    }

    override fun getPageTitle(position: Int): CharSequence {
        return String.format(Locale.getDefault(), "%d/%d", position + 1, attachments.size)
    }

    override fun onTransitionEnd() {
        this.didTransition = true
        primaryItem?.onTransitionEnd()
    }
}
