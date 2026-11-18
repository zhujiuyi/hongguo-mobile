package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.hongguotv.core.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 26], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class InteractionFlowTest {
    private fun get(target: Any, name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun set(target: Any, name: String, value: Any?) { target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value) }
    private fun flush(account: AccountController) {
        ShadowLooper.idleMainLooper() // AlertDialog button listeners are posted to the UI queue.
        (get(account, "worker") as ExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        ShadowLooper.idleMainLooper()
    }
    private fun texts(view: View): List<TextView> = if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else if (view is TextView) listOf(view) else emptyList()

    private fun tap(view: View) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action, view.width / 2f, view.height / 2f, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        ShadowLooper.idleMainLooper()
    }

    private fun layout(dialog: AlertDialog): View {
        val decor = dialog.window!!.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(dialog.window!!.attributes.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST),
        )
        decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        return decor
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun playbackSelectorSupportsOneTapAndAdaptiveColumns() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        var played = -1
        var located = -1
        var closed = 0
        lateinit var panel: EpisodePanel
        panel = EpisodePanel(
            activity, 65, 24,
            { current, _, _ -> located = current },
            { played = it; panel.dismiss() },
            { closed++ },
            22,
        )
        try {
            val host = LinearLayout(activity)
            val anchor = TextView(activity).apply {
                id = View.generateViewId()
                isFocusableInTouchMode = true
            }
            host.addView(anchor)
            activity.setContentView(host)
            val oldDown = anchor.nextFocusDownId
            panel.show(host, anchor)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val decor = layout(dialog)
            assertEquals(1, host.childCount)
            val current = texts(decor).single { it.text == "25\n当前" }
            assertTrue(current.isSelected)
            assertFalse(current.isFocusableInTouchMode)
            val cells = texts(decor).filter { it.tag?.toString()?.startsWith("episode-") == true && !it.tag.toString().startsWith("episode-group-") }
            assertEquals(20, cells.size)
            val columns = (cells.first().parent as ViewGroup).childCount
            assertTrue(columns in 2..6)
            assertTrue(cells.first().width >= 48)
            assertEquals(cells[columns].id, cells.first().nextFocusDownId)
            assertEquals(listOf("1–20", "21–40", "41–60", "61–65"), texts(decor).filter { it.tag?.toString()?.startsWith("episode-group-") == true }.map { it.text.toString() })
            assertTrue(texts(decor).any { it.text == "23\n上次" })
            tap(texts(decor).single { it.text == "跳转集数" })
            assertEquals(24, located)
            tap(decor.findViewWithTag<View>("episode-group-3"))
            layout(dialog)
            assertTrue(decor.findViewWithTag<View>("episode-group-3").isSelected)
            assertEquals(-1, played)
            tap(decor.findViewWithTag<View>("episode-64"))
            assertEquals(64, played)
            assertEquals(1, closed)
            assertFalse(dialog.isShowing)
            assertTrue(anchor.hasFocus())
            assertEquals(oldDown, anchor.nextFocusDownId)

            panel.show(host, anchor)
            ShadowAlertDialog.getLatestAlertDialog().onBackPressed()
            ShadowLooper.idleMainLooper()
            assertEquals(2, closed)
        } finally { panel.dismiss(); controller.pause().stop().destroy() }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun accountOffersPhoneLoginFirst() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val account = AccountController(controller.get()) {}
        try {
            flush(account)
            account.show()
            val choices = ShadowAlertDialog.getLatestAlertDialog().listView
            assertEquals("手机号 + 验证码登录", choices.adapter.getItem(0).toString())
            choices.performItemClick(choices.adapter.getView(0, null, choices), 0, 0)
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val fields = texts(dialog.window!!.decorView).filterIsInstance<EditText>()
            assertEquals(listOf("手机号", "短信验证码"), fields.map { it.hint.toString() })
            assertTrue(fields[0].nextFocusForwardId == fields[1].id)
        } finally {
            account.destroy()
            controller.pause().stop().destroy()
        }
    }

    @Test fun episodeInputKeepsInvalidInputOpenAndValidInputPlaysOnce() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val tools = TvTools(activity.get())
        val played = mutableListOf<Int>()
        try {
            tools.episodePicker(5, 0, null) { played += it }
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("播放此集", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_9))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertTrue(dialog.isShowing)
            assertTrue(played.isEmpty())
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(listOf(2), played)
            assertFalse(dialog.isShowing)
        } finally { tools.destroy(); activity.pause().stop().destroy() }
    }

    private fun historyFixture(test: (AccountController, () -> Unit, (Boolean) -> Unit, MutableList<CloudProgress>) -> Unit) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val played = mutableListOf<CloudProgress>()
        val account = AccountController(activity.get()) { played += it }
        var fail = false
        try {
            flush(account)
            set(account, "credentials", AccountCredentials("test-only"))
            set(account, "client", CloudAccountClient(OkHttpClient.Builder().addInterceptor { chain ->
                if (fail) throw java.io.IOException("fixture unavailable")
                val body = when {
                    chain.request().url.encodedPath.contains("user/info") -> """{"code":0,"data":{"biz_user_id":"1","user_name":"测试"}}"""
                    chain.request().url.encodedPath.contains("read_progress") -> """{"code":0,"video_data":[{"series_id":"123","vid":"456","vid_index":2,"current_play_time":12000}]}"""
                    else -> """{"code":0,"data":[{"book_id":"123","book_name":"云端测试剧"}]}"""
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
            }.build()))
            val show = { account.javaClass.getDeclaredMethod("showHistory").apply { isAccessible = true }.invoke(account); Unit }
            test(account, show, { fail = it }, played)
        } finally { account.destroy(); activity.pause().stop().destroy() }
    }

    @Test fun cloudContinueLoadsAndOpensRecordsWithoutReturningToAccountMenu() = historyFixture { account, show, _, played ->
        show()
        flush(account)
        val list = ShadowAlertDialog.getLatestAlertDialog().listView
        assertEquals(1, list.adapter.count)
        list.performItemClick(list.adapter.getView(0, null, list), 0, 0)
        assertEquals("123", played.single().series.id)
        assertEquals(12000L, played.single().position)
    }

    @Test fun cancelledHistoryLoadNeverReopensDialog() = historyFixture { account, show, _, _ ->
        show()
        val loading = ShadowAlertDialog.getLatestAlertDialog()
        loading.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        flush(account)
        assertSame(loading, ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(loading.isShowing)
    }

    @Test fun historyFailureRetriesInPlaceWithoutReopeningAccountMenu() = historyFixture { account, show, fail, _ ->
        fail(true); show(); flush(account)
        val error = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("重试", error.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        fail(false); error.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); flush(account)
        assertEquals(1, ShadowAlertDialog.getLatestAlertDialog().listView.adapter.count)
    }
}
