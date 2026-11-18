// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/** Shared episode ranges, grid and focus paths for detail and playback. */
internal class EpisodeSelectorView(
    context: Context,
    count: Int,
    current: Int,
    val group: Int,
    lastWatched: Int?,
    lastCompleted: Boolean,
    onGroup: (Int) -> Unit,
    onJump: (Int, View) -> Unit,
    onSelect: (Int) -> Unit,
) : LinearLayout(context) {
    val groupButtons: List<TextView>
    val episodes: List<View>
    val jump: TextView
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private var columns = 0
    private var focusedEpisode = if (current / 20 == group) current else group * 20

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun button(value: String, selected: Boolean = false, action: () -> Unit) =
        TextView(context).apply {
            id = View.generateViewId()
            text = value
            textSize = 14f
            gravity = Gravity.CENTER
            isFocusable = true
            isSelected = selected
            minimumHeight = dp(48)
            setTextColor(if (selected) TvStyle.accent else TvStyle.text)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            fun paint(focused: Boolean) {
                background = RippleDrawable(
                    ColorStateList.valueOf(TvStyle.text and 0x33ffffff),
                    TvStyle.card(context, focused, selected),
                    null,
                )
            }
            setOnFocusChangeListener { _, focused -> paint(focused) }
            setOnClickListener { action() }
            paint(false)
        }

    init {
        orientation = VERTICAL
        val ranges = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val strip = LinearLayout(context)
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            addView(strip)
        }
        ranges.addView(scroll, LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        val maxGroup = (count - 1).coerceAtLeast(0) / 20
        groupButtons = (0..maxGroup).map { index ->
            val start = index * 20 + 1
            val end = minOf((index + 1) * 20, count)
            val cell = button("$start–$end", index == group) { if (index != group) onGroup(index) }.apply {
                tag = "episode-group-$index"
                contentDescription = "第 $start 至 $end 集分组"
                textSize = 13f
                minWidth = dp(76)
                maxLines = 1
                if (isSelected) setTypeface(null, Typeface.BOLD)
            }
            strip.addView(cell, LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            cell
        }
        jump = button("跳转集数") { onJump(focusedEpisode, jump) }
        ranges.addView(jump, LayoutParams(-2, -2))
        addView(ranges, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        addView(grid, LayoutParams(-1, -2))
        episodes = (group * 20 until minOf((group + 1) * 20, count)).map { index ->
            val status = when {
                index == current -> "当前"
                index == lastWatched -> if (lastCompleted) "已看" else "上次"
                else -> ""
            }
            val number = (index + 1).toString()
            val title = if (status.isEmpty()) number else "$number\n$status"
            button(title, index == current) { onSelect(index) }.apply {
                tag = "episode-$index"
                minimumHeight = dp(52)
                textSize = 13f
                setPadding(dp(2), dp(4), dp(2), dp(4))
                contentDescription = "第 ${index + 1} 集" + if (status.isEmpty()) "" else "，$status"
                val paint = onFocusChangeListener
                setOnFocusChangeListener { view, focused ->
                    paint?.onFocusChange(view, focused)
                    if (focused) focusedEpisode = index
                }
            }
        }
        val activeGroup = groupButtons[group.coerceIn(groupButtons.indices)]
        groupButtons.forEachIndexed { i, cell ->
            cell.nextFocusLeftId = groupButtons.getOrNull(i - 1)?.id ?: cell.id
            cell.nextFocusRightId = groupButtons.getOrNull(i + 1)?.id ?: jump.id
            cell.nextFocusUpId = cell.id
            cell.nextFocusDownId = episodes.firstOrNull()?.id ?: cell.id
        }
        jump.nextFocusLeftId = groupButtons.last().id
        jump.nextFocusRightId = jump.id
        jump.nextFocusUpId = jump.id
        jump.nextFocusDownId = episodes.firstOrNull()?.id ?: jump.id
        updateGrid(resources.displayMetrics.widthPixels)
        scroll.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                scroll.scrollTo((activeGroup.left - scroll.width / 2 + activeGroup.width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            updateGrid(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun updateGrid(width: Int) {
        val newColumns = (width / dp(54).coerceAtLeast(1)).coerceIn(1, 10)
        if (newColumns == columns) return
        columns = newColumns
        val focused = grid.findFocus()
        for (index in 0 until grid.childCount) {
            (grid.getChildAt(index) as LinearLayout).removeAllViews()
        }
        grid.removeAllViews()
        episodes.chunked(columns).forEach { cells ->
            val row = LinearLayout(context).apply { isBaselineAligned = false }
            (cells + List(columns - cells.size) { Space(context) }).forEach { cell ->
                row.addView(cell, LayoutParams(0, -1, 1f).apply { marginEnd = dp(6) })
            }
            grid.addView(row, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        val activeGroup = groupButtons[group.coerceIn(groupButtons.indices)]
        episodes.forEachIndexed { index, cell ->
            val column = index % columns
            cell.nextFocusLeftId = if (column == 0) cell.id else episodes[index - 1].id
            cell.nextFocusRightId =
                if (column == columns - 1 || index == episodes.lastIndex) cell.id else episodes[index + 1].id
            cell.nextFocusUpId = episodes.getOrNull(index - columns)?.id ?: activeGroup.id
            cell.nextFocusDownId = episodes.getOrNull(index + columns)?.id ?: View.NO_ID
        }
        focused?.requestFocus()
    }
}
