// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hongguotv.core.RelatedWork
import kotlin.math.roundToInt

internal class RelatedWorksView(
    context: Context,
    private val upper: List<View>,
    private val loadCover: (ImageView, String) -> Unit,
    private val reserveLabel: (RelatedWork) -> String,
    private val open: (RelatedWork, View) -> Unit,
    private val reload: () -> Unit,
) : LinearLayout(context) {
    private var allDialog: AlertDialog? = null
    private val labels = mutableListOf<Pair<RelatedWork, TextView>>()

    init {
        orientation = VERTICAL
        setPadding(0, dp(16), 0, dp(12))
        loading()
    }

    fun loading() { reset("正在加载相关作品…") }

    fun failed() { reset("相关作品加载失败，点按刷新重试") }

    fun show(rows: List<RelatedWork>) {
        reset(if (rows.isEmpty()) "暂无相关作品" else "", rows)
        if (rows.isEmpty()) return
        val line = LinearLayout(context)
        val strip = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(line, FrameLayout.LayoutParams(-2, -2))
        }
        addView(strip, LayoutParams(-1, -2))
        val width = dp(114)
        val posterHeight = ((width - dp(12)) / TvStyle.POSTER_ASPECT).roundToInt()
        rows.forEach { work ->
            val card = actionCard(work) { pressed -> open(work, pressed) }.apply {
                orientation = VERTICAL
                tag = work.series.id
                setPadding(dp(6), dp(6), dp(6), dp(10))
            }
            card.addView(poster(work), LayoutParams(-1, posterHeight).apply { bottomMargin = dp(6) })
            card.addView(title(work).apply { minLines = 2 })
            if (work.season.isNotBlank()) card.addView(label(work.season, 12f, TvStyle.muted))
            val status = label(status(work), 12f, TvStyle.muted)
            labels += work to status
            card.addView(status)
            line.addView(card, LayoutParams(width, -2).apply { marginEnd = dp(8) })
        }
    }

    fun refreshLabels() {
        labels.forEach { (work, view) -> if (work.upcoming) view.text = status(work) }
    }

    override fun onDetachedFromWindow() {
        allDialog?.dismiss()
        allDialog = null
        super.onDetachedFromWindow()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float, color: Int = TvStyle.text) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun action(value: String, click: (View) -> Unit) = label(value, 14f).apply {
        id = View.generateViewId()
        isFocusable = true
        gravity = Gravity.CENTER
        minHeight = dp(48)
        maxLines = 1
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = catalogControlBackground(context, false)
        setOnFocusChangeListener { _, focused -> background = catalogControlBackground(context, focused) }
        setOnClickListener(click)
    }

    private fun reset(message: String, rows: List<RelatedWork> = emptyList()) {
        removeAllViews()
        labels.clear()
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label("相关作品", 18f).apply {
            typeface = Typeface.DEFAULT_BOLD
        }, LayoutParams(0, -2, 1f))
        if (rows.isNotEmpty()) {
            heading.addView(action("全部 ${rows.size} 部") { showAll(rows, it) }, LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
        }
        heading.addView(action("刷新") { reload() }.apply {
            nextFocusUpId = upper.lastOrNull()?.id ?: View.NO_ID
        }, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        addView(heading, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (message.isNotBlank()) addView(label(message, 13f, TvStyle.muted))
    }

    private fun poster(work: RelatedWork) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = TvStyle.shape(context, TvStyle.raised, radius = 5)
        clipToOutline = true
        loadCover(this, work.series.cover)
    }

    private fun title(work: RelatedWork) = label(work.series.title, 14f).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun status(work: RelatedWork) = if (work.upcoming) reserveLabel(work) else "已上线"

    private fun actionCard(work: RelatedWork, click: (View) -> Unit) = LinearLayout(context).apply {
        id = View.generateViewId()
        isFocusable = true
        contentDescription = listOf(work.series.title, work.season, status(work))
            .filter(String::isNotBlank).joinToString("，")
        background = catalogControlBackground(context, false)
        setOnClickListener(click)
        setOnFocusChangeListener { _, focused -> background = catalogControlBackground(context, focused) }
    }

    private fun showAll(rows: List<RelatedWork>, anchor: View) {
        allDialog?.dismiss()
        val items = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        rows.forEach { work ->
            val row = actionCard(work) {
                allDialog?.dismiss()
                open(work, anchor)
            }.apply {
                tag = "all-${work.series.id}"
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(poster(work), LayoutParams(dp(52), dp(74)).apply { marginEnd = dp(12) })
            val details = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title(work))
                val meta = listOf(work.season, status(work)).filter(String::isNotBlank).joinToString(" · ")
                addView(label(meta, 12f, TvStyle.muted), LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            row.addView(details, LayoutParams(0, -2, 1f))
            items.addView(row, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        val builder = AlertDialog.Builder(context)
        val dialogContext = builder.context
        val tint = TextView(dialogContext).currentTextColor
        val back = BackIconButton(dialogContext, tint = tint)
        val header = dialogBackHeader(dialogContext, "相关作品", back, tint).apply {
            setPadding(dp(12), dp(4), dp(12), 0)
        }
        val dialog = builder
            .setCustomTitle(header)
            .setView(ScrollView(context).apply { addView(items) })
            .create()
        back.setOnClickListener { dialog.dismiss() }
        allDialog = dialog
        dialog.setOnDismissListener {
            if (allDialog === dialog) allDialog = null
            if (anchor.isAttachedToWindow && !anchor.isInTouchMode) anchor.requestFocus()
        }
        dialog.show()
        dialog.window?.setLayout(context.dialogWidth(520), -2)
    }
}
