package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.keylesspalace.tusky.R
import kotlin.math.roundToInt

/**
 * Lays out a set of [MediaPreviewImageView]s keeping their aspect ratios into account.
 */
class MediaPreviewLayout(context: Context, attrs: AttributeSet? = null) :
    ViewGroup(context, attrs) {

    private val spacing = context.resources.getDimensionPixelOffset(R.dimen.preview_image_spacing)

    /**
     * An ordered list of aspect ratios used for layout. An image view for each aspect ratio passed
     * will be attached. Supports up to 4, additional ones will be ignored.
     */
    var aspectRatios: List<Double> = emptyList()
        set(value) {
            field = value
            attachImageViews()
        }

    private val imageViewCache = Array(4) {
        LayoutInflater.from(context).inflate(R.layout.item_image_preview_overlay, this, false)
    }

    private var measuredOrientation = LinearLayout.VERTICAL

    private fun attachImageViews() {
        removeAllViews()
        for (i in 0 until aspectRatios.size.coerceAtMost(imageViewCache.size)) {
            addView(imageViewCache[i])
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val halfWidth = width / 2 - spacing / 2
        var totalHeight = 0

        when (childCount) {
            1 -> {
                val aspect = aspectRatios[0]
                totalHeight += getChildAt(0).measureToAspect(width, aspect)
            }
            2 -> {
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]

                if ((aspect1 + aspect2) / 2 > 1.2) {
                    // stack vertically
                    measuredOrientation = LinearLayout.VERTICAL
                    totalHeight += getChildAt(0).measureToAspect(width, aspect1.coerceAtLeast(1.8))
                    totalHeight += spacing
                    totalHeight += getChildAt(1).measureToAspect(width, aspect2.coerceAtLeast(1.8))
                } else {
                    // stack horizontally
                    measuredOrientation = LinearLayout.HORIZONTAL
                    val height = rowHeight(halfWidth, aspect1, aspect2)
                    totalHeight += height
                    getChildAt(0).measureExactly(halfWidth, height)
                    getChildAt(1).measureExactly(halfWidth, height)
                }
            }
            3 -> {
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]
                val aspect3 = aspectRatios[2]
                if (aspect1 >= 1) {
                    // |     1     |
                    // -------------
                    // |  2  |  3  |
                    measuredOrientation = LinearLayout.VERTICAL
                    totalHeight += getChildAt(0).measureToAspect(width, aspect1.coerceAtLeast(1.8))
                    totalHeight += spacing
                    val bottomHeight = rowHeight(halfWidth, aspect2, aspect3)
                    totalHeight += bottomHeight
                    getChildAt(1).measureExactly(halfWidth, bottomHeight)
                    getChildAt(2).measureExactly(halfWidth, bottomHeight)
                } else {
                    // |     |  2  |
                    // |  1  |-----|
                    // |     |  3  |
                    measuredOrientation = LinearLayout.HORIZONTAL
                    val colHeight = getChildAt(0).measureToAspect(halfWidth, aspect1)
                    totalHeight += colHeight
                    val halfHeight = colHeight / 2 - spacing / 2
                    getChildAt(1).measureExactly(halfWidth, halfHeight)
                    getChildAt(2).measureExactly(halfWidth, halfHeight)
                }
            }
            4 -> {
                val aspect1 = aspectRatios[0]
                val aspect2 = aspectRatios[1]
                val aspect3 = aspectRatios[2]
                val aspect4 = aspectRatios[3]
                val topHeight = rowHeight(halfWidth, aspect1, aspect2)
                totalHeight += topHeight
                getChildAt(0).measureExactly(halfWidth, topHeight)
                getChildAt(1).measureExactly(halfWidth, topHeight)
                totalHeight += spacing
                val bottomHeight = rowHeight(halfWidth, aspect3, aspect4)
                totalHeight += bottomHeight
                getChildAt(2).measureExactly(halfWidth, bottomHeight)
                getChildAt(3).measureExactly(halfWidth, bottomHeight)
            }
        }

        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t
        val halfWidth = width / 2 - spacing / 2
        when (childCount) {
            1 -> {
                getChildAt(0).layout(0, 0, width, height)
            }
            2 -> {
                if (measuredOrientation == LinearLayout.VERTICAL) {
                    val y = imageViewCache[0].measuredHeight
                    getChildAt(0).layout(0, 0, width, y)
                    getChildAt(1).layout(
                        0,
                        y + spacing,
                        width,
                        y + spacing + getChildAt(1).measuredHeight
                    )
                } else {
                    getChildAt(0).layout(0, 0, halfWidth, height)
                    getChildAt(1).layout(halfWidth + spacing, 0, width, height)
                }
            }
            3 -> {
                if (measuredOrientation == LinearLayout.VERTICAL) {
                    val y = getChildAt(0).measuredHeight
                    getChildAt(0).layout(0, 0, width, y)
                    getChildAt(1).layout(0, y + spacing, halfWidth, height)
                    getChildAt(2).layout(halfWidth + spacing, y + spacing, width, height)
                } else {
                    val colHeight = getChildAt(0).measuredHeight
                    getChildAt(0).layout(0, 0, halfWidth, colHeight)
                    val halfHeight = colHeight / 2 - spacing / 2
                    getChildAt(1).layout(halfWidth + spacing, 0, width, halfHeight)
                    getChildAt(2).layout(
                        halfWidth + spacing,
                        halfHeight + spacing,
                        width,
                        colHeight
                    )
                }
            }
            4 -> {
                val topHeight = (getChildAt(0).measuredHeight + getChildAt(1).measuredHeight) / 2
                getChildAt(0).layout(0, 0, halfWidth, topHeight)
                getChildAt(1).layout(halfWidth + spacing, 0, width, topHeight)
                val bottomHeight =
                    (imageViewCache[2].measuredHeight + imageViewCache[3].measuredHeight) / 2
                getChildAt(2).layout(
                    0,
                    topHeight + spacing,
                    halfWidth,
                    topHeight + spacing + bottomHeight
                )
                getChildAt(3).layout(
                    halfWidth + spacing,
                    topHeight + spacing,
                    width,
                    topHeight + spacing + bottomHeight
                )
            }
        }
    }

    inline fun forEachIndexed(action: (Int, MediaPreviewImageView, TextView) -> Unit) {
        for (index in 0 until childCount) {
            val wrapper = getChildAt(index)
            action(
                index,
                wrapper.findViewById(R.id.preview_image_view) as MediaPreviewImageView,
                wrapper.findViewById(R.id.preview_media_description_indicator) as TextView
            )
        }
    }
}

private fun rowHeight(halfWidth: Int, aspect1: Double, aspect2: Double): Int {
    return ((halfWidth / aspect1 + halfWidth / aspect2) / 2).roundToInt()
}

private fun View.measureToAspect(width: Int, aspect: Double): Int {
    val height = (width / aspect).roundToInt()
    measureExactly(width, height)
    return height
}

private fun View.measureExactly(width: Int, height: Int) {
    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    )
}
