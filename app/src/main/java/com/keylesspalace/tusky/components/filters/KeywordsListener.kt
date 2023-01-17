package com.keylesspalace.tusky.components.filters

import com.keylesspalace.tusky.entity.FilterKeyword

interface KeywordsListener {
    fun addKeyword(keyword: FilterKeyword)
    fun showEditKeywordUI(keyword: FilterKeyword)
    fun modifyKeyword(original: FilterKeyword, updated: FilterKeyword)
    fun deleteKeyword(keyword: FilterKeyword)
}