// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hongguotv.core.BackupData
import com.hongguotv.core.EpisodeTarget
import com.hongguotv.core.LibraryTransferServer
import com.hongguotv.core.PhoneSearchServer
import java.net.Inet4Address
import java.util.concurrent.Executors

internal fun Context.dialogWidth(maxWidthDp: Int): Int {
    val metrics = resources.displayMetrics
    val screenWidthDp = resources.configuration.screenWidthDp
    val width = if (screenWidthDp > 0) (screenWidthDp * metrics.density).toInt() else metrics.widthPixels
    return minOf((maxWidthDp * metrics.density).toInt(), (width - (32 * metrics.density).toInt()).coerceAtLeast(1))
}

internal fun AlertDialog.stylePanel() {
    findViewById<TextView>(android.R.id.message)?.setTextColor(TvStyle.text)
    val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
    if (titleId != 0) findViewById<TextView>(titleId)?.setTextColor(TvStyle.text)
    listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
        .forEach { which ->
            getButton(which)?.apply {
                setTextColor(TvStyle.text)
                backgroundTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(TvStyle.raised, TvStyle.surface),
                )
            }
        }
    window?.setBackgroundDrawable(TvVisual.panel(context, modal = true))
    window?.setDimAmount(.6f)
}

/** Native modal controls kept separate from catalog/player navigation. */
class TvTools(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    val showing: Boolean
        get() = dialog?.isShowing == true

    private var phone: java.io.Closeable? = null

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 17f) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(TvStyle.text)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

    private fun column() =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }

    private fun open(next: AlertDialog, anchor: View?) {
        val previous = dialog
        dialog = null
        phone?.close()
        phone = null
        previous?.dismiss()
        dialog = next
        next.setOnDismissListener {
            if (dialog === next) {
                phone?.close()
                phone = null
                dialog = null
                if (anchor?.isAttachedToWindow == true) anchor.requestFocus()
            }
        }
        next.show()
        next.stylePanel()
        next.window?.setLayout(activity.dialogWidth(560), -2)
    }

    internal fun dispatchPhoneKey(event: android.view.KeyEvent): Boolean {
        val modal = dialog?.takeIf { it.isShowing } ?: return false
        modal.dispatchKeyEvent(event)
        return true
    }

    fun close() {
        dialog?.dismiss()
        phone?.close()
        phone = null
    }

    fun destroy() {
        close()
        worker.shutdownNow()
    }

    fun info(title: String, message: String, anchor: View?) =
        open(
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .create(),
            anchor,
        )

    fun confirm(title: String, message: String, anchor: View?, action: () -> Unit) {
        val next =
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认") { _, _ -> action() }
                .create()
        open(next, anchor)
        next.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
    }

    fun choose(
        title: String,
        labels: List<String>,
        anchor: View?,
        onChoice: (Int) -> Unit,
        extra: String? = null,
        compact: Boolean = false,
        selected: Int = -1,
        covers: List<String> = emptyList(),
        loadCover: (ImageView, String) -> Unit = { _, _ -> },
        onBack: (() -> Unit)? = null,
        onExtra: () -> Unit = {},
    ) {
        require(labels.isNotEmpty())
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(14))
            background = TvVisual.panel(activity, modal = true)
        }
        content.addView(label(title, 21f).apply {
            setTextColor(TvStyle.text)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), dp(14))
        })
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        content.addView(scroll)
        val choices = mutableListOf<View>()
        lateinit var next: AlertDialog
        val cancel = Button(activity).apply {
            id = View.generateViewId()
            text = if (onBack == null) "关闭" else "返回"
            setTextColor(TvStyle.text)
            backgroundTintList = ColorStateList.valueOf(TvStyle.raised)
            minHeight = dp(48)
            setOnClickListener {
                next.dismiss()
                if (onBack != null) main.post { onBack() }
            }
        }
        fun choice(value: String, checked: Boolean, action: () -> Unit) {
            val row = LinearLayout(activity).apply {
                id = View.generateViewId()
                tag = "choice-${choices.size}"
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                minimumHeight = dp(48)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                contentDescription = value + if (checked) "，当前已选" else ""
            }
            val name = label(value, 16f).apply { setPadding(0, 0, dp(8), 0) }
            val marker = label(if (checked) "✓" else "", 18f).apply {
                gravity = Gravity.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(0, 0, 0, 0)
            }
            covers.getOrNull(choices.size)?.takeIf { it.isNotBlank() }?.let { url ->
                val poster = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                row.addView(poster, LinearLayout.LayoutParams(dp(54), dp(72)).apply { rightMargin = dp(12) })
                loadCover(poster, url)
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(marker, LinearLayout.LayoutParams(dp(26), -2))
            fun paint(focused: Boolean) {
                row.background = TvVisual.row(activity, focused, checked)
                name.setTextColor(TvVisual.primary)
                marker.setTextColor(TvVisual.accent)
            }
            paint(false)
            row.setOnFocusChangeListener { _, focused -> paint(focused) }
            row.setOnClickListener { next.dismiss(); action() }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            choices += row
        }
        labels.forEachIndexed { index, value -> choice(value, index == selected) { onChoice(index) } }
        if (extra != null) choice(extra, false, onExtra)
        choices.forEachIndexed { index, view ->
            view.nextFocusUpId = choices[maxOf(0, index - 1)].id
            view.nextFocusDownId = choices.getOrNull(index + 1)?.id ?: cancel.id
            view.nextFocusLeftId = view.id
            view.nextFocusRightId = view.id
        }
        cancel.nextFocusUpId = choices.last().id
        content.addView(cancel, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
        next = AlertDialog.Builder(activity).create()
        next.setView(content, 0, 0, 0, 0)
        if (onBack != null) next.setOnCancelListener { main.post { onBack() } }
        open(next, anchor)
        next.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        val width = activity.dialogWidth(if (compact) 440 else 480)
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val height = minOf(content.measuredHeight, (activity.resources.displayMetrics.heightPixels * .84f).toInt())
        scroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        next.window?.setLayout(width, height)
        val target = choices[selected.takeIf { it in labels.indices } ?: 0]
        target.requestFocus()
        target.post { if (next.isShowing) scroll.requestChildFocus(list, target) }
    }

    fun chooseGrid(
        title: String,
        labels: List<String>,
        selected: Int,
        anchor: View?,
        onChoice: (Int) -> Unit,
    ) {
        val width = activity.dialogWidth(680)
        val columns = ((width - dp(32)) / dp(116)).coerceIn(1, 4)
        val list = column()
        val scroll = ScrollView(activity).apply { addView(list) }
        val choices = mutableListOf<TextView>()
        lateinit var next: AlertDialog
        labels.chunked(columns).forEachIndexed { rowIndex, values ->
            val row = LinearLayout(activity).apply { isBaselineAligned = false }
            values.forEachIndexed { col, value ->
                val index = rowIndex * columns + col
                val choice =
                    TextView(activity).apply {
                        id = View.generateViewId()
                        text = value
                        textSize = 15f
                        gravity = android.view.Gravity.CENTER
                        isFocusable = true
                        isClickable = true
                        minimumHeight = dp(48)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setPadding(dp(6), dp(6), dp(6), dp(6))
                        contentDescription = value
                        fun paint(focused: Boolean) {
                            setTextColor(if (index == selected) TvStyle.accent else TvStyle.text)
                            background = TvStyle.card(activity, focused, index == selected)
                        }
                        paint(false)
                        setOnFocusChangeListener { _, focused -> paint(focused) }
                        setOnClickListener {
                            next.dismiss()
                            onChoice(index)
                        }
                    }
                row.addView(
                    choice,
                    LinearLayout.LayoutParams(0, -1, 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    },
                )
                choices += choice
            }
            repeat(columns - values.size) {
                row.addView(Space(activity), LinearLayout.LayoutParams(0, -1, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            list.addView(row)
        }
        choices.forEachIndexed { i, v ->
            v.nextFocusLeftId = if (i % columns == 0) v.id else choices[i - 1].id
            v.nextFocusRightId =
                if (i % columns == columns - 1 || i == choices.lastIndex) v.id else choices[i + 1].id
            v.nextFocusUpId = choices.getOrNull(i - columns)?.id ?: v.id
            v.nextFocusDownId =
                choices.getOrNull(i + columns)?.id
                    ?: if (i / columns < choices.lastIndex / columns) choices.last().id else View.NO_ID
        }
        next =
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("返回", null)
                .create()
        open(next, anchor)
        next.window?.setLayout(width, -2)
        choices.getOrNull(selected.coerceAtLeast(0))?.requestFocus()
    }

    fun episodePicker(count: Int, current: Int, anchor: View?, select: (Int) -> Unit) {
        val content = column()
        var digits = (current + 1).toString()
        var replace = true
        val value = label(digits, 28f).apply { gravity = Gravity.CENTER }
        content.addView(value)
        val error = label("", 14f).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        content.addView(error)
        fun digit(key: String) {
            digits = if (replace) key else (digits + key).take(6)
            replace = false
            value.text = digits
            error.visibility = View.GONE
        }
        fun erase() {
            digits = if (replace) "" else digits.dropLast(1)
            replace = false
            value.text = digits.ifEmpty { "—" }
            error.visibility = View.GONE
        }
        val buttons = mutableListOf<Button>()
        listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("清空", "0", "退格"),
            )
            .forEach { keys ->
                val row = LinearLayout(activity)
                keys.forEach { key ->
                    val button =
                        Button(activity).apply {
                            id = View.generateViewId()
                            text = key
                            setTextColor(TvStyle.text)
                            minHeight = dp(48)
                            backgroundTintList =
                                android.content.res.ColorStateList(
                                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                                    intArrayOf(TvStyle.raised, TvStyle.surface),
                                )
                            setOnClickListener {
                                when (key) {
                                    "清空" -> {
                                        digits = ""
                                        replace = false
                                        value.text = "—"
                                        error.visibility = View.GONE
                                    }
                                    "退格" -> erase()
                                    else -> digit(key)
                                }
                            }
                        }
                    buttons += button
                    row.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
                }
                content.addView(row)
            }
        val next =
            AlertDialog.Builder(activity)
                .setTitle("跳转集数 · 1—$count")
                .setView(ScrollView(activity).apply { addView(content) })
                .setNegativeButton("取消", null)
                .setPositiveButton("播放此集", null)
                .create()
        next.setOnKeyListener { _, key, event ->
            when {
                key in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    if (event.action == KeyEvent.ACTION_DOWN)
                        digit((key - KeyEvent.KEYCODE_0).toString())
                    true
                }
                key == KeyEvent.KEYCODE_DEL -> {
                    if (event.action == KeyEvent.ACTION_DOWN) erase()
                    true
                }
                else -> false
            }
        }
        open(next, anchor)
        val locate = next.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancel = next.getButton(AlertDialog.BUTTON_NEGATIVE)
        buttons.forEachIndexed { i, button ->
            button.nextFocusLeftId = buttons[if (i % 3 == 0) i else i - 1].id
            button.nextFocusRightId = buttons[if (i % 3 == 2) i else i + 1].id
            button.nextFocusUpId = buttons[if (i < 3) i else i - 3].id
            button.nextFocusDownId = if (i < 9) buttons[i + 3].id else locate.id
        }
        locate.nextFocusUpId = buttons[10].id
        cancel.nextFocusUpId = buttons[9].id
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val target = EpisodeTarget.parse(digits, count)
            if (target == null) {
                error.text = "请输入 1—$count 之间的集数"
                error.visibility = View.VISIBLE
            }
            else {
                next.dismiss()
                select(target)
            }
        }
        buttons.first().requestFocus()
    }

    private fun qr(url: String) =
        ImageView(activity).apply {
            val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 360, 360)
            val pixels =
                IntArray(360 * 360) { i ->
                    if (bits[i % 360, i / 360]) Color.BLACK else Color.WHITE
                }
            setImageBitmap(Bitmap.createBitmap(pixels, 360, 360, Bitmap.Config.ARGB_8888))
            contentDescription = "二维码"
        }

    private fun qrDetails(url: String, message: String): LinearLayout {
        val width = activity.dialogWidth(560)
        val stacked = width < dp(420)
        val size = minOf(dp(185), (width - dp(48)).coerceAtLeast(1))
        val instructions = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(message, 16f))
            addView(label(url, 12f))
        }
        return LinearLayout(activity).apply {
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (stacked) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            addView(qr(url), LinearLayout.LayoutParams(size, size))
            addView(
                instructions,
                if (stacked) LinearLayout.LayoutParams(-1, -2)
                else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) },
            )
        }
    }

    fun phoneConsole(url: String, anchor: View?, disconnect: () -> Unit) {
        val content = column().apply {
            addView(qrDetails(url, "在同一局域网的另一台设备扫码，用浏览器搜索剧名、遥控播放或管理片单。退出应用后连接失效，请勿分享二维码。"))
        }
        open(AlertDialog.Builder(activity).setTitle("手机扫码控制")
            .setView(ScrollView(activity).apply { addView(content) }).setPositiveButton("关闭", null)
            .setNegativeButton("断开所有手机") { _, _ -> disconnect() }.create(), anchor)
    }

    fun phoneInput(
        anchor: View?,
        inputTitle: String = "手机输入剧名",
        pageTitle: String = "在电视上搜索",
        pagePrompt: String = "输入剧名，电视会搜索全部短剧和漫剧。",
        inputPlaceholder: String = "输入剧名或关键词",
        extraFieldLabel: String? = null,
        extraFieldPlaceholder: String? = null,
        submit: (String?, String?) -> Unit,
    ) {
        val addresses =
            runCatching {
                    LanInputAddresses.find(
                        NetworkCompat.manager(activity)
                    )
                }
                .getOrDefault(emptyList())
        if (addresses.isEmpty()) {
            info("局域网输入", "未找到可用的局域网地址。请确认本机与用于输入的设备处于可互通的局域网。", anchor)
            return
        }
        showPhoneInput(
            anchor, addresses, 0,
            inputTitle, pageTitle, pagePrompt, inputPlaceholder,
            extraFieldLabel, extraFieldPlaceholder, submit,
        )
    }

    private fun showPhoneInput(
        anchor: View?,
        addresses: List<LanInputAddress>,
        index: Int,
        inputTitle: String,
        pageTitle: String,
        pagePrompt: String,
        inputPlaceholder: String,
        extraFieldLabel: String?,
        extraFieldPlaceholder: String?,
        submit: (String?, String?) -> Unit,
    ) {
        val selected = addresses[index]
        val content = column()
        val status = label("正在开启手机输入…")
        content.addView(status)
        val builder =
            AlertDialog.Builder(activity)
                .setTitle(inputTitle)
                .setView(ScrollView(activity).apply { addView(content) })
                .setNegativeButton("关闭", null)
                .setPositiveButton("连接帮助", null)
        if (addresses.size > 1) builder.setNeutralButton("切换地址", null)
        val next = builder.create()
        open(next, anchor)
        next.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val message =
                "请依次检查：\n1. 用手机浏览器打开二维码地址；扫码软件内打不开时，选择在浏览器打开。\n2. 手机不要只用移动数据；手机和电视需处于可互通的局域网。\n3. 访客 Wi-Fi、路由器设备隔离、VPN 可能阻断连接。\n4. 多个网络可尝试切换地址；电脑模拟器的内部地址通常无法被手机访问。\n\n返回时会重新生成二维码，旧地址随窗口关闭失效。"
            val help =
                AlertDialog.Builder(activity)
                    .setTitle("手机连接不上")
                    .setMessage(message)
                    .setPositiveButton("重新生成二维码") { _, _ ->
                        showPhoneInput(
                            anchor, addresses, index,
                            inputTitle, pageTitle, pagePrompt, inputPlaceholder,
                            extraFieldLabel, extraFieldPlaceholder, submit,
                        )
                    }
                    .setNegativeButton("关闭", null)
                    .create()
            open(help, anchor)
        }
        if (addresses.size > 1)
            next.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                choose(
                    "选择本机网络地址",
                    addresses.map { "${it.label} · ${it.address}" },
                    anchor,
                    { chosen ->
                        showPhoneInput(
                            anchor, addresses, chosen,
                            inputTitle, pageTitle, pagePrompt, inputPlaceholder,
                            extraFieldLabel, extraFieldPlaceholder, submit,
                        )
                    },
                )
            }
        worker.execute {
            try {
                val session =
                    PhoneSearchServer(
                        selected.address,
                        { query, extra ->
                            main.post {
                                if (dialog === next) {
                                    next.dismiss()
                                    submit(query, extra)
                                }
                            }
                        },
                        { main.post { if (dialog === next) status.text = "二维码已过期，请关闭后重新打开。" } },
                        onConnected = {
                            main.post { if (dialog === next) status.text = "手机已连接，请在手机上输入并发送。" }
                        },
                        pageTitle = pageTitle,
                        pagePrompt = pagePrompt,
                        inputPlaceholder = inputPlaceholder,
                        extraFieldLabel = extraFieldLabel,
                        extraFieldPlaceholder = extraFieldPlaceholder,
                    )
                main.post {
                    if (dialog !== next) {
                        session.close()
                        return@post
                    }
                    phone = session
                    content.removeAllViews()
                    content.addView(
                        qrDetails(
                            session.url,
                            "${selected.label} · ${selected.address}\n手机扫码后，用浏览器打开并提交。\n需要同一局域网；二维码有效 5 分钟。",
                        )
                    )
                    status.text = "等待手机连接…"
                    content.addView(status)
                }
            } catch (_: Exception) {
                main.post { if (dialog === next) status.text = "此地址无法开启手机输入，请关闭重试，或切换其他网络地址。" }
            }
        }
    }

    fun libraryTransfer(backup: String, anchor: View?, restore: (BackupData, Boolean) -> Unit) {
        val address = NetworkCompat.lanAddress(NetworkCompat.manager(activity))
        if (address == null) {
            info("备份与恢复", "请将本机与用于传输备份的设备连接到同一局域网后重试。", anchor)
            return
        }
        val content = column()
        val status = label("正在生成二维码…")
        content.addView(status)
        val next =
            AlertDialog.Builder(activity)
                .setTitle("手机备份与恢复")
                .setView(ScrollView(activity).apply { addView(content) })
                .setNegativeButton("关闭", null)
                .create()
        open(next, anchor)
        worker.execute {
            try {
                val session =
                    LibraryTransferServer(
                        address,
                        backup,
                        { data ->
                            main.post {
                                if (dialog !== next) return@post
                                next.dismiss()
                                val preview = column()
                                preview.addView(
                                    label(
                                        "备份含 ${data.favorites.size} 部收藏、${data.history.size} 条观看记录、${data.searches.size} 条搜索、${data.later.size} 部稍后看、${data.hidden.size} 部已隐藏推荐。\n\n收藏合并，观看位置以较新的记录为准；已有收藏优先保留（最多 500 部），历史保留最近 200 条。",
                                        16f,
                                    )
                                )
                                val settings =
                                    CheckBox(activity).apply {
                                        text = "同时恢复播放设置和内容分类"
                                        isChecked = false
                                    }
                                preview.addView(settings)
                                val confirm =
                                    AlertDialog.Builder(activity)
                                        .setTitle("确认恢复本机记录")
                                        .setView(ScrollView(activity).apply { addView(preview) })
                                        .setNegativeButton("取消", null)
                                        .setPositiveButton("合并恢复") { _, _ ->
                                            restore(data, settings.isChecked)
                                        }
                                        .create()
                                open(confirm, anchor)
                                confirm.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
                            }
                        },
                        {
                            main.post {
                                if (dialog === next) {
                                    content.removeAllViews()
                                    content.addView(label("二维码已过期，请关闭后重新打开。"))
                                }
                            }
                        },
                    )
                main.post {
                    if (dialog !== next) {
                        session.close()
                        return@post
                    }
                    phone = session
                    content.removeAllViews()
                    content.addView(
                        qrDetails(
                            session.url,
                            "在同一局域网扫码后，用浏览器下载备份或上传文件恢复。二维码有效 5 分钟，关闭后失效。恢复需在本机确认。",
                        )
                    )
                }
            } catch (_: Exception) {
                main.post { if (dialog === next) status.text = "无法开启备份传输，请检查网络后重试。" }
            }
        }
    }
}
