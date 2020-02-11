package com.keylesspalace.tusky.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractHelper {
    companion object {
        private val tesseractApi = TessBaseAPI()
        private var tesseractLanguage: String? = null

        val initialized: Boolean
                get() = tesseractLanguage != null

        fun initialize(context: Context, iso3Language: String){
            if (tesseractLanguage != iso3Language) {
                try {
                    // FIXME: Offer language selection / training data download? Ship training data with translations?
                    if (!tesseractApi.init(context.getExternalFilesDir("OCR").toString(), iso3Language, TessBaseAPI.OEM_TESSERACT_ONLY)) {
                        throw RuntimeException("Unknown error")
                    }
                    tesseractLanguage = iso3Language
                } catch (e: Exception) {
                    Toast.makeText(context, String.format("Error initializing OCR engine: %s", e), Toast.LENGTH_LONG).show()
                }
            }
        }

        fun performOCR(resource: Drawable, input: EditText, context: Context) {
            try {
                tesseractApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                tesseractApi.setImage(resource.toBitmap())
                val text = tesseractApi.utF8Text
                // TODO: Minimum confidence threshold?
                if (!text.isNullOrBlank()) {
                    input.setText(text)
                }
            } catch (e: Exception) {
                Toast.makeText(context, String.format("Error performing OCR: %s", e), Toast.LENGTH_LONG).show()
            }
        }
    }
}