# SharedChest (vcmc-Storage)

## 概要
データベース（MySQL）を利用して、複数サーバー間でアイテムを共有できる共有チェストを提供する Paper（Folia対応・Kotlin製）プラグインです。
データベースのフラグを用いた排他制御（ロック機構）およびタイムアウト自動解放により、複数サーバー間でのアイテム増殖（デュープ）を防止します。

## 機能
- **データベース保存**: プレイヤーごとのチェスト内容をMySQLデータベースに保存・同期。
- **Folia完全対応**: `AsyncScheduler` を用いた非同期のDB通信と、`EntityScheduler` を用いた安全なインベントリ操作を実装しています。
- **排他制御（デュープ対策）**: 共有チェストを開く際、データベース上で `is_open` フラグを用いたロック処理を行い、同時に別サーバーで開くことを防ぎます（クラッシュ時の5分タイムアウト自動解放およびサーバー停止時の同期保存に対応）。
- **MiniMessage対応**: チェストタイトルなどでAdventure Component（MiniMessage）を使用可能。
- **スロット保存**: `BukkitObjectOutputStream` によるBase64シリアライズを利用し、アイテムメタデータと配置スロットを完全に復元します。

## コマンド
- `/gchest` : 自分の共有チェストを開きます。

## パーミッション
- `sharedchest.use` : 共有チェストを開く権限（デフォルト: `true`）

## 設定 (config.yml)
```yaml
database:
  host: "localhost"      # データベースホスト
  port: 3306             # データベースポート
  name: "sharedchest"    # データベース名
  user: "root"           # データベースユーザー
  password: "password"   # データベースパスワード
  pool-size: 5           # HikariCPのコネクションプールサイズ

chest:
  title: "<gold>共有チェスト" # チェストのタイトル (MiniMessage形式)
  rows: 3                    # チェストの行数 (1〜6)
```

## コード構成
- `SharedChestPlugin.kt`
  - メインクラス。設定の読み込み、DB初期化、コマンド・リスナーの登録、サーバー停止時の同期保存を行います。
- `command/ChestCommand.kt`
  - `/gchest` コマンドの処理。非同期でDBロックを取得し、成功した場合のみ `EntityScheduler` を介してインベントリを開きます。
- `database/DatabaseManager.kt`
  - HikariCPを使用した接続管理クラス。チェストのセーブ/ロード機能と、`tryLock()` / `unlock()` によるアトミックな排他制御を提供します。
- `listener/ChestListener.kt`
  - インベントリを閉じた時（`InventoryCloseEvent`）やプレイヤー切断時（`PlayerQuitEvent`）を検知し、チェスト内容の保存とロック解除を実行します。
- `util/ItemSerializer.kt`
  - `Array<ItemStack?>` と Base64 文字列間のシリアライズ・デシリアライズを担当します。
