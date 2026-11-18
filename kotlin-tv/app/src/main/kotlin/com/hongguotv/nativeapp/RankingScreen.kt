// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.core.RankBoard
import com.hongguotv.core.RankBrowserState
import com.hongguotv.core.RankSnapshot
import com.hongguotv.core.Series

internal class RankingScreen(
    private val activity: Activity,
    private val state: RankBrowserState,
    private val nav: List<View>,
    private val tools: TvTools,
    private val cover: (ImageView, String) -> Unit,
    private val open: (Series) -> Unit,
    private val more: (Series, View) -> Unit,
    private val load: (RankBoard, ((RankSnapshot?, String?) -> Unit)) -> Unit,
) : LinearLayout(activity) {
    private val controls = mutableListOf<TextView>()
    private val heading = label("热播榜", 22f).apply { typeface = Typeface.DEFAULT_BOLD }
    private val subtitle = label("", 13f, TvStyle.muted).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(8))
    }
    private val body = LinearLayout(activity).apply { orientation = VERTICAL }
    private val filterButton = button("题材") { chooseTopic() }.apply { maxWidth = dp(136) }
    private val refreshButton = button("刷新") { maybeRefresh(state.board, force = true) }
    private var browser: ContinuousCatalogView? = null
    private var closed = false

    init {
        orientation = VERTICAL
        val title = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(heading, LayoutParams(0, -2, 1f))
        title.addView(filterButton, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        title.addView(refreshButton, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        addView(title, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val boards = LinearLayout(activity)
        RankBoard.entries.forEach { board ->
            val control = button(board.label) {
                if (state.board == board) return@button
                state.select(board)
                markControls()
                render()
                maybeRefresh(board)
            }
            boards.addView(control, LayoutParams(0, -2, 1f).apply {
                if (board.ordinal > 0) marginStart = dp(6)
            })
            controls += control
        }
        controls += filterButton
        controls += refreshButton
        addView(boards, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        addView(subtitle, LayoutParams(-1, -2))
        addView(body, LayoutParams(-1, 0, 1f))
        markControls()
        render()
    }

    fun start(force: Boolean, focusNav: Boolean) {
        if (!isInTouchMode) {
            if (focusNav) nav[2].requestFocus()
            else if (state.selectedId.isNotEmpty()) browser?.enter()
            else controls[state.board.ordinal].requestFocus()
        }
        maybeRefresh(state.board, force = force && !state.isFresh())
    }

    fun backToTop() {
        browser?.resetToTop()
        state.selectedId = state.snapshots[state.board]?.filtered(state.topic)?.firstOrNull()?.id.orEmpty()
    }

    fun close() {
        if (closed) return
        browser?.capture()?.let { state.selectedId = it.id }
        closed = true
        browser?.close()
        browser = null
    }

    fun refreshFromState(target: RankBoard, error: String?) {
        if (closed) return
        markControls()
        if (target != state.board) return
        val snapshot = state.snapshots[target]
        if (snapshot != null && state.topic.isNotEmpty() && state.topic !in snapshot.topics) state.topic = ""
        render(keepScroll = true, error = error)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 16f, color: Int = TvStyle.text) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    private fun button(value: String, action: () -> Unit) = label(value, 14f).apply {
        id = View.generateViewId()
        isFocusable = true
        gravity = Gravity.CENTER
        maxLines = 1
        minHeight = dp(48)
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setOnFocusChangeListener { _, _ -> paintButton(this) }
        setOnClickListener { action() }
        paintButton(this)
    }

    private fun paintButton(button: TextView) {
        button.setTextColor(if (button.isSelected) TvStyle.accent else TvStyle.text)
        button.alpha = if (button.isEnabled) 1f else .5f
        button.background = catalogControlBackground(activity, button.hasFocus(), button.isSelected)
    }

    private fun markControls() {
        RankBoard.entries.forEachIndexed { index, board ->
            val control = controls[index]
            control.isSelected = state.board == board
            control.text = board.label + if (state.isRefreshing(board)) " …" else ""
            paintButton(control)
        }
        refreshButton.isEnabled = !state.isRefreshing(state.board)
        paintButton(refreshButton)
        nav.getOrNull(2)?.nextFocusUpId = controls[state.board.ordinal].id
    }

    private fun maybeRefresh(target: RankBoard, force: Boolean = false) {
        if (!force && state.isFresh(target)) return
        if (!state.beginRefresh(target)) {
            markControls()
            return
        }
        markControls()
        if (target == state.board) {
            if (state.snapshots[target] == null) render()
            else showStatus(state.snapshots[target], state.snapshots[target]?.filtered(state.topic).orEmpty().size)
        }
        load(target) { snapshot, error ->
            state.endRefresh(target, snapshot)
            refreshFromState(target, error)
        }
    }

    private fun chooseTopic() {
        val snapshot = state.snapshots[state.board] ?: return
        val values = listOf("") + snapshot.topics
        val labels = values.map { topic ->
            val name = topic.ifEmpty { "全部题材" }
            "$name · ${snapshot.filtered(topic).size} 部"
        }
        tools.choose(
            "${state.board.label}热播榜 · 题材",
            labels,
            filterButton,
            onChoice = { index ->
                state.filter(values[index])
                render()
            },
            selected = values.indexOf(state.topic),
        )
    }

    private fun showStatus(snapshot: RankSnapshot?, count: Int, error: String? = null) {
        subtitle.text = if (snapshot == null) {
            error.orEmpty()
        } else {
            val source = snapshot.ranking.updatedText.ifBlank { "来源：红果热播榜" }
            val status = when {
                error != null -> " · 更新失败"
                state.isRefreshing(state.board) -> " · 正在更新"
                else -> ""
            }
            "$source · $count 部$status"
        }
        subtitle.visibility = if (subtitle.text.isBlank()) GONE else VISIBLE
    }

    private fun render(keepScroll: Boolean = false, error: String? = null) {
        val enterGrid = browser?.grid?.hasFocus() == true
        val position = browser?.capture()?.takeIf { keepScroll }
            ?: state.selectedId.takeIf(String::isNotBlank)?.let { ContinuousCatalogView.Position(it, 0) }
        browser?.close()
        browser = null
        body.removeAllViews()
        val snapshot = state.snapshots[state.board]
        val items = snapshot?.filtered(state.topic).orEmpty()
        filterButton.text = state.topic.ifBlank { "题材" }
        filterButton.isSelected = state.topic.isNotBlank()
        filterButton.isEnabled = snapshot != null
        paintButton(filterButton)
        showStatus(snapshot, items.size, error)
        if (items.isEmpty()) {
            val message = when {
                snapshot != null -> "没有符合题材的剧目"
                error != null -> "榜单加载失败，点按刷新重试"
                else -> "正在读取榜单…"
            }
            body.addView(label(message, 15f, TvStyle.muted).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -1))
            return
        }
        val list = ContinuousCatalogView(
            activity,
            6,
            controls[state.board.ordinal],
            { item, recycled ->
                (recycled as? CatalogPosterCard ?: CatalogPosterCard(activity)).apply {
                    show(item, snapshot?.ranking?.positions?.get(item.id))
                    cover(image, item.cover)
                }
            },
            { state.selectedId = it.id },
            open,
            more,
            {},
        )
        list.setSideEntry(nav[2])
        browser = list
        body.addView(list, LayoutParams(-1, -1))
        list.show(items, false, position)
        controls.forEach { it.nextFocusDownId = list.grid.id }
        if (enterGrid && !isInTouchMode) list.enter()
    }
}
