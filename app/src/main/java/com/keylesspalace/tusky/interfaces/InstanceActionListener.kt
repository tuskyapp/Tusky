package com.keylesspalace.tusky.interfaces

interface InstanceActionListener {
    fun mute(mute: Boolean, instance: String, position: Int)
}