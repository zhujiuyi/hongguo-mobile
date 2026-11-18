// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w960dp-h540dp-land-mdpi")
class TvChoiceDialogTest {
    @Test fun movingFocusDoesNotChangeSavedChoiceAndConfirmReturnsToAnchor() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val anchor = TextView(activity).apply { isFocusableInTouchMode = true }
        activity.setContentView(anchor)
        val tools = TvTools(activity)
        var chosen = -1
        try {
            tools.choose("启动页面", listOf("首页", "找剧", "观看历史"), anchor, { chosen = it }, selected = 2)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val root = dialog.findViewById<View>(android.R.id.content)
            val current = root.findViewWithTag<LinearLayout>("choice-2")
            val other = root.findViewWithTag<LinearLayout>("choice-0")
            assertTrue(current.hasFocus())
            assertEquals("✓", (current.getChildAt(1) as TextView).text.toString())
            other.requestFocus()
            assertEquals(-1, chosen)
            assertEquals("✓", (current.getChildAt(1) as TextView).text.toString())
            other.performClick()
            ShadowLooper.idleMainLooper()
            assertEquals(0, chosen)
            assertFalse(tools.showing)
            assertTrue(anchor.hasFocus())
        } finally { tools.destroy(); controller.pause().stop().destroy() }
    }

    @Test fun longListFitsScreenAndCancellationDoesNotChooseAnything() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val tools = TvTools(activity)
        var chosen = -1
        try {
            tools.choose("选择", (1..30).map { "选项 $it" }, null, { chosen = it }, selected = 29)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val root = dialog.findViewById<View>(android.R.id.content)
            assertTrue(root.findViewWithTag<View>("choice-29").hasFocus())
            assertTrue(dialog.window!!.attributes.height <= activity.resources.displayMetrics.heightPixels * .84f)
            dialog.cancel()
            assertEquals(-1, chosen)
            assertFalse(tools.showing)
        } finally { tools.destroy(); controller.pause().stop().destroy() }
    }

    @Test fun nestedPickerReturnsOnceFromEitherBackControl() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val tools = TvTools(controller.get())
        var returns = 0
        var chosen = -1
        fun show() {
            tools.choose("高级设置", listOf("画面", "声音"), null, { chosen = it }, onBack = { returns++ })
        }
        try {
            show()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val root = dialog.findViewById<View>(android.R.id.content)
            val firstChoice = root.findViewWithTag<View>("choice-0")
            val back = root.findViewById<BackIconButton>(firstChoice.nextFocusUpId)
            assertEquals("返回上一级", back.contentDescription)
            assertEquals(firstChoice.id, back.nextFocusDownId)
            assertTrue(firstChoice.hasFocus())
            back.performClick()
            ShadowLooper.idleMainLooper()
            assertFalse(dialog.isShowing)
            assertEquals(1, returns)

            show()
            ShadowAlertDialog.getLatestAlertDialog().onBackPressed()
            ShadowLooper.idleMainLooper()
            assertFalse(tools.showing)
            assertEquals(2, returns)
            assertEquals(-1, chosen)
        } finally { tools.destroy(); controller.pause().stop().destroy() }
    }
}
