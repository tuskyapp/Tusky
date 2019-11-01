package com.keylesspalace.tusky.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.keylesspalace.tusky.ViewMediaAdapter
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.fragment.ViewMediaFragment
import java.lang.ref.WeakReference

class ImagePagerAdapter(
        activity: FragmentActivity,
        private val attachments: List<Attachment>,
        private val initialPosition: Int
) : ViewMediaAdapter(activity) {

    private var didTransition = false
    private val fragments = MutableList<WeakReference<ViewMediaFragment>?>(attachments.size) { null }

    override fun getItemCount() = attachments.size

    override fun createFragment(position: Int): Fragment {
        if (position >= 0 && position < attachments.size) {
            // Fragment should not wait for or start transition if it already happened but we
            // instantiate the same fragment again, e.g. open the first photo, scroll to the
            // forth photo and then back to the first. The first fragment will try to start the
            // transition and wait until it's over and it will never take place.
            val fragment = ViewMediaFragment.newInstance(
                    attachment = attachments[position],
                    shouldStartPostponedTransition = !didTransition && position == initialPosition
            )
            fragments[position] = WeakReference(fragment)
            return fragment
        } else {
            throw IllegalStateException()
        }
    }

   override fun onTransitionEnd(position: Int) {
        this.didTransition = true
        fragments[position]?.get()?.onTransitionEnd()
    }
}
