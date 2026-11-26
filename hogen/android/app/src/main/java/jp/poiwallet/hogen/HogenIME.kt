package jp.poiwallet.hogen

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 方言の予測変換キーボード本体。
 * ひらがなを打つと上の候補バーに方言の言葉が並び、タップで確定する。
 */
class HogenIME : InputMethodService(), FlickKeyboardView.Listener {

    private lateinit var dict: Dictionary
    private lateinit var candidateRow: LinearLayout
    private lateinit var candidateScroll: HorizontalScrollView
    private val composing = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        dict = Dictionary.get(this)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DCE3EC"))
        }
        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        candidateScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#EEF2F6"))
            addView(candidateRow)
        }
        root.addView(candidateScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(FlickKeyboardView(this, this))
        refreshCandidates()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing.setLength(0)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshCandidates()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.setLength(0)
    }

    // ---- 入力 ----
    private fun updateComposing() {
        currentInputConnection?.setComposingText(composing, 1)
        refreshCandidates()
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        composing.setLength(0)
        refreshCandidates()
    }

    override fun onChar(text: String) {
        if (text in PUNCT) {
            // 句読点は入力中の文字を確定してからそのまま打つ
            commit(composing.toString() + text)
            return
        }
        composing.append(text)
        updateComposing()
    }

    override fun onToggle(previous: String, next: String) {
        if (composing.endsWith(previous)) {
            composing.setLength(composing.length - previous.length)
            composing.append(next)
            updateComposing()
        } else {
            onChar(next)
        }
    }

    override fun onModify() {
        if (composing.isEmpty()) return
        val last = composing.last()
        val next = MODIFY[last] ?: return
        composing.setCharAt(composing.length - 1, next)
        updateComposing()
    }

    override fun onDelete() {
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            updateComposing()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun onSpace() {
        if (composing.isNotEmpty()) commit(composing.toString()) else commit("　")
    }

    override fun onEnter() {
        if (composing.isNotEmpty()) {
            commit(composing.toString())
            return
        }
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            (currentInputEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onSwitchKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToNextInputMethod(false)) requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showInputMethodPicker()
        }
    }

    // ---- 候補バー ----
    private fun refreshCandidates() {
        if (!::candidateRow.isInitialized) return
        candidateRow.removeAllViews()
        val q = composing.toString()
        if (q.isEmpty()) {
            candidateRow.addView(hint("ひらがなを打つと方言の候補が出ます"))
            return
        }
        for (c in dict.suggest(q)) {
            val e = c.entry
            val sub = buildString {
                append(e.g)
                if (e.m.isNotEmpty()) append("・").append(e.m)
            }
            candidateRow.addView(chip((if (c.viaStandard) "→" else "") + e.w, sub, e.mine) {
                dict.recordUse(e)
                commit(e.w)
            })
        }
        // そのまま（ひらがな／カタカナ）
        candidateRow.addView(chip(q, "ひらがな", false) { commit(q) })
        val kata = Dictionary.toKatakana(q)
        if (kata != q) candidateRow.addView(chip(kata, "カタカナ", false) { commit(kata) })
        // 言葉を追加
        candidateRow.addView(chip("＋追加", "「$q」を辞書へ", true) {
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_READING, q)
            startActivity(i)
        })
        candidateScroll.scrollTo(0, 0)
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#5B6A7D"))
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun chip(main: String, sub: String, mine: Boolean, onTap: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(2), dp(10), dp(2))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor(if (mine) "#F8E6DA" else "#FFFFFF"))
            setStroke(dp(1), Color.parseColor(if (mine) "#C8662B" else "#D3DBE5"))
        }
        addView(TextView(context).apply {
            text = main
            textSize = 17f
            setTextColor(Color.parseColor("#1D2A3A"))
        })
        addView(TextView(context).apply {
            text = sub
            textSize = 10f
            setTextColor(Color.parseColor("#5B6A7D"))
        })
        setOnClickListener { onTap() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT,
        ).apply { marginEnd = dp(6) }
    }

    companion object {
        private val PUNCT = setOf("、", "。", "？", "！", "…", "「", "」")

        /** ゛゜小 キー: 押すたびに 清音→濁音→半濁音/小文字 と巡回する */
        private val MODIFY: Map<Char, Char> = buildMap {
            fun cycle(vararg cs: Char) { for (i in cs.indices) put(cs[i], cs[(i + 1) % cs.size]) }
            "かきくけこさしすせそたちてと".forEach { c -> cycle(c, c + 1) }
            cycle('つ', 'っ', 'づ')
            "はひふへほ".forEach { c -> cycle(c, c + 1, c + 2) }
            cycle('あ', 'ぁ'); cycle('い', 'ぃ'); cycle('え', 'ぇ'); cycle('お', 'ぉ')
            cycle('う', 'ぅ', 'ゔ')
            cycle('や', 'ゃ'); cycle('ゆ', 'ゅ'); cycle('よ', 'ょ'); cycle('わ', 'ゎ')
        }
    }
}
