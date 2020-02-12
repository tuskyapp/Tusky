package com.keylesspalace.tusky.util

import android.app.DownloadManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.googlecode.tesseract.android.ResultIterator
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
        private const val MINIMUM_CONFIDENCE = 80

        val initialized: Boolean
                get() = tesseractLanguage != null

        fun initialize(context: Context, iso3Language: String){
            if (tesseractLanguage != iso3Language) {
                try {
                    // FIXME: Offer language selection / training data download? Ship training data with translations?
                    if (!tesseractApi.init(getDataDirectory(context), iso3Language, TessBaseAPI.OEM_TESSERACT_ONLY)) {
                        throw RuntimeException(context.getString(R.string.error_generic))
                    }
                    tesseractLanguage = iso3Language
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.training_data_initialization_error, e), Toast.LENGTH_LONG).show()
                }
            }
        }

        private fun getDataDirectory(context: Context): String {
            return context.getExternalFilesDir("OCR").toString()
        }

        fun performOCR(resource: Drawable, input: EditText, context: Context) {
            var result: ResultIterator? = null
            val text = StringBuilder()
            val level = TessBaseAPI.PageIteratorLevel.RIL_BLOCK

            try {
                tesseractApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT_OSD
                tesseractApi.setImage(resource.toBitmap())
                val fullText = tesseractApi.utF8Text // This is necessary to actually run OCR
                result = tesseractApi.resultIterator
                result?.begin()
                do {
                    val blockText = result?.getUTF8Text(level)
                    if (!blockText.isNullOrBlank() && result.confidence(level) > MINIMUM_CONFIDENCE) {
                        text.appendln(blockText.trim())
                    }
                } while (result?.next(level) == true)

                input.setText(text.trim())
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.training_data_ocr_error, e), Toast.LENGTH_LONG).show()
            } finally {
                result?.delete()
            }
        }

        private fun downloadTrainingData(context: Context, language: String) {
            val baseFilename = "${language}.traineddata"
            // The com.rmtheis package is still using tesseract 3.x
            // The newer version at https://github.com/alexcohn/tess-two supports tesseract 4.x
            // (which has much smaller training data), but we'd have to compile it ourselves?
            val uri = Uri.parse("https://github.com/tesseract-ocr/tessdata/raw/3.04.00/$baseFilename")
            val localPath = File(getDataDirectory(context), "tessdata/$baseFilename").toUri()
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
                downloadTrainingData(context, "${language}_vert")
            }
        }
    }
}