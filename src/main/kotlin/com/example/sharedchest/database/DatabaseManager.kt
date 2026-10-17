package com.example.sharedchest.database

import com.example.sharedchest.util.ItemSerializer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.CompletableFuture

class DatabaseManager(private val plugin: JavaPlugin) {

    private var dataSource: HikariDataSource? = null

    /**
     * HikariCP コネクションプールを初期化し、テーブルを作成する
     */
    fun initialize(config: FileConfiguration) {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "sharedchest")
            )
            username = config.getString("database.user", "root")
            password = config.getString("database.password", "password")
            maximumPoolSize = config.getInt("database.pool-size", 5)
            poolName = "SharedChestPool"
            connectionTestQuery = "SELECT 1"
        }

        try {
            dataSource = HikariDataSource(hikariConfig)
            plugin.logger.info("データベース接続プールを初期化しました。")
        } catch (e: Exception) {
            plugin.logger.severe("データベース接続プールの初期化に失敗しました: ${e.message}")
            return
        }

        try {
            dataSource?.connection?.use { conn ->
                val meta = conn.metaData
                meta.getTables(null, null, "shared_chests", arrayOf("TABLE")).use { rs ->
                    if (!rs.next()) {
                        conn.createStatement().use { stmt ->
                            stmt.executeUpdate(
                                """
                                CREATE TABLE shared_chests (
                                  uuid       VARCHAR(36)  NOT NULL PRIMARY KEY,
                                  contents   LONGTEXT     NOT NULL,
                                  is_open    TINYINT(1)   NOT NULL DEFAULT 0,
                                  updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP
                                )
                                """.trimIndent()
                            )
                            plugin.logger.info("shared_chests テーブルを作成しました。")
                        }
                    } else {
                        plugin.logger.info("shared_chests テーブルを確認しました。")
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("テーブル確認・作成に失敗しました: ${e.message}")
        }
    }

    /**
     * データベース接続プールをシャットダウンする
     */
    fun shutdown() {
        dataSource?.let {
            if (!it.isClosed) {
                it.close()
                plugin.logger.info("データベース接続プールを終了しました。")
            }
        }
    }

    /**
     * プレイヤーの共有チェスト内容を DB から取得する
     */
    fun loadChest(playerUuid: UUID, slots: Int): CompletableFuture<Array<ItemStack?>> {
        return CompletableFuture.supplyAsync {
            val ds = dataSource
            if (ds == null || ds.isClosed) {
                plugin.logger.severe("データベース未接続のため loadChest をスキップします。")
                return@supplyAsync arrayOfNulls<ItemStack>(slots)
            }
            try {
                ds.connection.use { conn ->
                    conn.prepareStatement("SELECT contents FROM shared_chests WHERE uuid = ?").use { ps ->
                        ps.setString(1, playerUuid.toString())
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                val contents = rs.getString("contents")
                                if (!contents.isNullOrEmpty()) {
                                    return@supplyAsync ItemSerializer.deserialize(contents, slots)
                                }
                            }
                        }
                        arrayOfNulls<ItemStack>(slots)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("チェスト読み込みに失敗しました (UUID: $playerUuid): ${e.message}")
                arrayOfNulls<ItemStack>(slots)
            }
        }
    }

    /**
     * プレイヤーの共有チェスト内容を DB に保存し、is_open を 0 にリセットする
     */
    fun saveChest(playerUuid: UUID, contents: Array<ItemStack?>): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            val ds = dataSource
            if (ds == null || ds.isClosed) {
                plugin.logger.severe("データベース未接続のため saveChest をスキップします。")
                return@supplyAsync false
            }
            try {
                val serialized = ItemSerializer.serialize(contents)
                ds.connection.use { conn ->
                    conn.prepareStatement(
                        """
                        INSERT INTO shared_chests (uuid, contents, is_open)
                        VALUES (?, ?, 0)
                        ON DUPLICATE KEY UPDATE contents = VALUES(contents), is_open = 0
                        """.trimIndent()
                    ).use { ps ->
                        ps.setString(1, playerUuid.toString())
                        ps.setString(2, serialized)
                        ps.executeUpdate()
                        true
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("チェスト保存に失敗しました (UUID: $playerUuid): ${e.message}")
                false
            }
        }
    }

    /**
     * is_open フラグを 1 に更新してアトミックロックを取得する
     */
    fun tryLock(playerUuid: UUID): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            val ds = dataSource
            if (ds == null || ds.isClosed) {
                plugin.logger.severe("データベース未接続のため tryLock をスキップします。")
                return@supplyAsync false
            }
            try {
                ds.connection.use { conn ->
                    // ステップ1: 既存行で is_open が 0（またはクラッシュ等で5分以上放置されたロック）のものを 1 に更新
                    conn.prepareStatement(
                        "UPDATE shared_chests SET is_open = 1, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND (is_open = 0 OR updated_at < NOW() - INTERVAL 5 MINUTE)"
                    ).use { updatePs ->
                        updatePs.setString(1, playerUuid.toString())
                        if (updatePs.executeUpdate() == 1) {
                            return@supplyAsync true
                        }
                    }

                    // ステップ2: 行が存在しない場合は INSERT IGNORE で新規作成してロック
                    conn.prepareStatement(
                        "INSERT IGNORE INTO shared_chests (uuid, contents, is_open) VALUES (?, '', 1)"
                    ).use { insertPs ->
                        insertPs.setString(1, playerUuid.toString())
                        insertPs.executeUpdate() == 1
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("ロック取得に失敗しました (UUID: $playerUuid): ${e.message}")
                false
            }
        }
    }

    /**
     * is_open フラグを 0 にリセットしてアンロックする
     */
    fun unlock(playerUuid: UUID): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            val ds = dataSource
            if (ds == null || ds.isClosed) {
                plugin.logger.severe("データベース未接続のため unlock をスキップします。")
                return@runAsync
            }
            try {
                ds.connection.use { conn ->
                    conn.prepareStatement(
                        "UPDATE shared_chests SET is_open = 0 WHERE uuid = ?"
                    ).use { ps ->
                        ps.setString(1, playerUuid.toString())
                        ps.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("アンロックに失敗しました (UUID: $playerUuid): ${e.message}")
            }
        }
    }
}
