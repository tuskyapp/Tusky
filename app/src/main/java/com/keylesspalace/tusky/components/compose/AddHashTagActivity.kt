package com.keylesspalace.tusky.components.compose

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Respond to ACTION_PROCESS_TEXT be inserting "#" at the start of every whitespace/non-whitespace
 * boundary.
 */
class AddHashTagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != Intent.ACTION_PROCESS_TEXT) {
            finish()
            return
        }

        if (intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)) {
            finish()
            return
        }

        val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
        if (selectedText == null) {
            finish()
            return
        }

        val newText = addHashtags(selectedText)

        val intent = Intent()
        intent.putExtra(Intent.EXTRA_PROCESS_TEXT, newText)
        setResult(RESULT_OK, intent)

        finish()
    }

    companion object {
        /**
         * Return a copy of `text` with "#" inserted at the start of every
         * whitespace/non-whitespace boundary.
         */
        fun addHashtags(text: String): String {
            var wasWhitespace = true
            val out = mutableListOf<Char>()
            for (ch in text.iterator()) {
                if (ch.isWhitespace()) {
                    wasWhitespace = true
                    out.add(ch)
                    continue
                }

                if (wasWhitespace) {
                    out.add('#')
                }
                out.add(ch)
                wasWhitespace = false
            }

            return out.joinToString(separator = "")
        }
    }
}
