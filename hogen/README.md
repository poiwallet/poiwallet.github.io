# ほうげん予測変換

ひらがなで打つと方言の言葉を候補に出す予測変換ツールです。標準語で打つと、その方言での言い方も出ます。
ユーザーが言葉を追加でき、よく使う言葉ほど上に出るので、使うほど育ちます。

## 構成

| 場所 | 内容 |
| --- | --- |
| `dict.json` | 共通の方言辞書（Web版とAndroid版の両方が使う） |
| `index.html` | Web版（PWA。https://poiwallet.github.io/hogen/ で公開） |
| `android/` | Androidキーボード（IME）。12キーのフリック入力＋方言候補バー |

辞書の1語は `{"r":"読み","w":"表記","g":"地域","m":"標準語の意味","mr":"意味の読み"}` です。

## Androidキーボードの使い方

1. [最新版のAPK](https://github.com/poiwallet/poiwallet.github.io/releases/tag/hogen-latest)をダウンロードしてインストール
2. アプリを開き「キーボードの設定を開く」から「ほうげんキーボード」をオン
3. 文字入力欄で 🌐 か切り替え画面から選ぶ

ローカルで作る場合: `cd android && ./gradlew assembleDebug`

## これから

- みんなが追加した言葉を共有する「みんなの辞書」（Supabase）
- 辞書の拡充（地元の方の確認つき）
