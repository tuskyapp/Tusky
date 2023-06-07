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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import org.junit.Test

@Suppress("UnstableApiUsage")
class RawDimensionDetectorTest : LintDetectorTest() {
    override fun getDetector() = RawDimensionDetector()
    override fun getIssues() = listOf(RawDimensionDetector.ISSUE)

    @Test
    fun testErrors() {
        lint().files(
            xml(
                "/res/layout/test.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                    <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/accountFieldValue"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="12dp"
                        android:drawablePadding="6dp"
                        android:gravity="center"
                        android:lineSpacingMultiplier="1.1" />
                """
            ).indented()
        )
            .allowMissingSdk()
            .run()
            .expectErrorCount(2)
            .expect(
                """res/layout/test.xml:6: Error: For consistent design across different layouts use a dimension resource from values/dimens.xml or an ?attr/... reference instead of a raw dimension value. [RawDimensionValue]
                        android:layout_marginStart="12dp"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
res/layout/test.xml:7: Error: For consistent design across different layouts use a dimension resource from values/dimens.xml or an ?attr/... reference instead of a raw dimension value. [RawDimensionValue]
                        android:drawablePadding="6dp"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
2 errors, 0 warnings
                    """
            )
    }

    @Test
    fun testNoErrors() {
        lint().files(
            xml(
                "/res/layout/test.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                    <TextView xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/accountFieldValue"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="@dimen/some_dimen"
                        android:drawablePadding="?attr/some_attr"
                        android:gravity="center"
                        android:lineSpacingMultiplier="1.1" />
                """
            ).indented()
        )
            .allowMissingSdk()
            .run()
            .expectErrorCount(0)
    }
}
