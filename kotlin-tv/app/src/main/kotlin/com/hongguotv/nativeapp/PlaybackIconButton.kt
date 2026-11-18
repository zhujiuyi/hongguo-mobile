// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.widget.TextView

/** Icon-only transport control; text remains the accessible action name. */
internal class PlaybackIconButton(context: Context, action: String) : TextView(context) {
    var iconAction = action
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        id = generateViewId()
        isFocusable = true
        isFocusableInTouchMode = false
        isClickable = true
        minWidth = dp(48)
        minHeight = dp(48)
        setOnFocusChangeListener { _, _ -> paintFocus() }
        paintFocus()
    }

    private fun paintFocus() {
        val border = if (hasFocus()) Color.WHITE else Color.TRANSPARENT
        background = RippleDrawable(
            ColorStateList.valueOf(0x55ffffff),
            TvStyle.shape(context, Color.TRANSPARENT, border, radius = 12),
            TvStyle.shape(context, Color.WHITE, radius = 12),
        )
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        contentDescription = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (iconAction == "speed") {
            val label = text.toString().substringAfterLast(' ')
            speedPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, resources.displayMetrics)
            val available = (width - dp(8)).coerceAtLeast(0)
            val textWidth = speedPaint.measureText(label)
            if (textWidth > available) speedPaint.textSize *= available / textWidth
            canvas.drawText(label, width / 2f, height / 2f - (speedPaint.ascent() + speedPaint.descent()) / 2f, speedPaint)
            return
        }
        val kind = when (iconAction) {
            "transport-play" -> if (text.toString() == "暂停") 11 else 10
            "previous" -> TvGlyph.PREVIOUS
            "next" -> TvGlyph.NEXT
            "episodes" -> 1
            "danmaku" -> 13
            "fullscreen" -> TvGlyph.FULLSCREEN
            "exit-fullscreen" -> TvGlyph.EXIT_FULLSCREEN
            else -> 4
        }
        val size = minOf(dp(28), width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        TvGlyph(kind, size, Color.WHITE).apply {
            setBounds(left, top, left + size, top + size)
            draw(canvas)
        }
    }
}
