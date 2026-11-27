package jp.poiwallet.hogen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.widget.GridLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 12キーのフリック入力。タップで1文字目、左・上・右・下フリックで2〜5文字目。
 * 同じキーを素早く続けてタップすると、ケータイ打ちのように次の文字に変わる。
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class FlickKeyboardView(
    context: Context,
    private val listener: Listener,
) : GridLayout(context) {

    interface Listener {
        fun onChar(text: String)
        fun onToggle(previous: String, next: String)
        fun onModify()
        fun onDelete()
        fun onSpace()
        fun onEnter()
        fun onSwitchKeyboard()
    }

    private sealed class Key(val label: String) {
        class Kana(val chars: List<String>) : Key(chars[0])
        class Action(label: String, val action: () -> Unit) : Key(label)
    }

    private val density = resources.displayMetrics.density
    private val flickThreshold = 28 * density
    private var lastKana: Key.Kana? = null
    private var lastIndex = 0
    private var lastTime = 0L

    private val kanaRows = listOf(
        "あいうえお", "かきくけこ", "さしすせそ",
        "たちつてと", "なにぬねの", "はひふへほ",
        "まみむめも",
    )

    init {
        columnCount = 4
        rowCount = 4
        setBackgroundColor(Color.parseColor("#DCE3EC"))
        setPadding(dp(3), dp(3), dp(3), dp(3))

        fun kana(s: String) = Key.Kana(s.map { it.toString() })
        val keys = listOf(
            kana(kanaRows[0]), kana(kanaRows[1]), kana(kanaRows[2]), Key.Action("⌫") { listener.onDelete() },
            kana(kanaRows[3]), kana(kanaRows[4]), kana(kanaRows[5]), Key.Action("空白") { listener.onSpace() },
            kana(kanaRows[6]), Key.Kana(listOf("や", "「", "ゆ", "」", "よ")), kana("らりるれろ"), Key.Action("改行") { listener.onEnter() },
            Key.Action("゛゜小") { listener.onModify() }, Key.Kana(listOf("わ", "を", "ん", "ー", "〜")),
            Key.Kana(listOf("、", "。", "？", "！", "…")), Key.Action("🌐") { listener.onSwitchKeyboard() },
        )
        keys.forEachIndexed { i, k -> addView(makeKey(k), params(i / 4, i % 4)) }
    }

    private fun dp(v: Int) = (v * density).toInt()

    private fun params(row: Int, col: Int) = GridLayout.LayoutParams(GridLayout.spec(row, 1f), GridLayout.spec(col, if (col == 3) 0.8f else 1f)).apply {
        width = 0
        height = dp(52)
        setMargins(dp(3), dp(3), dp(3), dp(3))
    }

    private fun makeKey(key: Key): TextView = TextView(context).apply {
        text = key.label
        gravity = Gravity.CENTER
        textSize = if (key is Key.Kana) 22f else 15f
        typeface = if (key is Key.Kana) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(Color.parseColor("#1D2A3A"))
        background = GradientDrawable().apply {
            cornerRadius = 8 * density
            setColor(if (key is Key.Kana) Color.WHITE else Color.parseColor("#C3CEDC"))
        }
        if (key is Key.Kana) {
            contentDescription = key.chars.joinToString(" ")
            var downX = 0f
            var downY = 0f
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; v.alpha = 0.6f }
                    MotionEvent.ACTION_UP -> {
                        v.alpha = 1f
                        onKanaUp(key, ev.x - downX, ev.y - downY)
                    }
                    MotionEvent.ACTION_CANCEL -> v.alpha = 1f
                }
                true
            }
        } else if (key is Key.Action) {
            setOnClickListener { lastKana = null; key.action() }
        }
    }

    private fun onKanaUp(key: Key.Kana, dx: Float, dy: Float) {
        val now = System.currentTimeMillis()
        val flickIndex = when {
            abs(dx) < flickThreshold && abs(dy) < flickThreshold -> 0
            abs(dx) > abs(dy) -> if (dx < 0) 1 else 3
            else -> if (dy < 0) 2 else 4
        }
        if (flickIndex == 0 && key === lastKana && now - lastTime < 800) {
            // 続けてタップ: 前の文字を次の文字に置き換える
            val prev = key.chars[lastIndex]
            lastIndex = (lastIndex + 1) % key.chars.size
            listener.onToggle(prev, key.chars[lastIndex])
        } else {
            val ch = key.chars.getOrElse(flickIndex) { key.chars[0] }
            lastIndex = flickIndex
            listener.onChar(ch)
        }
        lastKana = if (flickIndex == 0) key else null
        lastTime = now
    }
}
