// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.hongguotv.core.Series
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], qualifiers = "w320dp-h640dp-port-mdpi")
class ContinuousCatalogViewTest {
    @Test
    fun narrowGridKeepsPostingClicksAndPagesWithoutDuplicates() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var calls = 0
        var opened = ""
        var managed = ""
        val view = ContinuousCatalogView(
            activity,
            6,
            TextView(activity),
            { item, recycled -> (recycled as? CatalogPosterCard ?: CatalogPosterCard(activity)).apply { show(item) } },
            {},
            { opened = it.id },
            { item, _ -> managed = item.id },
            { calls++ },
        )
        try {
            activity.setContentView(view)
            view.show(rows(1..16), true)
            layout(view)
            assertEquals(2, view.grid.numColumns)
            assertTrue(view.grid.childCount < 16)
            assertTrue(view.next.isShown && view.next.height >= 48)
            assertEquals(0, calls)

            val poster = view.grid.getChildAt(0)
            view.grid.performItemClick(poster, 0, view.grid.adapter.getItemId(0))
            view.grid.onItemLongClickListener.onItemLongClick(view.grid, poster, 0, view.grid.adapter.getItemId(0))
            assertEquals("1", opened)
            assertEquals("1", managed)
            view.next.performClick()
            assertEquals(1, calls)
            view.append(rows(15..24), false)
            layout(view)
            assertEquals(24, view.grid.adapter.count)
            assertEquals("1", view.capture()?.id)
            assertEquals(View.GONE, view.next.visibility)
        } finally {
            view.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun searchUsesFullWidthRowsAndRestoresTheCatalogPosition() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = ContinuousCatalogView(
            activity,
            4,
            TextView(activity),
            { item, recycled -> (recycled as? SearchResultCard ?: SearchResultCard(activity)).apply { show(item) } },
            {},
            {},
            { _, _ -> },
            {},
        )
        try {
            view.useSearchPresentation()
            activity.setContentView(view)
            val saved = ContinuousCatalogView.Position("9", 0)
            view.show(rows(1..24), false, saved)
            layout(view)
            assertEquals(1, view.grid.numColumns)
            assertEquals("9", view.capture()?.id)
            view.top.performClick()
            layout(view)
            assertEquals("1", view.capture()?.id)
        } finally {
            view.close()
            controller.pause().stop().destroy()
        }
    }

    private fun rows(range: IntRange) = range.map { Series(it.toString(), "作品$it") }

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(530, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 320, 530)
        ShadowLooper.idleMainLooper()
    }
}
