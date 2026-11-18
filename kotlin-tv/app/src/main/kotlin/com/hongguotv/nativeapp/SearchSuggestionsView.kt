// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.core.RequestScope
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Cancellable suggestion requests are independent of search result pagination. */
internal class SearchSuggestionsView(
    context: Context,
    private val input: EditText,
    private val lower: View,
    private val fetch: (String) -> List<String>,
    private val search: (String) -> Unit,
) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val line = LinearLayout(context).apply { orientation = VERTICAL }
    private val chips = mutableListOf<TextView>()
    private var pending: Runnable? = null
    private var job: Future<*>? = null
    private var scope: RequestScope? = null
    private var ticket = 0
    private var closed = false
    var navigationChanged: (() -> Unit)? = null

    fun entry(): View? = chips.firstOrNull()
    fun values(): List<String> = chips.map { it.text.toString() }
    fun restore(values: List<String>) = render(values)
    fun viewAt(index: Int): View? = chips.getOrNull(index)
    fun selectedIndex(): Int = chips.indexOfFirst { it.hasFocus() }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            schedule(s?.toString().orEmpty())
        }
    }

    init {
        orientation = VERTICAL
        addView(TextView(context).apply {
            text = "搜索建议"
            textSize = 14f
            setTextColor(TvStyle.muted)
            setPadding(0, 0, 0, dp(8))
        })
        addView(line, LayoutParams(-1, -2))
        visibility = GONE
        input.addTextChangedListener(watcher)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun schedule(value: String) {
        cancel()
        val keyword = value.trim()
        if (closed || keyword.isEmpty()) return
        val expected = ticket
        val task = Runnable {
            pending = null
            val request = RequestScope()
            scope = request
            job = worker.submit {
                val result = runCatching { request.run { fetch(keyword) } }
                main.post {
                    if (closed || ticket != expected || input.text.toString().trim() != keyword) return@post
                    render(result.getOrDefault(emptyList()), result.isFailure)
                }
            }
        }
        pending = task
        main.postDelayed(task, 350)
    }

    private fun render(values: List<String>, failed: Boolean = false) {
        val focusedValue = chips.firstOrNull { it.hasFocus() }?.text?.toString()
        line.removeAllViews()
        chips.clear()
        values.filter(String::isNotBlank).distinct().take(8).forEach { value ->
            val chip = TextView(context).apply {
                id = View.generateViewId()
                text = value
                textSize = 15f
                maxLines = 2
                minHeight = dp(48)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                setTextColor(TvStyle.text)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = catalogControlBackground(context, false)
                setOnFocusChangeListener { _, focused -> background = catalogControlBackground(context, focused) }
                setOnClickListener { search(value) }
            }
            chips += chip
            line.addView(chip, LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        if (failed) line.addView(TextView(context).apply {
            text = "暂时无法读取建议，可直接搜索"
            textSize = 14f
            setTextColor(TvStyle.muted)
            setPadding(0, dp(6), 0, dp(6))
        })
        visibility = if (chips.isNotEmpty() || failed) VISIBLE else GONE
        updateNavigation()
        if (focusedValue != null && !isInTouchMode)
            (chips.firstOrNull { it.text.toString() == focusedValue } ?: input).requestFocus()
    }

    fun updateNavigation() {
        chips.forEachIndexed { index, chip ->
            chip.nextFocusUpId = chips.getOrNull(index - 1)?.id ?: input.id
            chip.nextFocusDownId = chips.getOrNull(index + 1)?.id ?: lower.id
        }
        navigationChanged?.invoke()
    }

    fun cancel() {
        ticket++
        pending?.let(main::removeCallbacks)
        pending = null
        scope?.cancel()
        scope = null
        job?.cancel(true)
        job = null
        render(emptyList())
    }

    fun close() {
        if (closed) return
        closed = true
        input.removeTextChangedListener(watcher)
        navigationChanged = null
        cancel()
        worker.shutdownNow()
    }
}
