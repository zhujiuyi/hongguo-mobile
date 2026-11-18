// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hongguotv.core.Series
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class LeftRailNavigationTest {
    private fun layout(view: View, width: Int = 960, height: Int = 540) {
        ShadowLooper.idleMainLooper()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun settingsKeepsTheChosenCategoryWhenReturning() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        fun field(name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }
        try {
            val library = field("library").get(activity) as Library
            val loader = library.javaClass.getDeclaredField("loader").apply { isAccessible = true }
            (loader.get(library) as java.util.concurrent.ExecutorService).submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            val switch = activity.javaClass.getDeclaredMethod("switchTab", Int::class.javaPrimitiveType).apply { isAccessible = true }
            switch.invoke(activity, 6)
            assertEquals(0, field("settingsGroup").getInt(activity))
            val shell = field("catalogShell").get(activity) as CatalogShell
            shell.body.findViewWithTag<View>("settings-category-3").performClick()
            switch.invoke(activity, 3)
            shell.navigation[6].performClick()
            layout(activity.window.decorView)
            assertEquals(3, field("settingsGroup").getInt(activity))
            assertTrue(views(shell.body).filterIsInstance<TextView>().any { it.isClickable && it.text == "关于红果手机版" })
        } finally { controller.destroy() }
    }

    @Test fun systemBackReturnsFromSettingsToHomeAndThenFinishes() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        fun field(target: Any, name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        try {
            val library = field(activity, "library").get(activity) as Library
            (field(library, "loader").get(library) as java.util.concurrent.ExecutorService)
                .submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            field(activity, "repository").set(activity, com.hongguotv.core.ContentRepository(
                okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline fixture") }.build()))
            activity.javaClass.getDeclaredMethod("switchTab", Int::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(activity, 6)
            val shell = field(activity, "catalogShell").get(activity) as CatalogShell
            assertTrue(shell.navigation[6].isSelected)
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(shell.navigation[0].isSelected)
            assertFalse(activity.isFinishing)
            @Suppress("DEPRECATION")
            activity.onBackPressed()
            assertTrue(activity.isFinishing)
        } finally { controller.destroy() }
    }

    @Test fun moreHotRetainsItsReturnButtonAndSystemBackRestoresHome() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        fun field(target: Any, name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
        fun show() = MainActivity::class.java.getDeclaredMethod("showCatalog", Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, false, false, false)
        try {
            val library = field(activity, "library").get(activity) as Library
            (field(library, "loader").get(library) as java.util.concurrent.ExecutorService).submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            field(activity, "repository").set(activity, com.hongguotv.core.ContentRepository(
                okhttp3.OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline fixture") }.build()))
            val homeItems = listOf(Series("123", "首页热门"))
            field(activity, "catalog").set(activity, homeItems)
            field(activity, "hasMore").setBoolean(activity, true)
            field(activity, "tab").setInt(activity, 0)
            field(activity, "page").setInt(activity, 1)
            show(); layout(activity.window.decorView)
            fun more() = views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString().startsWith("更多热门") }
            fun labels() = views(activity.window.decorView).filterIsInstance<TextView>()
            assertFalse(labels().any { it.text.toString() in listOf("漫剧", "短剧", "刷新热门") })
            more().requestFocus(); more().performClick()
            (field(activity, "workJob").get(activity) as? java.util.concurrent.Future<*>)?.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper(); layout(activity.window.decorView)
            val shell = field(activity, "catalogShell").get(activity) as CatalogShell
            val back = shell.backEntry as BackIconButton
            assertFalse(labels().any { it.text.toString() == "热门发现" })
            val comic = labels().single { it.text.toString() == "漫剧" }
            val short = labels().single { it.text.toString() == "短剧" }
            assertSame(comic.parent, short.parent)
            val topBar = back.parent as View
            assertSame(shell, topBar.parent)
            assertTrue(shell.indexOfChild(topBar) < shell.indexOfChild(shell.body))
            short.performClick()
            (field(activity, "workJob").get(activity) as? java.util.concurrent.Future<*>)?.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper(); layout(activity.window.decorView)
            assertEquals("hotMore", field(activity, "collection").get(activity))
            assertEquals(1, field(activity, "page").getInt(activity))
            assertEquals(com.hongguotv.core.ContentType.SHORT, field(activity, "hotContentType").get(activity))
            shell.backEntry!!.performClick()
            layout(activity.window.decorView)
            assertFalse(labels().any { it.text.toString() in listOf("漫剧", "短剧", "刷新热门") })
            assertEquals(1, field(activity, "page").getInt(activity))
            assertEquals(homeItems, field(activity, "catalog").get(activity))
            assertNull(field(activity, "collection").get(activity))
            assertTrue(more().isClickable && more().visibility == View.VISIBLE)
            more().performClick()
            (field(activity, "workJob").get(activity) as? java.util.concurrent.Future<*>)?.get(5, java.util.concurrent.TimeUnit.SECONDS)
            ShadowLooper.idleMainLooper()
            field(activity, "page").setInt(activity, 3)
            field(activity, "catalog").set(activity, listOf(Series("456", "第三页热门")))
            show(); layout(activity.window.decorView)
            (field(activity, "catalogShell").get(activity) as CatalogShell).navigation[0].requestFocus()
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            layout(activity.window.decorView)
            assertNull(field(activity, "collection").get(activity))
            assertEquals(1, field(activity, "page").getInt(activity))
            assertEquals(homeItems, field(activity, "catalog").get(activity))
            assertFalse(activity.isFinishing)
        } finally { controller.destroy() }
    }

    @Test fun returningToEitherResumeActionRevealsTheWholeBanner() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            val top = TextView(activity).apply { id = View.generateViewId(); isFocusable = true }
            val home = HomeScreen(activity, top, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, {})
            val spotlight = HomeSpotlight(activity, Series("1", "续播横幅测试"), "第 2 集", true, { _, _ -> }) {}
            home.setLeadingSection(spotlight)
            activity.setContentView(home)
            home.render(listOf(HomeScreen.Shelf("hot", "热门发现",
                (2..8).map { HomeScreen.Entry(Series("$it", "测试 $it"), "测试") })),
                "", false, null)
            repeat(3) { layout(home, 864, 420) }
            val scroll = views(home).filterIsInstance<android.widget.ScrollView>().single()
            val card = views(home).first { it.tag == "hot:2" }
            for (action in listOf(spotlight.play, spotlight.detail)) {
                card.requestFocus()
                scroll.scrollTo(0, spotlight.bottom)
                assertTrue("Fixture must scroll the banner away", scroll.scrollY > spotlight.top)
                action.requestFocus()
                layout(home, 864, 420)
                assertTrue(action.hasFocus())
                assertTrue("Banner top must be visible", scroll.scrollY <= spotlight.top)
                assertTrue("Banner bottom must be visible", spotlight.bottom <= scroll.scrollY + scroll.height)
                val position = home.capturePosition().copy(vertical = spotlight.bottom)
                home.restorePosition(position)
                layout(home, 864, 420)
                assertEquals("Restoration must not crop a focused banner", 0, scroll.scrollY)
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun artworkAndTextNeverShareTheSameRectangle() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        try {
            val card = CatalogPosterCard(activity)
            card.show(Series("2", "长标题作品"))
            activity.setContentView(card)
            layout(card, 160, 360)
            val posterBottom = (card.image.parent as View).bottom
            views(card).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.forEach {
                assertTrue("Metadata must be below poster", it.top >= posterBottom)
            }
            val initialHeight = card.image.height
            layout(card, 210, 420)
            assertTrue("Artwork should grow with actual column width", card.image.height > initialHeight)
        } finally { controller.pause().stop().destroy() }
    }
}
