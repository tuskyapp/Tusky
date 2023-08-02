package com.keylesspalace.tusky.util

import androidx.annotation.FontRes
import androidx.annotation.StyleRes
import com.keylesspalace.tusky.R

enum class EmbeddedFontFamily(@FontRes val font: Int, @StyleRes val style: Int) {
    DEFAULT(-1, -1),
    ATKINSON_HYPERLEGIBLE(R.font.atkinson_hyperlegible, R.style.FontAtkinsonHyperlegible),
    ESTEDAD(R.font.estedad, R.style.FontEstedad),
    OPENDYSLEXIC(R.font.opendyslexic, R.style.FontOpenDyslexic);

    companion object {
        fun from(s: String?): EmbeddedFontFamily {
            s ?: return DEFAULT

            return try {
                valueOf(s.uppercase())
            } catch (_: Throwable) {
                DEFAULT
            }
        }
    }
}
