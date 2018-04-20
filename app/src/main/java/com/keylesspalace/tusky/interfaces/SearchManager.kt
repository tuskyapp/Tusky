package com.keylesspalace.tusky.interfaces

interface SearchManager {
    fun onBeginSearch(url: String)
    fun onEndSearch(url: String)
    fun getCancelSearchRequested(url: String): Boolean
    fun getIsSearching(): Boolean
    fun cancelActiveSearch()
}