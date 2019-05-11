package com.keylesspalace.tusky.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Half.toFloat
import android.util.Log
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.keylesspalace.tusky.entity.Attachment
import java.nio.charset.Charset
import java.security.MessageDigest

class GlideMatrixTransformation(val focus: Attachment.Focus): BitmapTransformation() {

    private var focalMatrix = Matrix()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        FocalPointUtil.updateFocalPointMatrix(toTransform.width.toFloat(), toTransform.height.toFloat(),
                outWidth.toFloat(), outHeight.toFloat(),
                focus, focalMatrix)
        Log.d("GlideMatrixTrans","transform: $outWidth-$outHeight")
        return Bitmap.createBitmap(toTransform,0,0,outWidth,outHeight,focalMatrix,true)
    }

    override fun equals(other: Any?): Boolean {
        return other is GlideMatrixTransformation
    }

    override fun hashCode(): Int {
        return ID.hashCode()
    }


    companion object{
        private val ID = "com.keylesspalace.tusky.util.GlideMatrixTransformation"
        private val ID_BYTES = ID.toByteArray(Charset.forName("UTF-8"))
    }
}