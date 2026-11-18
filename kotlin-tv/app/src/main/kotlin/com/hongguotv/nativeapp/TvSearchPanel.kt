// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.graphics.Typeface
import android.text.InputFilter
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal class TvSearchPanel(
    context: Context,
    initial: String,
    history: List<String>,
    private val upper: View,
    fetch: (String) -> List<String>,
    private val search: (String) -> Unit,
) : LinearLayout(context) {
    data class EditState(val draft: String, val words: List<String>, val word: Int, val recent: Int)

    val input = EditText(context).apply {
        id = View.generateViewId()
        hint = "剧名 / 拼音首字母"
        textSize = 16f
        setTextColor(TvStyle.text)
        setHintTextColor(TvStyle.muted)
        isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(80))
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setText(initial)
        setSelection(text.length)
        minHeight = dp(50)
        background = TvStyle.shape(context, TvStyle.surface, TvStyle.outline)
        setPadding(dp(14), 0, dp(12), 0)
    }

    private val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    private val editor = LinearLayout(context).apply { orientation = VERTICAL }
    private val resultStage = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
    private val resultHeading = TextView(context).apply {
        text = "搜索结果"
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(TvStyle.text)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val backButton = action("修改搜索") {
        if (back()) {
            input.requestFocus()
            input.post {
                if (input.isShown && input.hasFocus()) keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
    private val recents = mutableListOf<TextView>()
    private val historyGroup = LinearLayout(context).apply { orientation = VERTICAL }
    private var editingFocus: View? = null

    val results = LinearLayout(context).apply { orientation = VERTICAL }
    val searchButton = action("搜索") { submit(input.text.toString()) }
    val suggestions = SearchSuggestionsView(context, input, upper, fetch, ::submit)
    var resultEntry: () -> View? = { null }
    var showingResults = false
        private set

    init {
        orientation = VERTICAL
        addView(editor, LayoutParams(-1, -1))
        addView(resultStage, LayoutParams(-1, -1))

        val searchRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        searchRow.addView(input, LayoutParams(0, -2, 1f))
        searchRow.addView(searchButton, LayoutParams(-2, -2).apply { marginStart = dp(8) })
        editor.addView(searchRow, LayoutParams(-1, -2))

        val choices = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(12), 0, dp(8))
        }
        choices.addView(suggestions, LayoutParams(-1, -2))
        choices.addView(historyGroup, LayoutParams(-1, -2))
        editor.addView(ScrollView(context).apply { addView(choices) }, LayoutParams(-1, 0, 1f))

        val recentQueries = history.distinct().take(8)
        if (recentQueries.isNotEmpty()) {
            historyGroup.addView(TextView(context).apply {
                text = "最近搜索"
                textSize = 14f
                setTextColor(TvStyle.muted)
                setPadding(0, 0, 0, dp(8))
            })
            recentQueries.forEach { value ->
                val recent = action(value) { submit(value) }.apply {
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                }
                recents += recent
                historyGroup.addView(recent, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }

        val resultHeader = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        resultHeader.addView(backButton, LayoutParams(-2, -2).apply { marginEnd = dp(12) })
        resultHeader.addView(resultHeading, LayoutParams(0, -2, 1f))
        resultStage.addView(resultHeader, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        resultStage.addView(results, LayoutParams(-1, 0, 1f))

        input.setOnEditorActionListener { _, action, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER || event?.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (action != EditorInfo.IME_ACTION_SEARCH && !enter) {
                false
            } else {
                if (event == null || (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0))
                    submit(input.text.toString())
                true
            }
        }
        suggestions.navigationChanged = ::updateNavigation
        updateNavigation()
    }

    fun capture() = EditState(
        input.text.toString(),
        suggestions.values(),
        suggestions.selectedIndex(),
        recents.indexOfFirst { it.hasFocus() },
    )

    fun restore(state: EditState) {
        input.setText(state.draft)
        input.setSelection(input.text.length)
        suggestions.cancel()
        suggestions.restore(state.words)
        editingFocus = suggestions.viewAt(state.word) ?: recents.getOrNull(state.recent)
        updateNavigation()
    }

    fun showResults() {
        if (!showingResults) editingFocus = editor.findFocus() ?: editingFocus
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)
        showingResults = true
        editor.visibility = GONE
        resultStage.visibility = VISIBLE
        if (!isInTouchMode) backButton.requestFocus()
    }

    fun back(): Boolean {
        if (!showingResults) return false
        showingResults = false
        resultStage.visibility = GONE
        editor.visibility = VISIBLE
        if (!isInTouchMode && editingFocus?.requestFocus() != true) input.requestFocus()
        updateNavigation()
        return true
    }

    fun resultTitle(query: String) { resultHeading.text = query }

    fun resultBackEntry(): View = backButton

    fun middleEntry(): View? = suggestions.entry() ?: recents.firstOrNull()?.takeIf { input.text.isBlank() }

    fun entry(): View = if (showingResults) backButton else input

    fun updateNavigation() {
        historyGroup.visibility = if (recents.isNotEmpty() && input.text.isBlank()) VISIBLE else GONE
        searchButton.isEnabled = input.text.isNotBlank()
        searchButton.alpha = if (searchButton.isEnabled) 1f else .55f
        val choices = middleEntry()?.id ?: upper.id
        input.nextFocusDownId = choices
        input.nextFocusRightId = searchButton.id
        searchButton.nextFocusLeftId = input.id
        searchButton.nextFocusDownId = choices
        backButton.nextFocusDownId = resultEntry()?.id ?: upper.id
        recents.forEachIndexed { index, view ->
            view.nextFocusUpId = recents.getOrNull(index - 1)?.id ?: input.id
            view.nextFocusDownId = recents.getOrNull(index + 1)?.id ?: upper.id
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun action(value: String, click: () -> Unit) = TextView(context).apply {
        id = View.generateViewId()
        text = value
        textSize = 15f
        gravity = Gravity.CENTER
        isFocusable = true
        minHeight = dp(48)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(TvStyle.text)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = catalogControlBackground(context, false)
        setOnFocusChangeListener { _, focused -> background = catalogControlBackground(context, focused) }
        setOnClickListener { click() }
    }

    private fun submit(value: String) {
        val keyword = value.trim()
        if (keyword.isEmpty()) return
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)
        search(keyword)
    }
}
