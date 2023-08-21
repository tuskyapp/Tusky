package com.keylesspalace.tusky.components.filters

import com.keylesspalace.tusky.entity.Filter

interface FiltersListener {
    fun deleteFilter(filter: Filter)
    fun updateFilter(updatedFilter: Filter)
}
