package com.keylesspalace.tusky.components.preference

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.SplashActivity
import com.keylesspalace.tusky.util.EmojiCompatFont
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.FONTS
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import okhttp3.OkHttpClient
import kotlin.system.exitProcess

/**
 * This Preference lets the user select their preferred emoji font
 */
class EmojiPreference(
        context: Context,
        private val okHttpClient: OkHttpClient
) : Preference(context) {

    private lateinit var selected: EmojiCompatFont
    private lateinit var original: EmojiCompatFont
    private val radioButtons = mutableListOf<RadioButton>()
    private var updated = false
    private var currentNeedsUpdate = false

    private val downloadDisposables = MutableList<Disposable?>(FONTS.size) { null }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)

        // Find out which font is currently active
        selected = EmojiCompatFont.byId(
                PreferenceManager.getDefaultSharedPreferences(context).getInt(key, 0)
        )
        // We'll use this later to determine if anything has changed
        original = selected
        summary = selected.getDisplay(context)
    }

    override fun onClick() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_emojicompat, null)
        viewIds.forEachIndexed { index, viewId ->
            setupItem(view.findViewById(viewId), FONTS[index])
        }
        AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ -> onDialogOk() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun setupItem(container: View, font: EmojiCompatFont) {
        val title: TextView = container.findViewById(R.id.emojicompat_name)
        val caption: TextView = container.findViewById(R.id.emojicompat_caption)
        val thumb: ImageView = container.findViewById(R.id.emojicompat_thumb)
        val download: ImageButton = container.findViewById(R.id.emojicompat_download)
        val cancel: ImageButton = container.findViewById(R.id.emojicompat_download_cancel)
        val radio: RadioButton = container.findViewById(R.id.emojicompat_radio)

        // Initialize all the views
        title.text = font.getDisplay(container.context)
        caption.setText(font.caption)
        thumb.setImageResource(font.img)

        // There needs to be a list of all the radio buttons in order to uncheck them when one is selected
        radioButtons.add(radio)
        updateItem(font, container)

        // Set actions
        download.setOnClickListener { startDownload(font, container) }
        cancel.setOnClickListener { cancelDownload(font, container) }
        radio.setOnClickListener { radioButton: View -> select(font, radioButton as RadioButton) }
        container.setOnClickListener { containerView: View ->
            select(font, containerView.findViewById(R.id.emojicompat_radio))
        }
    }

    private fun startDownload(font: EmojiCompatFont, container: View) {
        val download: ImageButton = container.findViewById(R.id.emojicompat_download)
        val caption: TextView = container.findViewById(R.id.emojicompat_caption)
        val progressBar: ProgressBar = container.findViewById(R.id.emojicompat_progress)
        val cancel: ImageButton = container.findViewById(R.id.emojicompat_download_cancel)

        // Switch to downloading style
        download.visibility = View.GONE
        caption.visibility = View.INVISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        cancel.visibility = View.VISIBLE
        font.downloadFontFile(context, okHttpClient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { progress ->
                            // The progress is returned as a float between 0 and 1, or -1 if it could not determined
                            if (progress >= 0) {
                                progressBar.isIndeterminate = false
                                val max = progressBar.max.toFloat()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    progressBar.setProgress((max * progress).toInt(), true)
                                } else {
                                    progressBar.progress = (max * progress).toInt()
                                }
                            } else {
                                progressBar.isIndeterminate = true
                            }
                        },
                        {
                            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                            updateItem(font, container)
                        },
                        {
                            finishDownload(font, container)
                        }
                ).also { downloadDisposables[font.id] = it }


    }

    private fun cancelDownload(font: EmojiCompatFont, container: View) {
        font.deleteDownloadedFile(container.context)
        downloadDisposables[font.id]?.dispose()
        downloadDisposables[font.id] = null
        updateItem(font, container)
    }

    private fun finishDownload(font: EmojiCompatFont, container: View) {
        select(font, container.findViewById(R.id.emojicompat_radio))
        updateItem(font, container)
        // Set the flag to restart the app (because an update has been downloaded)
        if (selected === original && currentNeedsUpdate) {
            updated = true
            currentNeedsUpdate = false
        }
    }

    /**
     * Select a font both visually and logically
     *
     * @param font  The font to be selected
     * @param radio The radio button associated with it's visual item
     */
    private fun select(font: EmojiCompatFont, radio: RadioButton) {
        selected = font
        // Uncheck all the other buttons
        for (other in radioButtons) {
            if (other !== radio) {
                other.isChecked = false
            }
        }
        radio.isChecked = true
    }

    /**
     * Called when a "consistent" state is reached, i.e. it's not downloading the font
     *
     * @param font      The font to be displayed
     * @param container The ConstraintLayout containing the item
     */
    private fun updateItem(font: EmojiCompatFont, container: View) {
        // Assignments
        val download: ImageButton = container.findViewById(R.id.emojicompat_download)
        val caption: TextView = container.findViewById(R.id.emojicompat_caption)
        val progress: ProgressBar = container.findViewById(R.id.emojicompat_progress)
        val cancel: ImageButton = container.findViewById(R.id.emojicompat_download_cancel)
        val radio: RadioButton = container.findViewById(R.id.emojicompat_radio)

        // There's no download going on
        progress.visibility = View.GONE
        cancel.visibility = View.GONE
        caption.visibility = View.VISIBLE
        if (font.isDownloaded(context)) {
            // Make it selectable
            download.visibility = View.GONE
            radio.visibility = View.VISIBLE
            container.isClickable = true
        } else {
            // Make it downloadable
            download.visibility = View.VISIBLE
            radio.visibility = View.GONE
            container.isClickable = false
        }

        // Select it if necessary
        if (font === selected) {
            radio.isChecked = true
            // Update available
            if (!font.isDownloaded(context)) {
                currentNeedsUpdate = true
            }
        } else {
            radio.isChecked = false
        }
    }

    private fun saveSelectedFont() {
        val index = selected.id
        Log.i(TAG, "saveSelectedFont: Font ID: $index")
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .putInt(key, index)
                .apply()
        summary = selected.getDisplay(context)
    }

    /**
     * User clicked ok -> save the selected font and offer to restart the app if something changed
     */
    private fun onDialogOk() {
        saveSelectedFont()
        if (selected !== original || updated) {
            AlertDialog.Builder(context)
                    .setTitle(R.string.restart_required)
                    .setMessage(R.string.restart_emoji)
                    .setNegativeButton(R.string.later, null)
                    .setPositiveButton(R.string.restart) { _, _ ->
                        // Restart the app
                        // From https://stackoverflow.com/a/17166729/5070653
                        val launchIntent = Intent(context, SplashActivity::class.java)
                        val mPendingIntent = PendingIntent.getActivity(
                                context,
                                0x1f973, // This is the codepoint of the party face emoji :D
                                launchIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT)
                        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        mgr.set(
                                AlarmManager.RTC,
                                System.currentTimeMillis() + 100,
                                mPendingIntent)
                        exitProcess(0)
                    }.show()
        }
    }

    companion object {
        private const val TAG = "EmojiPreference"

        // Please note that this array must sorted in the same way as the fonts.
        private val viewIds = intArrayOf(
                R.id.item_nomoji,
                R.id.item_blobmoji,
                R.id.item_twemoji,
                R.id.item_notoemoji
        )
    }
}