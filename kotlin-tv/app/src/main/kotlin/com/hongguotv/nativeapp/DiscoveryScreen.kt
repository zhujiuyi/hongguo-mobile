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
import android.widget.TextView
import com.hongguotv.core.DiscoveryBrowserState
import com.hongguotv.core.DiscoveryCursor
import com.hongguotv.core.DiscoveryFilter
import com.hongguotv.core.DiscoveryGroup
import com.hongguotv.core.DiscoverySelection
import com.hongguotv.core.FilteredDiscoveryPage
import com.hongguotv.core.Series

/** Filter options are supplied by the server for the currently selected content type. */
internal class DiscoveryScreen(
    context: Context,
    private val state: DiscoveryBrowserState,
    private val upper: View,
    private val cover: (ImageView, String) -> Unit,
    private val open: (Series) -> Unit,
    private val more: (Series, View) -> Unit,
    private val fetch: (DiscoverySelection, DiscoveryCursor?, (FilteredDiscoveryPage?, Throwable?) -> Unit) -> Unit,
) : LinearLayout(context) {
    private val genreChoices = LinearLayout(context)
    private val genres = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(genreChoices, FrameLayout.LayoutParams(-2, -2))
    }
    private val summary = TextView(context).apply {
        textSize = 13f
        setTextColor(TvStyle.muted)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(5))
    }
    private val filter = button("筛选") { showFilters() }
    private val reset = button("重置") { change(DiscoverySelection()) }
    private val refresh = button("刷新") { request(null) }
    private val emptyState = TvEmptyState(context, "正在加载剧目", "")
    private val browser = ContinuousCatalogView(
        context,
        5,
        filter,
        { item, recycled ->
            (recycled as? CatalogPosterCard ?: CatalogPosterCard(context)).apply {
                show(item)
                cover(image, item.cover)
            }
        },
        { item -> if (state.position?.first != item.id) state.position = item.id to 0 },
        open,
        more,
        { state.next?.let(::request) },
    )
    private var selectedGenre: TextView? = null
    private var filterDialog: AlertDialog? = null
    private var closed = false
    private var serial = 0
    var navigationChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        val heading = TextView(context).apply {
            text = "找剧"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(TvStyle.text)
        }
        val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(heading, LayoutParams(0, -2, 1f))
        listOf(filter, reset, refresh).forEach { control ->
            bar.addView(control, LayoutParams(-2, -2).apply { marginStart = dp(6) })
        }
        addView(bar, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        addView(genres, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        addView(summary, LayoutParams(-1, -2))
        addView(browser, LayoutParams(-1, 0, 1f))
        addView(emptyState, LayoutParams(-1, 0, 1f))
        browser.setSideEntry(upper)
        renderFilters()
        showItems()
    }

    fun entry(): View = selectedGenre ?: refresh

    fun setSideEntry(view: View) { browser.setSideEntry(view) }

    fun backToTop() {
        browser.resetToTop()
        state.position = state.items.firstOrNull()?.let { it.id to 0 }
    }

    fun start(focus: Boolean) {
        if (focus && !isInTouchMode) {
            if (state.position != null && state.items.isNotEmpty()) browser.enter()
            else entry().requestFocus()
        }
        if (!state.loaded) request(null)
    }

    fun close() {
        if (closed) return
        closed = true
        serial++
        filterDialog?.dismiss()
        state.filterOffsets["genre"] = genres.scrollX
        rememberPosition()
        browser.close()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        id = View.generateViewId()
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = true
        minHeight = dp(48)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnFocusChangeListener { _, _ -> paintButton(this) }
        setOnClickListener { action() }
        paintButton(this)
    }

    private fun paintButton(button: TextView) {
        button.setTextColor(if (button.isSelected) TvStyle.accent else TvStyle.text)
        button.background = catalogControlBackground(context, button.hasFocus(), button.isSelected)
        button.alpha = if (button.isEnabled) 1f else .5f
    }

    private fun extraGroups() = state.groups.filter { it.type != "genre" && it.options.isNotEmpty() }

    private fun renderFilters() {
        val offset = if (genreChoices.childCount == 0) state.filterOffsets["genre"] ?: 0 else genres.scrollX
        val hadFocus = genreChoices.hasFocus()
        genreChoices.removeAllViews()
        selectedGenre = null
        val options = state.groups.firstOrNull { it.type == "genre" }?.options.orEmpty()
        val selectedId = state.selection.values["genre"].orEmpty()
        (listOf(DiscoveryFilter("", "全部类型")) + options).forEach { option ->
            val chip = button(option.label) {
                if (option.id != state.selection.values["genre"].orEmpty())
                    change(state.selection.choose("genre", option.id))
            }
            chip.maxWidth = dp(188)
            chip.isSelected = option.id == selectedId
            if (chip.isSelected) selectedGenre = chip
            paintButton(chip)
            genreChoices.addView(chip, LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            chip.nextFocusDownId = browser.grid.id
        }
        selectedGenre?.let { chip ->
            genres.post {
                if (closed || chip.parent !== genreChoices) return@post
                val target = if (chip.left < offset || chip.right > offset + genres.width)
                    (chip.left - dp(6)).coerceAtLeast(0) else offset
                genres.scrollTo(target, 0)
                if (hadFocus && !isInTouchMode) chip.requestFocus()
            }
        }
        val selectedCount = state.selection.values.count { (type, _) -> type != "genre" }
        filter.text = if (selectedCount == 0) "筛选" else "筛选 ($selectedCount)"
        filter.isEnabled = extraGroups().isNotEmpty()
        paintButton(filter)
        reset.visibility = if (state.selection.values.isNotEmpty()) VISIBLE else GONE
        navigationChanged?.invoke()
    }

    private fun showFilters() {
        val groups = extraGroups()
        if (groups.isEmpty()) return
        val labels = groups.map { group -> "${group.label} · ${selectionLabel(group)}" }.toTypedArray()
        showDialog(
            "筛选",
            AlertDialog.Builder(context)
                .setItems(labels) { dialog, index ->
                    dialog.dismiss()
                    showOptions(groups[index])
                },
        )
    }

    private fun showOptions(group: DiscoveryGroup) {
        val options = listOf(DiscoveryFilter("", "全部")) + group.options
        val selected = state.selection.values[group.type].orEmpty()
        showDialog(
            group.label,
            AlertDialog.Builder(context)
                .setSingleChoiceItems(options.map { it.label }.toTypedArray(), options.indexOfFirst { it.id == selected }) { dialog, index ->
                    dialog.dismiss()
                    if (!closed && options[index].id != selected)
                        change(state.selection.choose(group.type, options[index].id))
                },
        )
    }

    private fun showDialog(title: String, builder: AlertDialog.Builder) {
        filterDialog?.dismiss()
        val dialogContext = builder.context
        val tint = TextView(dialogContext).currentTextColor
        val back = BackIconButton(dialogContext, tint = tint)
        val header = dialogBackHeader(dialogContext, title, back, tint).apply {
            setPadding(dp(12), dp(4), dp(12), 0)
        }
        val dialog = builder.setCustomTitle(header).create()
        back.setOnClickListener { dialog.dismiss() }
        filterDialog = dialog
        dialog.setOnDismissListener {
            if (filterDialog === dialog) {
                filterDialog = null
                if (!closed && !isInTouchMode) filter.requestFocus()
            }
        }
        dialog.show()
    }

    private fun selectionLabel(group: DiscoveryGroup): String {
        val id = state.selection.values[group.type] ?: return "全部"
        return group.options.firstOrNull { it.id == id }?.label ?: id
    }

    private fun selectionSummary() = extraGroups()
        .filter { it.type in state.selection.values }
        .joinToString(" · ") { "${it.label}：${selectionLabel(it)}" }

    private fun setSummary(text: String) {
        summary.text = text
        summary.visibility = if (text.isBlank()) GONE else VISIBLE
    }

    private fun rememberPosition() {
        browser.capture()?.let { state.position = it.id to it.top }
    }

    private fun showItems(hasMore: Boolean = state.next != null) {
        browser.show(
            state.items,
            hasMore,
            state.position?.let { ContinuousCatalogView.Position(it.first, it.second) },
        )
        browser.visibility = if (state.loaded) VISIBLE else GONE
        emptyState.visibility = if (state.loaded) GONE else VISIBLE
        setSummary(selectionSummary())
    }

    private fun change(selection: DiscoverySelection) {
        if (selection == state.selection) return
        if (selection.values["genre"] != state.selection.values["genre"] || selection.values.isEmpty())
            state.groups = state.groups.filter { it.type == "genre" }
        state.selection = selection
        state.items = emptyList()
        state.next = null
        state.loaded = false
        state.position = null
        renderFilters()
        showItems()
        request(null)
    }

    private fun request(cursor: DiscoveryCursor?) {
        val ticket = ++serial
        refresh.isEnabled = false
        paintButton(refresh)
        if (cursor == null && state.loaded) {
            rememberPosition()
            showItems(hasMore = false)
            setSummary("正在更新…")
        }
        if (!state.loaded) {
            emptyState.heading.text = "正在加载剧目"
            emptyState.description.text = ""
        }
        fetch(state.selection, cursor) { result, error ->
            if (closed || ticket != serial) return@fetch
            refresh.isEnabled = true
            paintButton(refresh)
            if (result != null && cursor == null) {
                if (state.loaded) rememberPosition()
                state.items = result.items
                state.groups = result.groups
                state.loaded = true
                state.next = result.next
                renderFilters()
                showItems()
            } else if (result != null) {
                val seen = state.items.mapTo(HashSet()) { it.id }
                state.items = state.items + result.items.filter { seen.add(it.id) }
                state.next = result.next
                browser.append(result.items, result.next != null)
            } else if (cursor != null) {
                browser.fail(error)
            } else if (state.loaded) {
                rememberPosition()
                showItems()
                setSummary("更新失败，请刷新重试")
            } else {
                emptyState.heading.text = "剧目加载失败"
                emptyState.description.text = "点按刷新重试"
            }
        }
    }
}
