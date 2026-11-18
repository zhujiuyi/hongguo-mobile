// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w320dp-h640dp-port-mdpi")
class TvSearchPanelTest {
    @Test
    fun phoneSearchSubmitsFromTheKeyboardButtonAndHistory() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val submitted = mutableListOf<String>()
        val panel = TvSearchPanel(activity, "", listOf("历史剧名"), TextView(activity), { emptyList() }) {
            submitted += it
        }
        try {
            activity.setContentView(panel)
            layout(panel)
            assertTrue(panel.input.showSoftInputOnFocus)
            assertEquals(EditorInfo.IME_ACTION_SEARCH, panel.input.imeOptions and EditorInfo.IME_MASK_ACTION)
            assertTrue(panel.input.width > panel.searchButton.width)
            assertTrue(panel.searchButton.height >= 48)
            assertFalse(panel.searchButton.isFocusableInTouchMode)
            assertFalse(panel.searchButton.isEnabled)
            assertTrue(texts(panel).single { it.text == "历史剧名" }.performClick())

            panel.input.setText(" 测试剧名 ")
            assertTrue(panel.searchButton.isEnabled)
            assertFalse(texts(panel).single { it.text == "历史剧名" }.isShown)
            panel.input.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
            panel.input.setText("另一个剧名")
            panel.searchButton.performClick()
            assertEquals(listOf("历史剧名", "测试剧名", "另一个剧名"), submitted)
        } finally {
            panel.suggestions.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun phoneResultsCanReturnToTheDraftAndSelectASuggestion() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var submitted = ""
        val panel = TvSearchPanel(activity, "", emptyList(), TextView(activity), { emptyList() }) {
            submitted = it
        }
        try {
            activity.setContentView(panel)
            val words = listOf("重生", "重生螳螂觉醒无限吞噬进化")
            panel.restore(TvSearchPanel.EditState("cs", words, 1, -1))
            layout(panel)
            assertEquals(320, panel.suggestions.viewAt(1)?.width)
            assertTrue(panel.suggestions.viewAt(1)!!.height >= 48)
            panel.resultTitle("重生")
            panel.showResults()
            assertTrue(panel.showingResults)
            assertFalse(panel.input.isShown)
            assertTrue(panel.back())
            assertTrue(panel.input.isShown)
            assertEquals("cs", panel.input.text.toString())
            assertEquals(words, panel.suggestions.values())
            panel.suggestions.viewAt(1)!!.performClick()
            assertEquals(words[1], submitted)
        } finally {
            panel.suggestions.close()
            controller.pause().stop().destroy()
        }
    }

    private fun texts(view: View): List<TextView> =
        (if (view is TextView) listOf(view) else emptyList()) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 360)
    }
}
