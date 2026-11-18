// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal interface HandlesHorizontalKeys

internal class CatalogShell(context: Context, private val navigate: (Int) -> Unit) : LinearLayout(context) {
    val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val shortcuts = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val brand = ImageView(context).apply {
        setImageResource(R.drawable.brand_mark)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "hongguoTV"
    }
    private val back = BackIconButton(context).apply { visibility = GONE }
    private val bottomBar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val buttons = mutableMapOf<Int, TextView>()
    private var remembered: View? = null
    private var selectedDestination = 0

    val navigation: List<TextView>
        get() = (0..8).map { buttons.getValue(if (it in 3..5) 3 else it) }

    val hasNavigationFocus: Boolean
        get() = shortcuts.hasFocus() || bottomBar.hasFocus()

    val backEntry: View?
        get() = back.takeIf { it.visibility == VISIBLE }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        setBackgroundColor(TvStyle.background)
        shortcuts.setPadding(dp(12), 0, dp(8), 0)
        shortcuts.addView(brand, LayoutParams(dp(36), dp(36)))
        shortcuts.addView(back, LayoutParams(-2, -2))
        shortcuts.addView(View(context), LayoutParams(0, 1, 1f))
        listOf(8 to "随心看", 6 to "设置").forEach { (index, label) ->
            shortcuts.addView(
                destination(index, label, primary = false),
                LayoutParams(-2, -2).apply { marginStart = dp(4) },
            )
        }
        addView(shortcuts, LayoutParams(-1, -2))

        body.setPadding(dp(12), dp(6), dp(12), dp(8))
        addView(body, LayoutParams(-1, 0, 1f))

        addView(View(context).apply { setBackgroundColor(TvStyle.outline) }, LayoutParams(-1, dp(1)))
        bottomBar.setBackgroundColor(TvStyle.surface)
        bottomBar.setPadding(dp(6), dp(4), dp(6), dp(4))
        val primaryDestinations = listOf(0 to "首页", 1 to "找剧", 7 to "搜索", 2 to "排行榜", 3 to "我的")
        primaryDestinations.forEach { (index, label) ->
            bottomBar.addView(destination(index, label, primary = true), LayoutParams(0, -2, 1f))
        }
        addView(bottomBar, LayoutParams(-1, -2))
    }

    private fun destination(index: Int, label: String, primary: Boolean): TextView {
        val glyph = TvGlyph(index, dp(if (primary) 22 else 18), TvStyle.muted)
        val button = TextView(context).apply {
            id = View.generateViewId()
            text = label
            textSize = if (primary) 12f else 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isFocusable = true
            minHeight = dp(if (primary) 52 else 48)
            setPadding(dp(if (primary) 2 else 8), dp(6), dp(if (primary) 2 else 8), dp(6))
            compoundDrawablePadding = dp(if (primary) 4 else 5)
            if (primary) setCompoundDrawablesRelativeWithIntrinsicBounds(null, glyph, null, null)
            else setCompoundDrawablesRelativeWithIntrinsicBounds(glyph, null, null, null)
            setOnFocusChangeListener { _, _ -> paintNavigation(this) }
            setOnClickListener {
                if (index != selectedDestination) navigate(index)
                else restoreContent()
            }
            isSelected = index == selectedDestination
            paintNavigation(this)
        }
        buttons[index] = button
        return button
    }

    private fun paintNavigation(button: TextView) {
        val focused = button.hasFocus()
        val ink = if (focused) TvStyle.background else if (button.isSelected) TvStyle.accent else TvStyle.muted
        val fill = if (focused) TvStyle.text else if (button.isSelected) TvStyle.raised else Color.TRANSPARENT
        button.background = RippleDrawable(
            catalogRippleColor(),
            TvStyle.shape(context, fill, radius = 12),
            TvStyle.shape(context, Color.WHITE, radius = 12),
        )
        button.setTextColor(ink)
        button.typeface = if (button.isSelected || focused) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        button.compoundDrawablesRelative.filterIsInstance<TvGlyph>().forEach {
            it.ink = ink
            it.invalidateSelf()
        }
    }

    fun select(index: Int) {
        selectedDestination = if (index in 3..5) 3 else index
        buttons.forEach { (destination, button) ->
            button.isSelected = destination == selectedDestination
            paintNavigation(button)
        }
    }

    fun setBackAction(action: (() -> Unit)?) {
        brand.visibility = if (action == null) VISIBLE else GONE
        back.visibility = if (action == null) GONE else VISIBLE
        back.contentDescription = "返回上一级"
        back.nextFocusDownId = View.NO_ID
        back.setOnClickListener(action?.let { onBack -> View.OnClickListener { onBack() } })
    }

    fun clearBody() {
        remembered = null
        body.removeAllViews()
    }

    override fun requestChildFocus(child: View, focused: View) {
        if (child === body) remembered = focused
        super.requestChildFocus(child, focused)
    }

    fun restoreContent(): Boolean {
        val target = remembered?.takeIf { it.isAttachedToWindow && it.isShown && it.isFocusable }
            ?: body.getFocusables(FOCUS_FORWARD).firstOrNull()
        return target?.requestFocus() == true
    }
}

internal fun catalogControlBackground(context: Context, focused: Boolean, selected: Boolean = false) =
    RippleDrawable(catalogRippleColor(), TvStyle.card(context, focused, selected), null)

private fun catalogRippleColor() = ColorStateList.valueOf((TvStyle.text and 0x00ffffff) or 0x24000000)
