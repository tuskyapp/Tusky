package com.keylesspalace.tusky.components.preference

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.SplashActivity
import com.keylesspalace.tusky.databinding.DialogEmojicompatBinding
import com.keylesspalace.tusky.databinding.ItemEmojiPrefBinding
import com.keylesspalace.tusky.util.EmojiCompatFont
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.BLOBMOJI
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.FONTS
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.NOTOEMOJI
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.SYSTEM_DEFAULT
import com.keylesspalace.tusky.util.EmojiCompatFont.Companion.TWEMOJI
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
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
        val binding = DialogEmojicompatBinding.inflate(LayoutInflater.from(context))

        setupItem(BLOBMOJI, binding.itemBlobmoji)
        setupItem(TWEMOJI, binding.itemTwemoji)
        setupItem(NOTOEMOJI, binding.itemNotoemoji)
        setupItem(SYSTEM_DEFAULT, binding.itemNomoji)

        AlertDialog.Builder(context)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ -> onDialogOk() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun setupItem(font: EmojiCompatFont, binding: ItemEmojiPrefBinding) {
        // Initialize all the views
        binding.emojiName.text = font.getDisplay(context)
        binding.emojiCaption.setText(font.caption)
        binding.emojiThumbnail.setImageResource(font.img)

        // There needs to be a list of all the radio buttons in order to uncheck them when one is selected
        radioButtons.add(binding.emojiRadioButton)
        updateItem(font, binding)

        // Set actions
        binding.emojiDownload.setOnClickListener { startDownload(font, binding) }
        binding.emojiDownloadCancel.setOnClickListener { cancelDownload(font, binding) }
        binding.emojiRadioButton.setOnClickListener { radioButton: View -> select(font, radioButton as RadioButton) }
        binding.root.setOnClickListener {
            select(font, binding.emojiRadioButton)
        }
    }

    private fun startDownload(font: EmojiCompatFont, binding: ItemEmojiPrefBinding) {
        // Switch to downloading style
        binding.emojiDownload.hide()
        binding.emojiCaption.visibility = View.INVISIBLE
        binding.emojiProgress.show()
        binding.emojiProgress.progress = 0
        binding.emojiDownloadCancel.show()
        font.downloadFontFile(context, okHttpClient)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { progress ->
                            // The progress is returned as a float between 0 and 1, or -1 if it could not determined
                            if (progress >= 0) {
                                binding.emojiProgress.isIndeterminate = false
                                val max = binding.emojiProgress.max.toFloat()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    binding.emojiProgress.setProgress((max * progress).toInt(), true)
                                } else {
                                    binding.emojiProgress.progress = (max * progress).toInt()
                                }
                            } else {
                                binding.emojiProgress.isIndeterminate = true
                            }
                        },
                        {
                            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                            updateItem(font, binding)
                        },
                        {
                            finishDownload(font, binding)
                        }
                ).also { downloadDisposables[font.id] = it }


    }

    private fun cancelDownload(font: EmojiCompatFont, binding: ItemEmojiPrefBinding) {
        font.deleteDownloadedFile(context)
        downloadDisposables[font.id]?.dispose()
        downloadDisposables[font.id] = null
        updateItem(font, binding)
    }

    private fun finishDownload(font: EmojiCompatFont, binding: ItemEmojiPrefBinding) {
        select(font, binding.emojiRadioButton)
        updateItem(font, binding)
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
        radioButtons.forEach { radioButton ->
            radioButton.isChecked = radioButton == radio
        }
    }

    /**
     * Called when a "consistent" state is reached, i.e. it's not downloading the font
     *
     * @param font      The font to be displayed
     * @param binding The ItemEmojiPrefBinding to show the item in
     */
    private fun updateItem(font: EmojiCompatFont, binding: ItemEmojiPrefBinding) {
        // There's no download going on
        binding.emojiProgress.hide()
        binding.emojiDownloadCancel.hide()
        binding.emojiCaption.show()
        if (font.isDownloaded(context)) {
            // Make it selectable
            binding.emojiDownload.hide()
            binding.emojiRadioButton.show()
            binding.root.isClickable = true
        } else {
            // Make it downloadable
            binding.emojiDownload.show()
            binding.emojiRadioButton.hide()
            binding.root.isClickable = false
        }

        // Select it if necessary
        if (font === selected) {
            binding.emojiRadioButton.isChecked = true
            // Update available
            if (!font.isDownloaded(context)) {
                currentNeedsUpdate = true
            }
        } else {
            binding.emojiRadioButton.isChecked = false
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
    }
}