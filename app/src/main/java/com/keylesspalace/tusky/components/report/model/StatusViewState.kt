package com.keylesspalace.tusky.components.report.model

class StatusViewState {
    private val mediaShownState = HashMap<String, Boolean>()

    fun isMediaShow(id: String, isSensitive: Boolean): Boolean{
        return mediaShownState[id]?:!isSensitive
    }

    fun setMediaShow(id: String, isShow: Boolean){
        mediaShownState[id] = isShow
    }
}