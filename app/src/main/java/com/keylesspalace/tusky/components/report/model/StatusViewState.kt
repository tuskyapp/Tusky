package com.keylesspalace.tusky.components.report.model

class StatusViewState {
    private val mediaShownState = HashMap<String, Boolean>()
    private val contentShownState = HashMap<String, Boolean>()
    private val longContentCollapsedState = HashMap<String, Boolean>()

    fun isMediaShow(id: String, isSensitive: Boolean): Boolean = isStateEnabled(mediaShownState, id, !isSensitive)
    fun setMediaShow(id: String, isShow: Boolean) = setStateEnabled(mediaShownState, id, isShow)

    fun isContentShow(id: String, isSensitive: Boolean): Boolean = isStateEnabled(contentShownState, id, !isSensitive)
    fun setContentShow(id: String, isShow: Boolean) = setStateEnabled(contentShownState, id, isShow)

    fun isCollapsed(id: String, isCollapsed: Boolean): Boolean = isStateEnabled(longContentCollapsedState, id, isCollapsed)
    fun setCollapsed(id: String, isCollapsed: Boolean) = setStateEnabled(longContentCollapsedState, id, isCollapsed)

    private fun isStateEnabled(map: Map<String, Boolean>, id: String, def: Boolean): Boolean = map[id]
            ?: def

    private fun setStateEnabled(map: MutableMap<String, Boolean>, id: String, state: Boolean) = map.put(id, state)
}