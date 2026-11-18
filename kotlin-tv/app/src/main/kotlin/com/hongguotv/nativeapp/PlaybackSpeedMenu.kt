// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hongguotv.core.PlaybackSpeed

/** Anchored speed picker for the playback controls. */
internal class PlaybackSpeedMenu(
    private val anchor: View,
    private val selected: Float,
    private val onSelect: (Float) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var popup: PopupWindow? = null
    val showing: Boolean get() = popup?.isShowing == true
    private val anchorAttachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = dismiss()
    }

    private fun dp(value: Int) = (value * anchor.resources.displayMetrics.density).toInt()

    fun show(): Boolean {
        if (showing) return true
        if (!anchor.isAttachedToWindow || !anchor.isShown || anchor.width == 0 || anchor.height == 0) return false
        val available = visibleBounds()
        if (available.isEmpty) return false
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val button = Rect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)
        if (!Rect.intersects(available, button)) return false

        val context = anchor.context
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(96)
            setPadding(dp(15), dp(8), dp(15), dp(8))
        }
        val current = PlaybackSpeed.normalize(selected)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        var selectedRow: TextView? = null
        for (speed in PlaybackSpeed.options) {
            val row = TextView(context).apply {
                text = "${speed}x"
                contentDescription = "$speed 倍速"
                textSize = 16f
                typeface = medium
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                setPadding(0, dp(5), 0, dp(5))
                isFocusable = true
                isSelected = speed == current
                setTextColor(if (isSelected) Color.WHITE else 0xffbebfc7.toInt())
                fun paint(focused: Boolean) {
                    val fill = if (isSelected) 0xff343541.toInt() else Color.TRANSPARENT
                    val stroke = if (focused) 0xffa2a4ae.toInt() else Color.TRANSPARENT
                    background = RippleDrawable(
                        ColorStateList.valueOf(0x29ffffff),
                        InsetDrawable(TvStyle.shape(context, fill, stroke, radius = 9), 0, dp(5), 0, dp(5)),
                        InsetDrawable(TvStyle.shape(context, Color.WHITE, radius = 9), 0, dp(5), 0, dp(5)),
                    )
                }
                paint(false)
                setOnFocusChangeListener { _, hasFocus -> paint(hasFocus) }
                setOnClickListener {
                    dismiss()
                    onSelect(speed)
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (speed == current) selectedRow = row
        }
        list.measure(
            View.MeasureSpec.makeMeasureSpec(available.width(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val width = list.measuredWidth
        val desiredHeight = list.measuredHeight
        val gap = dp(8)
        val above = button.top - available.top - gap
        val below = available.bottom - button.bottom - gap
        val left = button.left - available.left - gap
        val right = available.right - button.right - gap
        val x: Int
        val y: Int
        val height: Int
        if (maxOf(above, below) < desiredHeight && maxOf(left, right) >= width) {
            height = minOf(desiredHeight, available.height())
            x = if (right >= left) button.right + gap else button.left - gap - width
            y = (button.bottom - height).coerceIn(available.top, available.bottom - height)
        } else {
            val opensAbove = above >= desiredHeight || above >= below
            height = minOf(desiredHeight, if (opensAbove) above else below)
            if (height <= 0) return false
            x = (button.centerX() - width / 2).coerceIn(available.left, available.right - width)
            y = if (opensAbove) button.top - gap - height else button.bottom + gap
        }

        val scroll = ScrollView(context).apply {
            addView(list)
            isVerticalScrollBarEnabled = desiredHeight > height
        }
        val next = PopupWindow(scroll, width, height, true).apply {
            setBackgroundDrawable(TvStyle.shape(context, 0xff23242f.toInt(), radius = 16))
            elevation = dp(8).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            isOutsideTouchable = true
        }
        try {
            next.showAsDropDown(anchor, x - button.left, y - button.bottom, Gravity.LEFT)
        } catch (_: WindowManager.BadTokenException) {
            next.dismiss()
            return false
        }
        popup = next
        anchor.addOnAttachStateChangeListener(anchorAttachment)
        next.setOnDismissListener {
            anchor.removeOnAttachStateChangeListener(anchorAttachment)
            popup = null
            onDismiss()
        }
        selectedRow?.let { row ->
            scroll.post {
                if (popup === next) {
                    row.requestFocus()
                    if (row.bottom > scroll.height) scroll.scrollTo(0, row.bottom - scroll.height + dp(8))
                }
            }
        }
        return true
    }

    fun dismiss() {
        popup?.dismiss()
    }

    private fun visibleBounds(): Rect {
        val root = anchor.rootView
        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val bounds = Rect(
            location[0] + (insets?.left ?: 0),
            location[1] + (insets?.top ?: 0),
            location[0] + root.width - (insets?.right ?: 0),
            location[1] + root.height - (insets?.bottom ?: 0),
        )
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        if (!bounds.intersect(frame)) return Rect()
        bounds.inset(dp(8), dp(8))
        return bounds
    }
}
