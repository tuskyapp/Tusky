/* Copyright 2018 Conny Duck
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.core.content.res.use
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.CardLicenseBinding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.openLink

class LicenseCard
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    init {
        val binding = CardLicenseBinding.inflate(LayoutInflater.from(context), this)

        setCardBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.BLACK))

        val (name, license, link) = context.theme.obtainStyledAttributes(
            attrs, R.styleable.LicenseCard, 0, 0
        ).use { a ->
            Triple(
                a.getString(R.styleable.LicenseCard_name),
                a.getString(R.styleable.LicenseCard_license),
                a.getString(R.styleable.LicenseCard_link),
            )
        }

        binding.licenseCardName.text = name
        binding.licenseCardLicense.text = license
        if (link.isNullOrBlank()) {
            binding.licenseCardLink.hide()
        } else {
            binding.licenseCardLink.text = link
            setOnClickListener { context.openLink(link) }
        }
    }
}
