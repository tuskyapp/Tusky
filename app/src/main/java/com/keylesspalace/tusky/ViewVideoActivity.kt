/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.content.FileProvider
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.VideoView

import com.keylesspalace.tusky.util.MediaUtils

import java.io.File

import com.keylesspalace.tusky.BuildConfig.APPLICATION_ID

class ViewVideoActivity: BaseActivity() {

    private val handler = Handler(Looper.getMainLooper())
    lateinit var toolbar: Toolbar
    private lateinit var url: String
    private lateinit var statusID: String
    private lateinit var statusURL: String

    companion object {
        private const val TAG = "ViewVideoActivity"
        const val URL_EXTRA = "url"
        const val STATUS_ID_EXTRA = "statusID"
        const val STATUS_URL_EXTRA = "statusURL"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_video)

        val progressBar = findViewById<ProgressBar>(R.id.video_progress)
        val videoView = findViewById<VideoView>(R.id.video_player)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val bar = supportActionBar
        if (bar != null) {
            bar.title = null
            bar.setDisplayHomeAsUpEnabled(true)
            bar.setDisplayShowHomeEnabled(true)
        }
        toolbar.setOnMenuItemClickListener {item ->
            val id = item.itemId
            when (id) {
                R.id.action_download -> downloadFile(url)
                R.id.action_open_status -> onOpenStatus()
                R.id.action_share_media -> shareVideo()
            }
            true
        }

        url = intent.getStringExtra(URL_EXTRA)
        statusID = intent.getStringExtra(STATUS_ID_EXTRA)
        statusURL = intent.getStringExtra(STATUS_URL_EXTRA)

        videoView.setVideoPath(url)
        val controller = MediaController(this)
        controller.setMediaPlayer(videoView)
        videoView.setMediaController(controller)
        videoView.requestFocus()
        videoView.setOnPreparedListener { mp ->
            progressBar.visibility = View.GONE
            mp.isLooping = true
            hideToolbarAfterDelay()
        }
        videoView.start()

        videoView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handler.removeCallbacksAndMessages(null)
                toolbar.animate().cancel()
                toolbar.alpha = 1.0f
                toolbar.visibility = View.VISIBLE
                hideToolbarAfterDelay()
            }
            false
        }

        window.statusBarColor = Color.BLACK
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.view_media_toolbar, menu)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadFile(url)
                } else {
                    doErrorDialog(toolbar, R.string.error_media_download_permission, R.string.action_retry) { _ -> downloadFile(url) }
                }
            }
        }
    }

    private fun hideToolbarAfterDelay() {
        handler.postDelayed({
            toolbar.animate().alpha(0.0f).setListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val decorView = window.decorView
                    val uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE
                    decorView.systemUiVisibility = uiOptions
                    toolbar.visibility = View.INVISIBLE
                    animation.removeListener(this)
                }
            })
        }, 3000)
    }

    private fun onOpenStatus() {
        startActivityWithSlideInAnimation(ViewThreadActivity.startIntent(this, statusID, statusURL))
    }

    private fun shareVideo() {
        val directory = applicationContext.getExternalFilesDir("Tusky")
        if (directory == null || !(directory.exists())) {
            Log.e(TAG, "Error obtaining directory to save temporary media.")
            return
        }

        val uri = Uri.parse(url)
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mimeType = mimeTypeMap.getMimeTypeFromExtension(extension)
        val filename = MediaUtils.getTemporaryMediaFilename(extension)
        val file = File(directory, filename)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(uri)
        request.setDestinationUri(Uri.fromFile(file))
        request.setVisibleInDownloadsUi(false)
        downloadManager.enqueue(request)

        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(applicationContext, "$APPLICATION_ID.fileprovider", file))
        sendIntent.type = mimeType
        startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_media_to)))
    }
}
