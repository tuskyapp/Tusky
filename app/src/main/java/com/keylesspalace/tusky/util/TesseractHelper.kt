package com.keylesspalace.tusky.util

import android.app.DownloadManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.googlecode.tesseract.android.TessBaseAPI
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.TuskyApplication
import java.io.File

class TesseractHelper {
    companion object {
        private val tesseractApi = TessBaseAPI()
        private var tesseractLanguage: String? = null
        private val languagesWithVerticalData = listOf(
                "jpn",
                "kor"
        )

        val initialized: Boolean
                get() = tesseractLanguage != null

        fun initialize(context: Context, iso3Language: String){
            if (tesseractLanguage != iso3Language) {
                try {
                    // FIXME: Offer language selection / training data download? Ship training data with translations?
                    if (!tesseractApi.init(getDataDirectory(context), iso3Language, TessBaseAPI.OEM_TESSERACT_ONLY)) {
                        throw RuntimeException("Unknown error")
                    }
                    tesseractLanguage = iso3Language
                } catch (e: Exception) {
                    Toast.makeText(context, String.format("Error initializing OCR engine: %s", e), Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun getDataDirectory(context: Context): String {
            return context.getExternalFilesDir("OCR").toString()
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

        private fun downloadTrainingData(context: Context, language: String) {
            val baseFilename = String.format("%s.traineddata", language)
            // The com.rmtheis package is still using tesseract 3.x
            // The newer version at https://github.com/alexcohn/tess-two supports tesseract 4.x
            // (which has much smaller training data), but we'd have to compile it ourselves?
            val uri = Uri.parse(String.format("https://github.com/tesseract-ocr/tessdata/raw/3.04.00/%s", baseFilename));
            val localPath = File(getDataDirectory(context), String.format("tessdata/%s", baseFilename)).toUri()
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(DownloadManager.Request(uri)
                    .setDestinationUri(localPath)
                    .setTitle(context.getString(R.string.training_data_download_notification, language)))
            // TODO: Check status etc.
        }

        fun downloadTrainingData(context: Context) {
            val language = TuskyApplication.localeManager.getISO3Locale()
            downloadTrainingData(context, language)
            if (languagesWithVerticalData.contains(language)) {
                downloadTrainingData(context, String.format("%s_vert", language))
            }
        }
    }
}