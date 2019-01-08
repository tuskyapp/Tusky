/* Copyright 2018 Conny Duck
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.view

import android.content.Context
import androidx.emoji.widget.EmojiEditTextHelper
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView
import android.text.InputType
import android.text.method.KeyListener
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class EditTextTyped @JvmOverloads constructor(context: Context,
                                              attributeSet: AttributeSet? = null)
    : AppCompatMultiAutoCompleteTextView(context, attributeSet) {

    private var onCommitContentListener: InputConnectionCompat.OnCommitContentListener? = null
    private val emojiEditTextHelper: EmojiEditTextHelper = EmojiEditTextHelper(this)

    init {
        //fix a bug with autocomplete and some keyboards
        val newInputType = inputType and (inputType xor InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        inputType = newInputType
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(keyListener))
    }

    override fun setKeyListener(input: KeyListener) {
        super.setKeyListener(getEmojiEditTextHelper().getKeyListener(input))
    }

    fun setOnCommitContentListener(listener: InputConnectionCompat.OnCommitContentListener) {
        onCommitContentListener = listener
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val connection = super.onCreateInputConnection(editorInfo)
        return if (onCommitContentListener != null) {
            EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))
            getEmojiEditTextHelper().onCreateInputConnection(InputConnectionCompat.createWrapper(connection, editorInfo,
                    onCommitContentListener!!), editorInfo)!!
        } else {
            connection
        }
    }

    private fun getEmojiEditTextHelper(): EmojiEditTextHelper {
        return emojiEditTextHelper
    }
}
