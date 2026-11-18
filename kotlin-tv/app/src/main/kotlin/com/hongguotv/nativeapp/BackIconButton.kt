// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.widget.ImageButton
import android.widget.ImageView

internal class BackIconButton(
    context: Context,
    label: String = "返回上一级",
    tint: Int = TvStyle.text,
    surface: Int = Color.TRANSPARENT,
) : ImageButton(context) {
    init {
        val density = resources.displayMetrics.density
        id = generateViewId()
        contentDescription = label
        minimumWidth = (48 * density).toInt()
        minimumHeight = (48 * density).toInt()
        scaleType = ImageView.ScaleType.CENTER
        setImageDrawable(TvGlyph(TvGlyph.BACK, (26 * density).toInt(), tint))
        background = RippleDrawable(
            ColorStateList.valueOf((tint and 0x00ffffff) or 0x33000000),
            TvStyle.shape(context, surface, radius = 24),
            TvStyle.shape(context, Color.WHITE, radius = 24),
        )
        setPadding(0, 0, 0, 0)
        isFocusable = true
        isFocusableInTouchMode = false
    }
}
