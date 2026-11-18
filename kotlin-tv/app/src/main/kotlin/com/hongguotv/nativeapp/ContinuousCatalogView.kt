// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.core.ContentHttpException
import com.hongguotv.core.RankPosition
import com.hongguotv.core.Series
import kotlin.math.roundToInt

internal class ContinuousCatalogView(
    context: Context,
    private val columns: Int,
    private val upper: View,
    private val bind: (Series, View?) -> View,
    private val selected: (Series) -> Unit,
    private val open: (Series) -> Unit,
    private val more: (Series, View) -> Unit,
    private val load: () -> Unit,
) : LinearLayout(context) {
    data class Position(val id: String, val top: Int)

    private val entries = mutableListOf<Series>()
    private val ids = mutableMapOf<String, Long>()
    private var nextId = 0L
    private var available = false
    private var busy = false
    private var failureReason = ""
    private var failed = false
    private var closed = false
    private var browsing = false
    private var enterAdded = false
    private var restorePosition: Position? = null
    private var searchPresentation = false
    private var visibleColumns = columns

    val grid = GridView(context).apply {
        id = View.generateViewId()
        numColumns = columns
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        horizontalSpacing = dp(8)
        verticalSpacing = dp(8)
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        setPadding(dp(2), dp(2), dp(2), dp(2))
        selector = ColorDrawable(Color.TRANSPARENT)
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        nextFocusUpId = upper.id
    }
    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(TvStyle.muted)
        setPadding(dp(12), 0, dp(12), 0)
        visibility = GONE
    }
    private val status = TextView(context).apply {
        textSize = 12f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(TvStyle.muted)
    }
    val next = action("加载更多") { requestMore(true) }
    val top = action("回到顶部") { resetToTop() }
    private val cards = object : BaseAdapter() {
        override fun getCount() = entries.size

        override fun getItem(position: Int) = entries[position]

        override fun getItemId(position: Int) =
            entries.getOrNull(position)?.let { ids.getOrPut(it.id) { nextId++ } }
                ?: AdapterView.INVALID_ROW_ID

        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = entries[position]
            return bind(item, convertView).apply {
                tag = item.id
                isFocusable = false
                isClickable = false
                isLongClickable = false
                if (this is ViewGroup) descendantFocusability = FOCUS_BLOCK_DESCENDANTS
                background = catalogControlBackground(context, isSelectedPosition(position))
            }
        }
    }

    init {
        orientation = VERTICAL
        grid.adapter = cards
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, index, _ ->
            entries.getOrNull(index)?.let { item ->
                selected(item)
                open(item)
            }
        }
        grid.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, view, index, _ ->
            val item = entries.getOrNull(index) ?: return@OnItemLongClickListener false
            selected(item)
            more(item, view)
            true
        }
        grid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                entries.getOrNull(position)?.let(selected)
                paintFocus()
                if (browsing && position >= entries.size - visibleColumns * 2)
                    requestMore(false)
            }
        }
        grid.setOnFocusChangeListener { _, _ -> paintFocus() }
        grid.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) {
                    val item = entries.getOrNull(grid.selectedItemPosition)
                    val card = grid.selectedView
                    if (item != null && card != null) more(item, card)
                }
                true
            } else {
                if (event.action == KeyEvent.ACTION_DOWN && (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_RIGHT))
                    browsing = true
                false
            }
        }
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, state: Int) {
                browsing = state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE
            }

            override fun onScroll(view: AbsListView?, first: Int, count: Int, total: Int) {
                if (browsing && first > 0 && first + count >= total - visibleColumns * 2)
                    requestMore(false)
            }
        })
        addView(grid, LayoutParams(-1, 0, 1f))
        addView(empty, LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(status, LayoutParams(0, -2, 1f))
        footer.addView(next, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        footer.addView(top, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        addView(footer, LayoutParams(-1, -2).apply { topMargin = dp(4) })
        next.nextFocusUpId = grid.id
        top.nextFocusUpId = grid.id
        paintStatus()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) -
            paddingLeft - paddingRight - grid.paddingLeft - grid.paddingRight
        val minCardWidth = dp(if (searchPresentation) 270 else 104)
        val spacing = grid.requestedHorizontalSpacing
        val fit = ((availableWidth + spacing) / (minCardWidth + spacing)).coerceIn(1, columns)
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && fit != visibleColumns) {
            visibleColumns = fit
            grid.numColumns = fit
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val pending = restorePosition ?: return
        if (grid.childCount == 0) return
        restorePosition = null
        val index = entries.indexOfFirst { it.id == pending.id }
        val tile = grid.getChildAt(index - grid.firstVisiblePosition) ?: return
        val offset = tile.top - pending.top
        if (offset != 0) grid.scrollListBy(offset)
    }

    fun setSideEntry(view: View?) { grid.nextFocusLeftId = view?.id ?: View.NO_ID }

    fun useSearchPresentation() {
        searchPresentation = true
        requestLayout()
    }

    fun resetToTop() {
        browsing = false
        enterAdded = false
        moveTo(0, grid.paddingTop)
    }

    fun show(items: List<Series>, hasMore: Boolean, position: Position? = null) {
        entries.clear()
        entries.addAll(items.distinctBy { it.id })
        available = hasMore
        busy = false
        failed = false
        browsing = false
        enterAdded = false
        cards.notifyDataSetChanged()
        paintStatus()
        val index = entries.indexOfFirst { it.id == position?.id }
        val offset = if (index >= 0 && position != null) position.top else grid.paddingTop
        moveTo(index.coerceAtLeast(0), offset)
        entries.getOrNull(index.coerceAtLeast(0))?.let(selected)
    }

    private fun moveTo(index: Int, top: Int) {
        val item = entries.getOrNull(index)
        restorePosition = item?.let { Position(it.id, top) }
        grid.setSelection(if (item == null) AdapterView.INVALID_POSITION else index)
    }

    fun capture(): Position? {
        val position = if (!grid.isInTouchMode && grid.selectedView != null)
            grid.selectedItemPosition else grid.firstVisiblePosition
        val tile = grid.getChildAt(position - grid.firstVisiblePosition)
        return restorePosition ?: entries.getOrNull(position)?.let { Position(it.id, tile?.top ?: grid.paddingTop) }
    }

    fun requestMore(manual: Boolean) {
        if (closed || !available || (failed && !manual)) return
        if (busy) {
            if (manual && next.hasFocus()) enterAdded = true
            return
        }
        busy = true
        failed = false
        browsing = false
        enterAdded = manual && next.hasFocus()
        paintStatus()
        load()
    }

    fun append(items: List<Series>, hasMore: Boolean) {
        if (closed) return
        val seen = entries.mapTo(HashSet()) { it.id }
        val firstNew = entries.size
        entries.addAll(items.filter { seen.add(it.id) })
        available = hasMore
        busy = false
        failed = false
        browsing = false
        val focusAdded = enterAdded && next.hasFocus()
        enterAdded = false
        cards.notifyDataSetChanged()
        paintStatus()
        if (focusAdded && entries.size > firstNew) {
            grid.setSelection(firstNew)
            grid.requestFocus()
        } else if (!available && next.hasFocus()) {
            top.requestFocus()
        }
    }

    fun fail(problem: Throwable? = null) {
        if (closed) return
        failureReason = when (problem) {
            is ContentHttpException -> "（HTTP ${problem.statusCode}）"
            is java.net.SocketTimeoutException -> "（连接超时）"
            is java.net.UnknownHostException -> "（无法连接网站）"
            is java.net.SocketException -> "（网络连接中断）"
            null -> ""
            else -> "（响应异常）"
        }
        busy = false
        failed = true
        browsing = false
        enterAdded = false
        paintStatus()
    }

    fun enter() {
        if (entries.isEmpty() && available) next.requestFocus() else grid.requestFocus()
    }

    fun close() {
        closed = true
        restorePosition = null
        grid.onItemSelectedListener = null
        grid.setOnScrollListener(null)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun action(value: String, click: () -> Unit) = TextView(context).apply {
        id = View.generateViewId()
        text = value
        textSize = 13f
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setTextColor(TvStyle.text)
        isFocusable = true
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = catalogControlBackground(context, false)
        setOnFocusChangeListener { _, focused -> background = catalogControlBackground(context, focused) }
        setOnClickListener { click() }
    }

    private fun isSelectedPosition(position: Int) = grid.hasFocus() && grid.selectedItemPosition == position

    private fun paintFocus() {
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).background =
                catalogControlBackground(context, isSelectedPosition(grid.firstVisiblePosition + i))
        }
    }

    private fun paintStatus() {
        val noItems = entries.isEmpty()
        grid.visibility = if (noItems) GONE else VISIBLE
        empty.visibility = if (noItems) VISIBLE else GONE
        empty.text = "暂无结果"
        status.text = when {
            busy -> "正在加载…"
            failed -> "加载失败$failureReason"
            noItems -> ""
            available -> "已加载 ${entries.size} 部"
            else -> "共 ${entries.size} 部"
        }
        next.text = when {
            busy -> "加载中…"
            failed -> "重试"
            else -> "加载更多"
        }
        next.visibility = if (available) VISIBLE else GONE
        next.isEnabled = !busy
        top.visibility = if (noItems) GONE else VISIBLE
        grid.nextFocusDownId = if (available) next.id else top.id
    }
}

