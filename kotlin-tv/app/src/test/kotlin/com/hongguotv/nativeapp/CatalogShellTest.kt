package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 26], qualifiers = "w320dp-h640dp-port-mdpi")
class CatalogShellTest {
    @Test
    fun fiveTouchDestinationsFitTheBottomAndShortcutsStayReachable() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val destinations = mutableListOf<Int>()
        val shell = CatalogShell(controller.get()) { destinations += it }
        try {
            controller.get().setContentView(shell)
            layout(shell)
            val tabs = listOf(0, 1, 7, 2, 3).map { shell.navigation[it] }
            val bar = tabs.first().parent as View
            assertEquals(shell.height, bar.bottom)
            assertTrue(shell.body.bottom <= bar.top)
            assertEquals(listOf("首页", "找剧", "搜索", "排行榜", "我的"), tabs.map { it.text.toString() })
            assertTrue(tabs.all { it.parent === bar && it.width >= 48 && it.height >= 48 && !it.isFocusableInTouchMode })
            assertTrue(tabs.zipWithNext().all { (left, right) -> left.right <= right.left })
            listOf(1, 7, 2, 3, 8, 6).forEach {
                val control = shell.navigation[it]
                assertTrue(control.isShown && !control.isFocusableInTouchMode)
                assertTrue(control.performClick())
            }
            assertEquals(listOf(1, 7, 2, 3, 8, 6), destinations)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun personalSubpagesKeepOneSelectedTabAcrossRebuildingTheBody() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val shell = CatalogShell(controller.get()) {}
        try {
            controller.get().setContentView(shell)
            val personal = shell.navigation[3]
            val ids = shell.navigation.map { it.id }
            shell.body.addView(TextView(controller.get()).apply { text = "首页" })
            shell.clearBody()
            shell.select(5)
            assertEquals(0, shell.body.childCount)
            assertEquals(ids, shell.navigation.map { it.id })
            assertSame(personal, shell.navigation[4])
            assertSame(personal, shell.navigation[5])
            assertTrue(personal.isSelected)
            assertFalse(shell.navigation[0].isSelected)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 640)
    }
}
