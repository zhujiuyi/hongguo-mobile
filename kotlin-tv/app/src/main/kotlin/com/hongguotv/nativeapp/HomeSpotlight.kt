// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.core.Series

/** Continue-watching banner with its own poster, title, and actions. */
internal class HomeSpotlight(
    context: Context,
    series: Series,
    status: String,
    resume: Boolean,
    cover: (ImageView, String) -> Unit,
    open: (Boolean) -> Unit,
) : LinearLayout(context) {
    val play: TextView
    val detail: TextView
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        val compact = resources.configuration.screenWidthDp < 600
        orientation = if (compact) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontalPadding = dp(if (compact) 12 else 18)
        setPadding(horizontalPadding, dp(12), horizontalPadding, dp(12))
        background = TvStyle.shape(context, TvStyle.surface)
        val header = if (compact) LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL } else this
        val info = LinearLayout(context).apply { orientation = VERTICAL }
        fun label(value: String, size: Float, color: Int, lines: Int = 1) = TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val title = label(series.title, if (compact) 20f else 26f, TvStyle.text, 2).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        info.addView(title, LayoutParams(-1, -2))
        info.addView(label(status.ifBlank { series.tags }, 13f, TvStyle.muted), LayoutParams(-1, -2).apply { topMargin = dp(5) })
        val actions = LinearLayout(context)
        fun action(value: String, proceed: () -> Unit) = label(value, 14f, TvStyle.text).apply {
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = false
            isClickable = true
            minHeight = dp(48)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = catalogControlBackground(context, false)
            setOnFocusChangeListener { _, focus -> background = catalogControlBackground(context, focus) }
            setOnClickListener { proceed() }
        }
        play = action(if (resume) "▶ 继续观看" else "▶ 开始观看") { open(true) }
        detail = action("查看详情") { open(false) }
        play.tag = "home:spotlight:play"
        detail.tag = "home:spotlight:detail"
        val actionWidth = if (compact) 0 else -2
        val actionWeight = if (compact) 1f else 0f
        actions.addView(play, LayoutParams(actionWidth, -2, actionWeight))
        actions.addView(detail, LayoutParams(actionWidth, -2, actionWeight).apply { marginStart = dp(8) })
        play.nextFocusRightId = detail.id
        detail.nextFocusLeftId = play.id
        val actionsParams = LayoutParams(-1, -2).apply { topMargin = dp(10) }
        if (!compact) info.addView(actions, actionsParams)
        header.addView(info, LayoutParams(0, -2, 1f))
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(image, LayoutParams(dp(if (compact) 72 else 104), dp(if (compact) 103 else 148)).apply {
            marginStart = dp(if (compact) 12 else 24)
        })
        if (compact) {
            addView(header, LayoutParams(-1, -2))
            addView(actions, actionsParams)
        }
        cover(image, series.cover)
    }
}