internal class CatalogPosterCard(context: Context) : LinearLayout(context) {
    val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val poster = FrameLayout(context).apply {
        background = TvStyle.shape(context, TvStyle.raised, radius = 5)
        clipToOutline = true
    }
    val title = TextView(context).apply {
        textSize = 14f
        setTextColor(TvStyle.text)
        includeFontPadding = false
        minLines = 2
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(2), dp(6), dp(2), 0)
    }
    val badge = TextView(context).apply {
        textSize = 12f
        setTextColor(TvStyle.muted)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(2), dp(4), dp(2), 0)
    }

    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(8))
        poster.addView(image, FrameLayout.LayoutParams(-1, -1))
        addView(poster, LayoutParams(-1, dp(148)))
        addView(title)
        addView(badge)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cardWidth = MeasureSpec.getSize(widthMeasureSpec)
        if (cardWidth > 0 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val innerWidth = (cardWidth - paddingLeft - paddingRight).coerceAtLeast(1)
            poster.layoutParams.height = (innerWidth / TvStyle.POSTER_ASPECT).roundToInt()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun show(item: Series, position: RankPosition? = null) {
        title.text = item.title
        val rankLabel = position?.rank?.let { "第 $it 名" }.orEmpty()
        badge.text = listOf(rankLabel, position?.heat?.takeIf { it.isNotBlank() } ?: item.badge)
            .filter(String::isNotBlank).joinToString(" · ")
        contentDescription = listOf(rankLabel, item.title, item.badge, item.tags, position?.heat.orEmpty(), position?.metrics.orEmpty())
            .filter(String::isNotBlank).joinToString("，")
    }
}

internal class SearchResultCard(context: Context) : LinearLayout(context) {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = TvStyle.shape(context, TvStyle.raised, radius = 5)
        clipToOutline = true
    }
    private val title = TextView(context).apply {
        textSize = 16f
        setTextColor(TvStyle.text)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }
    private val badge = TextView(context).apply {
        textSize = 13f
        setTextColor(TvStyle.muted)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(6), 0, 0)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(image, LayoutParams(dp(56), dp(80)).apply { marginEnd = dp(12) })
        val copy = LinearLayout(context).apply { orientation = VERTICAL }
        copy.addView(title, LayoutParams(-1, -2))
        copy.addView(badge, LayoutParams(-1, -2))
        addView(copy, LayoutParams(0, -2, 1f))
    }

    fun show(item: Series) {
        title.text = item.title
        badge.text = item.badge
        badge.visibility = if (item.badge.isBlank()) GONE else VISIBLE
        contentDescription = listOf(item.title, item.badge).filter(String::isNotBlank).joinToString("，")
    }
}
