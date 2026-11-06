# SharedChest (vcmc-Storage)

MySQL を使って複数サーバー間でアイテムを共有できる共有チェストの Folia 26.3 向けプラグインです。
開いている間はロックするので、別のサーバーから同時に開いてアイテムを増やすことはできません。

## 動作環境

| 項目 | バージョン |
|---|---|
| サーバー | Folia 26.3（`folia-api 26.3.build.6-alpha` でビルド） |
| Java | 25 以上 |
| データベース | MySQL 8 以降 |

## 機能

- **データベース保存**: プレイヤーごとのチェストの内容を MySQL に保存します。
- **Folia 対応**: データベースの処理は専用のスレッドで、インベントリの操作はプレイヤーのリージョンで行います。
- **排他制御**: チェストを開くと、開いたサーバーだけが持つトークンでロックします。開いている間は毎分ロックを更新するので、長く開いていても別のサーバーからは開けません。
- **クラッシュからの復帰**: サーバーが落ちてロックが残っても、5 分たてば開けるようになります。
- **アイテムを失わないための動作**
  - 読み込みに失敗したときは、空のチェストを開かずにエラーを表示します。
  - `chest.rows` を減らしても、入っているアイテムが収まる大きさで開きます。
  - データベースに保存できなかったときは 3 回やり直し、それでも失敗したら復旧用ファイルへ書き出します。
- **MiniMessage 対応**: チェストのタイトルに MiniMessage を使えます。

### 1.0.0 からの更新

jar を入れ替えるだけで動きます。すべてのサーバーを同時に更新してください。

- テーブルに `lock_token` 列を自動で追加します。
- 保存形式を Paper の NBT 形式に変更しました。1.0.0 で保存した内容も読み込めて、次に閉じたときに新しい形式で保存します。

## コマンド

- `/gchest`: 自分の共有チェストを開きます。

## パーミッション

- `sharedchest.use`: 共有チェストを開く権限（デフォルト: `true`）

## 設定 (config.yml)

```yaml
database:
  host: "localhost"      # データベースホスト
  port: 3306             # データベースポート
  name: "sharedchest"    # データベース名
  user: "root"           # データベースユーザー
  password: "password"   # データベースパスワード
  pool-size: 5           # HikariCP のコネクションプールサイズ
  use-ssl: false         # 通信を TLS で暗号化するか

chest:
  title: "<gold>共有チェスト" # チェストのタイトル (MiniMessage 形式)
  rows: 3                    # チェストの行数 (1〜6)
```

## 復旧用ファイルからの復旧

コンソールに「チェストの内容を ... へ保存しました」と出たときは、`plugins/SharedChest/recovery/<UUID>-<日時>.txt` に内容が保存されています。対象のプレイヤーがチェストを開いていないことを確認してから、ファイルの中身を `contents` 列へ入れてください。

```sql
UPDATE shared_chests SET contents = '<ファイルの中身>', is_open = 0, lock_token = NULL WHERE uuid = '<UUID>';
```

## コード構成

- `SharedChestPlugin.kt`: メインクラス。初期化とコマンドの登録、データベース用スレッドの管理を行います。
- `ChestService.kt`: チェストの開閉と保存。
- `command/ChestCommand.kt`: `/gchest`。
- `database/DatabaseManager.kt`: HikariCP の接続プール。
- `database/ChestRepository.kt`: `shared_chests` テーブルの読み書きとロック。
- `listener/ChestListener.kt`: インベントリを閉じたときと切断時の保存。
- `util/ItemSerializer.kt`: `ItemStack` 配列と文字列の変換。
- `util/ChestLayout.kt`: チェストの大きさの計算。

## ビルド

Java 25 が必要です。

```
mvn package
```

`target/SharedChest-<バージョン>.jar` が生成されます。テストは H2 の MySQL 互換モードでロックの動きを確認します。
