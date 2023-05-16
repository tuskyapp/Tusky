/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package app.tusky.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope.Companion.RESOURCE_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

/**
 * Detect the use of "raw" dimension values (like `16dp` or `12sp`) in layout attributes
 * where `@dimen/...` resources or `?attr/...` references should be used instead.
 */
class RawDimensionDetector : LayoutDetector() {
    override fun getApplicableAttributes(): Collection<String> {
        return listOf(
            // Margin
            SdkConstants.ATTR_LAYOUT_MARGIN,
            SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
            SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
            SdkConstants.ATTR_LAYOUT_MARGIN_START,
            SdkConstants.ATTR_LAYOUT_MARGIN_END,
            SdkConstants.ATTR_LAYOUT_MARGIN_HORIZONTAL,
            SdkConstants.ATTR_LAYOUT_MARGIN_VERTICAL,
            SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
            SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
            // Padding
            SdkConstants.ATTR_PADDING,
            SdkConstants.ATTR_PADDING_TOP,
            SdkConstants.ATTR_PADDING_BOTTOM,
            SdkConstants.ATTR_PADDING_START,
            SdkConstants.ATTR_PADDING_END,
            SdkConstants.ATTR_PADDING_HORIZONTAL,
            SdkConstants.ATTR_PADDING_VERTICAL,
            SdkConstants.ATTR_PADDING_LEFT,
            SdkConstants.ATTR_PADDING_RIGHT,
            // Text
            SdkConstants.ATTR_TEXT_SIZE,
            // Drawable
            SdkConstants.ATTR_DRAWABLE_PADDING,
            // Width x height
            SdkConstants.ATTR_LAYOUT_WIDTH,
            SdkConstants.ATTR_LAYOUT_HEIGHT
        )
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.value == "0dp") return

        START_DIGITS.find(attribute.value) ?: return

        context.report(
            ISSUE,
            context.getLocation(attribute),
            ISSUE.getExplanation(TextFormat.TEXT)
        )
    }

    companion object {
        /** Match digits with optional leading '-' */
        private val START_DIGITS = "^-?\\d+".toRegex()

        @JvmField
        val ISSUE = Issue.create(
            id = "RawDimensionValue",
            briefDescription = "Use @dimen/... instead of a raw dimension value",
            explanation = "For consistent design across different layouts use a dimension resource from `values/dimens.xml` or an `?attr/...` reference instead of a raw dimension value.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(RawDimensionDetector::class.java, RESOURCE_FILE_SCOPE)
        )
    }
}
