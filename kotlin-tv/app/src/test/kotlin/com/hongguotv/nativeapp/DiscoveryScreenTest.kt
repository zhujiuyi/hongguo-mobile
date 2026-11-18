// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hongguotv.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w320dp-h640dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DiscoveryScreenTest {
    private val groups = listOf(
        DiscoveryGroup("genre", "类型", listOf(DiscoveryFilter("short_play", "真人剧"))),
        DiscoveryGroup("category_dim_role", "设定", (1..30).map { DiscoveryFilter("tag_$it", "设定$it") }),
        DiscoveryGroup("category_dim_epoch", "背景", listOf(DiscoveryFilter("cate_1", "都市"))),
    )

    @Test
    fun touchFiltersReachTheLastOptionAndCanBeReset() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val state = DiscoveryBrowserState()
        val observed = mutableListOf<DiscoverySelection>()
        val screen = screen(activity, state) { selection, _, done ->
            observed += selection
            done(FilteredDiscoveryPage(listOf(Series("1", "作品")), groups, null), null)
        }
        try {
            activity.setContentView(screen)
            screen.start(false)
            layout(screen)
            button(screen, "真人剧").performClick()
            button(screen, "筛选").performClick()
            chooseFromDialog { it.startsWith("设定 · ") }
            val options = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(views(options.window!!.decorView).filterIsInstance<BackIconButton>().single().performClick())
            assertFalse(options.isShowing)
            assertEquals(mapOf("genre" to "short_play"), observed.last().values)
            choose(screen, "设定", "设定30")
            assertEquals(
                mapOf("genre" to "short_play", "category_dim_role" to "tag_30"),
                observed.last().values,
            )
            assertTrue(views(screen).filterIsInstance<TextView>().any { it.text == "设定：设定30" })
            button(screen, "重置").performClick()
            assertTrue(state.selection.values.isEmpty())
            assertTrue(observed.last().values.isEmpty())
        } finally {
            screen.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun newerFilterRequestWinsAndClosedPageIgnoresCallbacks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val callbacks = mutableListOf<(FilteredDiscoveryPage?, Throwable?) -> Unit>()
        val state = DiscoveryBrowserState()
        val screen = screen(activity, state) { _, _, done -> callbacks += done }
        try {
            activity.setContentView(screen)
            screen.start(false)
            callbacks[0](page("1"), null)
            choose(screen, "设定", "设定1")
            choose(screen, "背景", "都市")
            callbacks[1](page("2"), null)
            assertTrue(state.items.isEmpty())
            callbacks[2](page("3"), null)
            assertEquals(listOf("3"), state.items.map { it.id })
            assertEquals(
                mapOf("category_dim_role" to "tag_1", "category_dim_epoch" to "cate_1"),
                state.selection.values,
            )
            button(screen, "刷新").performClick()
            screen.close()
            callbacks[3](page("4"), null)
            assertEquals(listOf("3"), state.items.map { it.id })
        } finally {
            screen.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun reopeningDiscoveryRetainsTheFilterAndCatalogPosition() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val state = DiscoveryBrowserState().apply {
            loaded = true
            groups = this@DiscoveryScreenTest.groups
            selection = DiscoverySelection(mapOf("category_dim_role" to "tag_29"))
            items = (1..32).map { Series("$it", "作品$it") }
        }
        fun createScreen() = screen(activity, state) { _, _, _ -> fail("Unexpected discovery reload") }
        val first = createScreen()
        var returned: DiscoveryScreen? = null
        try {
            activity.setContentView(first)
            first.start(false)
            layout(first)
            val firstGrid = views(first).filterIsInstance<ContinuousCatalogView>().single()
            firstGrid.grid.setSelection(18)
            layout(first)
            val saved = firstGrid.capture()
            assertEquals("19", saved?.id)
            first.close()
            assertEquals(saved?.id, state.position?.first)

            val next = createScreen()
            returned = next
            val nextGrid = views(next).filterIsInstance<ContinuousCatalogView>().single()
            assertEquals(saved?.id, nextGrid.capture()?.id)
            activity.setContentView(next)
            next.start(false)
            layout(next)
            val restored = nextGrid.grid
            assertEquals("Visible ${restored.firstVisiblePosition}..${restored.lastVisiblePosition}", saved?.id, nextGrid.capture()?.id)
            assertTrue(views(next).filterIsInstance<TextView>().any { it.text == "设定：设定29" })
            next.backToTop()
            layout(next)
            assertEquals("1", nextGrid.capture()?.id)
        } finally {
            first.close()
            returned?.close()
            controller.pause().stop().destroy()
        }
    }

    private fun screen(
        activity: Activity,
        state: DiscoveryBrowserState,
        fetch: (DiscoverySelection, DiscoveryCursor?, (FilteredDiscoveryPage?, Throwable?) -> Unit) -> Unit,
    ) = DiscoveryScreen(activity, state, TextView(activity), { _, _ -> }, {}, { _, _ -> }, fetch)

    private fun choose(screen: DiscoveryScreen, group: String, option: String) {
        views(screen).filterIsInstance<TextView>().single { it.text.startsWith("筛选") }.performClick()
        chooseFromDialog { it.startsWith("$group · ") }
        chooseFromDialog { it == option }
    }

    private fun chooseFromDialog(matches: (String) -> Boolean) {
        val dialog: AlertDialog = ShadowAlertDialog.getLatestAlertDialog()
        val list = dialog.listView
        val index = (0 until list.adapter.count).single { matches(list.adapter.getItem(it).toString()) }
        list.performItemClick(list.adapter.getView(index, null, list), index, list.adapter.getItemId(index))
    }

    private fun button(view: View, label: String) =
        views(view).filterIsInstance<TextView>().single { it.text == label && it.isClickable }

    private fun page(id: String) = FilteredDiscoveryPage(listOf(Series(id, "作品$id")), groups, null)

    private fun views(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 560)
        ShadowLooper.idleMainLooper()
    }
}
