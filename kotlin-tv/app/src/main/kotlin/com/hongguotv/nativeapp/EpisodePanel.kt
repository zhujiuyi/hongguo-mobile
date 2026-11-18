// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Scrollable playback dialog using the detail page's episode selector. */
class EpisodePanel(
    private val activity: Activity,
    private val count: Int,
    private val current: Int,
    private val locate: (Int, View, (Int) -> Unit) -> Unit,
    private val select: (Int) -> Unit,
    private val closed: () -> Unit,
    private val lastWatched: Int? = null,
    private val lastCompleted: Boolean = false,
) {
    private var group = current / 20
    private var dialog: AlertDialog? = null
    private var anchor: View? = null

    fun show(host: LinearLayout, anchor: View) {
        if (dialog != null) return
        this.anchor = anchor
        val padding = (12 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val scroll = ScrollView(activity).apply { addView(content) }
        fun render(focusGroup: Boolean = false) {
            content.removeAllViews()
            val selector = EpisodeSelectorView(
                activity, count, current, group, lastWatched, lastCompleted,
                onGroup = { next ->
                    group = next
                    render(focusGroup = true)
                },
                onJump = { focusedEpisode, view ->
                    locate(focusedEpisode, view) { target -> if (target in 0 until count) select(target) }
                },
                onSelect = select,
            )
            content.addView(selector)
            val target =
                if (focusGroup) selector.groupButtons[group]
                else selector.episodes.getOrNull(current - group * 20) ?: selector.jump
            target.requestFocus()
            if (focusGroup && selector.isInTouchMode) scroll.scrollTo(0, 0)
        }
        val next = AlertDialog.Builder(host.context)
            .setTitle("选集 · 共 $count 集")
            .setPositiveButton("关闭", null)
            .create()
        next.setView(scroll, 0, 0, 0, 0)
        dialog = next
        next.setOnDismissListener { if (dialog === next) finish() }
        render()
        next.show()
        next.stylePanel()
        next.window?.setLayout(activity.dialogWidth(560), -2)
    }

    fun dismiss() {
        val next = dialog ?: return
        dialog = null
        next.dismiss()
        finish()
    }

    private fun finish() {
        dialog = null
        anchor?.takeIf { it.isAttachedToWindow }?.requestFocus()
        anchor = null
        closed()
    }
}
