package jp.poiwallet.hogen

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 設定画面: キーボードの有効化、言葉の追加、よく使う地域、自分の言葉の一覧、バックアップ */
class MainActivity : Activity() {

    private lateinit var dict: Dictionary
    private lateinit var body: LinearLayout
    private lateinit var myList: LinearLayout
    private lateinit var stats: TextView
    private lateinit var fReading: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dict = Dictionary.get(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#EEF2F6"))
            addView(body)
        })

        title("ほうげん予測変換", 26f)
        text("ひらがなで打つと方言の言葉が候補に出るキーボードです。使うほど、言葉を足すほど育ちます。")

        heading("1. キーボードを使えるようにする")
        button("キーボードの設定を開く") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        text("「ほうげんキーボード」をオンにしてください。")
        button("キーボードを切り替える") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        body.addView(EditText(this).apply { hint = "ここで試し打ちできます（例：なま → なまら）" })

        heading("2. 言葉を追加する")
        fReading = field("読み（ひらがな）　例：なまら")
        val fWord = field("表記　例：なまら")
        val fRegion = field("地域　例：北海道")
        val fMeaning = field("標準語での意味　例：とても")
        val fMeaningReading = field("意味の読み（ひらがな）　例：とても")
        intent?.getStringExtra(EXTRA_READING)?.let { fReading.setText(it); fWord.setText(it) }
        button("追加する") {
            val r = fReading.text.toString().trim()
            val w = fWord.text.toString().trim().ifEmpty { r }
            val g = fRegion.text.toString().trim()
            if (!Regex("^[ぁ-ゖァ-ヶー]+$").matches(r)) { toast("読みはひらがなで入れてください"); return@button }
            if (g.isEmpty()) { toast("地域を入れてください"); return@button }
            val ok = dict.add(Entry(r, w, g, fMeaning.text.toString().trim(), fMeaningReading.text.toString().trim()))
            if (ok) {
                toast("「$w」を追加しました")
                listOf(fReading, fWord, fRegion, fMeaning, fMeaningReading).forEach { it.setText("") }
                refresh()
            } else toast("その言葉はもう入っています")
        }

        heading("3. よく使う方言")
        text("選んだ地域の言葉を先に出します。")
        val regionBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(regionBox)
        for (g in dict.allRegions()) {
            regionBox.addView(CheckBox(this).apply {
                text = g
                isChecked = g in dict.preferredRegions
                setOnCheckedChangeListener { _, on ->
                    dict.preferredRegions = if (on) dict.preferredRegions + g else dict.preferredRegions - g
                }
            })
        }

        heading("4. 育ち具合")
        stats = text("")
        myList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(myList)

        heading("5. バックアップ・引っ越し")
        text("Web版と同じ形式です。書き出すとクリップボードにコピーされます。")
        button("書き出す（コピー）") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("hogen", dict.exportJson()))
            toast("コピーしました")
        }
        button("クリップボードから読み込む") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            try {
                toast("${dict.importJson(t)}語を読み込みました"); refresh()
            } catch (e: Exception) {
                toast("読み込めませんでした。書き出したデータをコピーしてから押してください")
            }
        }
        refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(EXTRA_READING)?.let { fReading.setText(it) }
    }

    private fun refresh() {
        stats.text = "${dict.baseCount + dict.myWords.size}語 収録　/　自分で追加 ${dict.myWords.size}語　/　候補を使った ${dict.totalUses}回"
        myList.removeAllViews()
        if (dict.myWords.isEmpty()) {
            myList.addView(small("まだ自分の言葉はありません。地元の言い方や家族だけの言葉を足してみてください。"))
            return
        }
        for (e in dict.myWords) {
            myList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, dp(6))
                addView(TextView(context).apply {
                    text = "${e.w}（${e.r}）［${e.g}］" + if (e.m.isNotEmpty()) "\n意味：${e.m}" else ""
                    textSize = 15f
                    setTextColor(Color.parseColor("#1D2A3A"))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(context).apply {
                    text = "削除"
                    setOnClickListener { dict.remove(e); refresh() }
                })
            })
        }
    }

    // ---- 小さな部品 ----
    private fun title(s: String, size: Float) = TextView(this).apply {
        text = s; textSize = size; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#2C4F8A"))
    }.also { body.addView(it) }

    private fun heading(s: String) = TextView(this).apply {
        text = s; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1D2A3A"))
        setPadding(0, dp(24), 0, dp(6))
    }.also { body.addView(it) }

    private fun text(s: String) = TextView(this).apply {
        text = s; textSize = 15f
        setTextColor(Color.parseColor("#1D2A3A"))
        setPadding(0, dp(4), 0, dp(4))
    }.also { body.addView(it) }

    private fun small(s: String) = TextView(this).apply {
        text = s; textSize = 13f
        setTextColor(Color.parseColor("#5B6A7D"))
    }

    private fun field(h: String) = EditText(this).apply { hint = h; isSingleLine = true }.also { body.addView(it) }

    private fun button(s: String, onClick: (View) -> Unit) = Button(this).apply {
        text = s; isAllCaps = false
        setOnClickListener(onClick)
    }.also { body.addView(it) }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_READING = "reading"
    }
}
