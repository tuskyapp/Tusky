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

package com.keylesspalace.tusky.components.compose.view

import android.content.Context
import android.text.InputType
import android.text.method.KeyListener
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.emoji2.viewsintegration.EmojiEditTextHelper

class EditTextTyped @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) :
    AppCompatMultiAutoCompleteTextView(context, attributeSet) {

    private val emojiEditTextHelper: EmojiEditTextHelper = EmojiEditTextHelper(this)

    init {
        // fix a bug with autocomplete and some keyboards
        val newInputType = inputType and (inputType xor InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        inputType = newInputType
        super.setKeyListener(emojiEditTextHelper.getKeyListener(keyListener))
    }

    override fun setKeyListener(input: KeyListener?) {
        if (input != null) {
            super.setKeyListener(emojiEditTextHelper.getKeyListener(input))
        } else {
            super.setKeyListener(input)
        }
    }

    fun setOnReceiveContentListener(listener: OnReceiveContentListener) {
        ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*"), listener)
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val connection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))
        return emojiEditTextHelper.onCreateInputConnection(
            InputConnectionCompat.createWrapper(this, connection, editorInfo),
            editorInfo
        )!!
    }

    /**
     * Override pasting to ensure that formatted content is always pasted as
     * plain text.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste) {
            return super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        }

        return super.onTextContextMenuItem(id)
    }
}
