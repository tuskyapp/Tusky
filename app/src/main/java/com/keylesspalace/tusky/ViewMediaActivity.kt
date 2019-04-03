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

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.viewpager.widget.ViewPager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.BuildConfig.APPLICATION_ID
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.fragment.ViewImageFragment

import com.keylesspalace.tusky.pager.AvatarImagePagerAdapter
import com.keylesspalace.tusky.pager.ImagePagerAdapter
import com.keylesspalace.tusky.util.CollectionUtil.map
import com.keylesspalace.tusky.util.getTemporaryMediaFilename
import com.keylesspalace.tusky.viewdata.AttachmentViewData

import kotlinx.android.synthetic.main.activity_view_media.*

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.ArrayList

class ViewMediaActivity : BaseActivity(), ViewImageFragment.PhotoActionsListener {
    companion object {
        private const val EXTRA_ATTACHMENTS = "attachments"
        private const val EXTRA_ATTACHMENT_INDEX = "index"
        private const val EXTRA_AVATAR_URL = "avatar"
        private const val TAG = "ViewMediaActivity"

        @JvmStatic
        fun newIntent(context: Context?, attachments: List<AttachmentViewData>, index: Int): Intent {
            val intent = Intent(context, ViewMediaActivity::class.java)
            intent.putParcelableArrayListExtra(EXTRA_ATTACHMENTS, ArrayList(attachments))
            intent.putExtra(EXTRA_ATTACHMENT_INDEX, index)
            return intent
        }

        fun newAvatarIntent(context: Context, url: String): Intent {
            val intent = Intent(context, ViewMediaActivity::class.java)
            intent.putExtra(EXTRA_AVATAR_URL, url)
            return intent
        }
    }

    private var attachments: ArrayList<AttachmentViewData>? = null

    private var toolbarVisible = true
    private val toolbarVisibilityListeners = ArrayList<ToolbarVisibilityListener>()

    interface ToolbarVisibilityListener {
        fun onToolbarVisiblityChanged(isVisible: Boolean)
    }

    fun addToolbarVisibilityListener(listener: ToolbarVisibilityListener): Function0<Boolean> {
        this.toolbarVisibilityListeners.add(listener)
        listener.onToolbarVisiblityChanged(toolbarVisible)
        return { toolbarVisibilityListeners.remove(listener) }
    }

    fun isToolbarVisible(): Boolean {
        return toolbarVisible
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_media)

        supportPostponeEnterTransition()

        // Gather the parameters.
        attachments = intent.getParcelableArrayListExtra(EXTRA_ATTACHMENTS)
        val initialPosition = intent.getIntExtra(EXTRA_ATTACHMENT_INDEX, 0)

        val adapter = if(attachments != null) {
            val realAttachs = map(attachments, AttachmentViewData::attachment)
            // Setup the view pager.
            ImagePagerAdapter(supportFragmentManager, realAttachs, initialPosition)

        } else {
            val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL) ?: throw IllegalArgumentException("attachment list or avatar url has to be set")

            AvatarImagePagerAdapter(supportFragmentManager, avatarUrl)
        }

        viewPager.adapter = adapter
        viewPager.currentItem = initialPosition
        viewPager.addOnPageChangeListener(object: ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                toolbar.title = adapter.getPageTitle(position)
            }
        })

        // Setup the toolbar.
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setDisplayShowHomeEnabled(true)
            actionBar.title = adapter.getPageTitle(initialPosition)
        }
        toolbar.setNavigationOnClickListener { supportFinishAfterTransition() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_download -> requestDownloadMedia()
                R.id.action_open_status -> onOpenStatus()
                R.id.action_share_media -> shareMedia()
                R.id.action_copy_media_link -> copyLink()
            }
            true
        }

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
        window.statusBarColor = Color.BLACK
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if(attachments != null) {
            menuInflater.inflate(R.menu.view_media_toolbar, menu)
            return true
        }
        return false
    }

    override fun onBringUp() {
        supportStartPostponedEnterTransition()
    }

    override fun onDismiss() {
        supportFinishAfterTransition()
    }

    override fun onPhotoTap() {
        toolbarVisible = !toolbarVisible
        for (listener in toolbarVisibilityListeners) {
            listener.onToolbarVisiblityChanged(toolbarVisible)
        }
        val visibility = if(toolbarVisible){ View.VISIBLE } else { View.INVISIBLE }
        val alpha = if(toolbarVisible){ 1.0f } else { 0.0f }

        toolbar.animate().alpha(alpha)
                .setListener(object: AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        toolbar.visibility = visibility
                        animation.removeListener(this)
                    }
                })
                .start()
    }

    private fun downloadMedia() {
        val url = attachments!![viewPager.currentItem].attachment.url
        val filename = Uri.parse(url).lastPathSegment
        Toast.makeText(applicationContext, resources.getString(R.string.download_image, filename), Toast.LENGTH_SHORT).show()

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                getString(R.string.app_name) + "/" + filename)
        downloadManager.enqueue(request)
    }

    private fun requestDownloadMedia() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Build.VERSION_CODES.M) { _, grantResults ->
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadMedia()
            } else {
                showErrorDialog(toolbar, R.string.error_media_download_permission, R.string.action_retry) { requestDownloadMedia() }
            }
        }
    }

    private fun onOpenStatus() {
        val attach = attachments!![viewPager.currentItem]
        startActivityWithSlideInAnimation(ViewThreadActivity.startIntent(this, attach.statusId, attach.statusUrl))
    }

    private fun copyLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText(null, attachments!![viewPager.currentItem].attachment.url)
    }

    private fun shareMedia() {
        val directory = applicationContext.getExternalFilesDir("Tusky")
        if (directory == null || !(directory.exists())) {
            Log.e(TAG, "Error obtaining directory to save temporary media.")
            return
        }

        val attachment = attachments!![viewPager.currentItem].attachment
        when(attachment.type) {
            Attachment.Type.IMAGE -> shareImage(directory, attachment.url)
            Attachment.Type.VIDEO,
            Attachment.Type.GIFV -> shareVideo(directory, attachment.url)
            else -> Log.e(TAG, "Unknown media format for sharing.")
        }
    }

    private fun shareFile(file: File, mimeType: String?) {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(applicationContext, "$APPLICATION_ID.fileprovider", file))
        sendIntent.type = mimeType
        startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_media_to)))
    }


    private fun shareImage(directory: File, url: String) {
        val file = File(directory, getTemporaryMediaFilename("png"))
        /*STOPSHIP
        Glide.with(applicationContext).load(Uri.parse(url)).into(object: Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                try {
                    val stream = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.close()
                } catch (fnfe: FileNotFoundException) {
                    Log.e(TAG, "Error writing temporary media.")
                } catch (ioe: IOException) {
                    Log.e(TAG, "Error writing temporary media.")
                }
            }

            override fun onBitmapFailed(errorDrawable: Drawable?) {
                Log.e(TAG, "Error loading temporary media.")
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable?) { }
        })
        */
        shareFile(file, "image/png")
    }

    private fun shareVideo(directory: File, url: String) {
        val uri = Uri.parse(url)
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mimeType = mimeTypeMap.getMimeTypeFromExtension(extension)
        val filename = getTemporaryMediaFilename(extension)
        val file = File(directory, filename)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(uri)
        request.setDestinationUri(Uri.fromFile(file))
        request.setVisibleInDownloadsUi(false)
        downloadManager.enqueue(request)

        shareFile(file, mimeType)
    }
}
