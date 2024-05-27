package com.keylesspalace.tusky.interfaces

interface HashtagActionListener {
    fun unfollow(tagName: String, position: Int)
    fun view(tagName: String)
    fun copyTagName(tagName: String)
}
