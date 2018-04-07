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
import android.support.v13.view.inputmethod.EditorInfoCompat
import android.support.v13.view.inputmethod.InputConnectionCompat
import android.support.v7.widget.AppCompatMultiAutoCompleteTextView
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class EditTextTyped @JvmOverloads constructor(context: Context,
                                              attributeSet: AttributeSet? = null)
    : AppCompatMultiAutoCompleteTextView(context, attributeSet) {

    private var onCommitContentListener: InputConnectionCompat.OnCommitContentListener? = null

    init {
        //fix a bug with autocomplete and some keyboards
        val newInputType = inputType and (inputType xor InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        inputType = newInputType
    }

    fun setOnCommitContentListener(listener: InputConnectionCompat.OnCommitContentListener) {
        onCommitContentListener = listener
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val connection = super.onCreateInputConnection(editorInfo)
        return if (onCommitContentListener != null) {
            EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))
            InputConnectionCompat.createWrapper(connection, editorInfo,
                    onCommitContentListener!!)
        } else {
            connection
        }
    }
}
