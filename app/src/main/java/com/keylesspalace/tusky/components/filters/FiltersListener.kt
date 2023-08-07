package com.keylesspalace.tusky.components.filters

import com.keylesspalace.tusky.core.database.model.Filter

interface FiltersListener {
    fun deleteFilter(filter: Filter)
    fun updateFilter(updatedFilter: Filter)
}
